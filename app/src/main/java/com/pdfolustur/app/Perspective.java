package com.pdfolustur.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PointF;

/**
 * Basit perspektif düzeltme: 4 köşeli seçim bölgesini düz dikdörtgene
 * geri eğrisel haritalar (homografi + çift doğrusal enterpolasyon).
 *
 * Bellek dostu: sonuç tek seferde oluşturulmaz; şerit (strip) şerit çizilir.
 */
public final class Perspective {

    private Perspective() {}

    /** src bitmap'inden seçili dörtlüyü en uzun kenarı maxDim'i aşmayacak şekilde dözer. */
    public static Bitmap warp(Bitmap src, PointF[] quad, int maxDim) throws IllegalArgumentException {
        if (src == null || quad == null || quad.length != 4) {
            throw new IllegalArgumentException("bitmap yok");
        }

        float top = dist(quad[0], quad[1]);
        float bottom = dist(quad[3], quad[2]);
        float left = dist(quad[0], quad[3]);
        float right = dist(quad[1], quad[2]);

        int wantW = Math.round((top + bottom) / 2f);
        int wantH = Math.round((left + right) / 2f);
        if (wantW < 20 || wantH < 20) {
            throw new IllegalArgumentException("Seçim çok küçük");
        }
        float factor = Math.min(1f, maxDim / (float) Math.max(wantW, wantH));
        int outW = Math.max(20, Math.round(wantW * factor));
        int outH = Math.max(20, Math.round(wantH * factor));

        // H: dst (X,Y) -> src (u,v)
        double[] H = homography(quad, outW, outH);
        double a = H[0], b = H[1], c = H[2], d = H[3], e = H[4], f = H[5], g = H[6], h = H[7];

        int sw = src.getWidth(), sh = src.getHeight();
        int[] srcPx = new int[sw * sh];
        src.getPixels(srcPx, 0, sw, 0, 0, sw, sh);

        Bitmap out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);

        final int stripH = 192;
        for (int y0 = 0; y0 < outH; y0 += stripH) {
            int rows = Math.min(stripH, outH - y0);
            int[] buf = new int[outW * rows];
            for (int r = 0; r < rows; r++) {
                int y = y0 + r;
                int rowOff = r * outW;
                double gy = h * y; // tekrar hesaplamayı azalt
                double base = b * y;
                for (int x = 0; x < outW; x++) {
                    double denom = g * x + gy + 1.0;
                    double u = (a * x + base + c) / denom;
                    double v = (d * x + e * y + f) / denom;

                    int fx = (int) Math.floor(u), fy = (int) Math.floor(v);
                    if (fx < 0 || fy < 0 || fx >= sw - 1 || fy >= sh - 1) {
                        // sınır dışı: en yakın pikseli çakıştır
                        int cx = Math.max(0, Math.min(sw - 1, fx));
                        int cy = Math.max(0, Math.min(sh - 1, fy));
                        buf[rowOff + x] = srcPx[cy * sw + cx];
                        continue;
                    }
                    double dx = u - fx, dy = v - fy;
                    int p00 = srcPx[fy * sw + fx];
                    int p10 = srcPx[fy * sw + fx + 1];
                    int p01 = srcPx[(fy + 1) * sw + fx];
                    int p11 = srcPx[(fy + 1) * sw + fx + 1];

                    int topC = lerpPixel(p00, p10, dx);
                    int botC = lerpPixel(p01, p11, dx);
                    buf[rowOff + x] = lerpPixel(topC, botC, dy);
                }
            }
            Bitmap stripBmp = Bitmap.createBitmap(buf, outW, rows, Bitmap.Config.ARGB_8888);
            canvas.drawBitmap(stripBmp, 0, y0, null);
            stripBmp.recycle();
        }
        return out;
    }

    /** İki piksel arasında t oranında çift doğrusal karışım. */
    private static int lerpPixel(int p0, int p1, double t) {
        int a0 = (p0 >>> 24), r0 = (p0 >> 16) & 0xFF, g0 = (p0 >> 8) & 0xFF, b0 = p0 & 0xFF;
        int a1 = (p1 >>> 24), r1 = (p1 >> 16) & 0xFF, g1 = (p1 >> 8) & 0xFF, b1 = p1 & 0xFF;
        int a = (int) (a0 + (a1 - a0) * t);
        int r = (int) (r0 + (r1 - r0) * t);
        int g = (int) (g0 + (g1 - g0) * t);
        int bl = (int) (b0 + (b1 - b0) * t);
        return (a << 24) | (r << 16) | (g << 8) | bl;
    }

    private static float dist(PointF p1, PointF p2) {
        return (float) Math.hypot(p1.x - p2.x, p1.y - p2.y);
    }

    /**
     * dst dikdörtgeninin (0..outW, 0..outH) 4 köşesini verilen dörtlüye taşıyan
     * homografi matrisinin 8 parametresini Gauss elimine yöntemiyle çözer.
     */
    private static double[] homography(PointF[] quad, int outW, int outH) {
        double[][] A = new double[8][8];
        double[] B = new double[8];

        float[] X = {0, outW, outW, 0};
        float[] Y = {0, 0, outH, outH};

        for (int i = 0; i < 4; i++) {
            double u = quad[i].x, v = quad[i].y, x = X[i], y = Y[i];
            double[] r1 = {x, y, 1, 0, 0, 0, -u * x, -u * y};
            double[] r2 = {0, 0, 0, x, y, 1, -v * x, -v * y};
            A[i * 2] = r1; A[i * 2 + 1] = r2;
            B[i * 2] = u; B[i * 2 + 1] = v;
        }

        for (int col = 0; col < 8; col++) {
            int pivot = col;
            for (int row = col + 1; row < 8; row++) {
                if (Math.abs(A[row][col]) > Math.abs(A[pivot][col])) pivot = row;
            }
            if (pivot != col) {
                double[] t = A[pivot]; A[pivot] = A[col]; A[col] = t;
                double tb = B[pivot]; B[pivot] = B[col]; B[col] = tb;
            }
            if (Math.abs(A[col][col]) < 1e-10) throw new IllegalArgumentException("Geçersiz seçim");
            for (int row = col + 1; row < 8; row++) {
                double factor = A[row][col] / A[col][col];
                for (int cc = col; cc < 8; cc++) {
                    A[row][cc] -= factor * A[col][cc];
                }
                B[row] -= factor * B[col];
            }
        }

        double[] H = new double[8];
        for (int row = 7; row >= 0; row--) {
            double s = B[row];
            for (int cc = row + 1; cc < 8; cc++) s -= A[row][cc] * H[cc];
            H[row] = s / A[row][row];
        }
        return H;
    }
}
