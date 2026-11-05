package com.pdfolustur.app;

import android.graphics.Bitmap;
import android.graphics.PointF;

/**
 * Basit perspektif düzeltme: 4 köşeli seçim bölgesini düz dikdörtgene
 * geri eğrisel haritalar (homografi + çift doğrusal enterpolasyon).
 */
public final class Perspective {

    private Perspective() {}

    /** src bitmap'inden seçili dörtlüyü en uzun kenarı maxDim'i aşmayacak şekilde dözer. */
    public static Bitmap warp(Bitmap src, PointF[] quad, int maxDim) {
        if (src == null || quad == null || quad.length != 4) return null;

        float top = dist(quad[0], quad[1]);
        float bottom = dist(quad[3], quad[2]);
        float left = dist(quad[0], quad[3]);
        float right = dist(quad[1], quad[2]);

        int outW = Math.round((top + bottom) / 2f);
        int outH = Math.round((left + right) / 2f);
        if (outW < 20 || outH < 20) {
            throw new IllegalArgumentException("Seçim çok küçük");
        }
        float factor = Math.min(1f, maxDim / (float) Math.max(outW, outH));
        outW = Math.max(20, Math.round(outW * factor));
        outH = Math.max(20, Math.round(outH * factor));

        double[] H = homography(quad, outW, outH);
        int sw = src.getWidth(), sh = src.getHeight();
        int[] srcPx = new int[sw * sh];
        src.getPixels(srcPx, 0, sw, 0, 0, sw, sh);
        int[] dstPx = new int[outW * outH];

        // H: dst (X,Y) -> src (u,v)
        // u = (a X + b Y + c) / (g X + h Y + 1)
        double a = H[0], b = H[1], c = H[2], d = H[3], e = H[4], f = H[5], g = H[6], h = H[7];

        for (int y = 0; y < outH; y++) {
            for (int x = 0; x < outW; x++) {
                double denom = g * x + h * y + 1.0;
                double u = (a * x + b * y + c) / denom;
                double v = (d * x + e * y + f) / denom;

                int fx = (int) Math.floor(u), fy = (int) Math.floor(v);
                if (fx < 0 || fy < 0 || fx >= sw - 1 || fy >= sh - 1) {
                    // sınır dışı: en yakın pikseli çakıştır
                    fx = Math.max(0, Math.min(sw - 1, fx));
                    fy = Math.max(0, Math.min(sh - 1, fy));
                    dstPx[y * outW + x] = srcPx[fy * sw + fx];
                    continue;
                }
                double dx = u - fx, dy = v - fy;
                int p00 = srcPx[fy * sw + fx];
                int p10 = srcPx[fy * sw + fx + 1];
                int p01 = srcPx[(fy + 1) * sw + fx];
                int p11 = srcPx[(fy + 1) * sw + fx + 1];

                int A = blend(p00, p10, dx), B = blend(p01, p11, dx);
                dstPx[y * outW + x] = blendRow(A, B, dy, p00, p10, p01, p11);
            }
        }
        return Bitmap.createBitmap(dstPx, outW, outH, Bitmap.Config.ARGB_8888);
    }

    private static int blend(int p0, int p1, double t) {
        int a0 = (p0 >>> 24), r0 = (p0 >> 16) & 0xFF, g0 = (p0 >> 8) & 0xFF, b0 = p0 & 0xFF;
        int a1 = (p1 >>> 24), r1 = (p1 >> 16) & 0xFF, g1 = (p1 >> 8) & 0xFF, b1 = p1 & 0xFF;
        int a = (int) Math.round(a0 + (a1 - a0) * t);
        int r = (int) Math.round(r0 + (r1 - r0) * t);
        int g = (int) Math.round(g0 + (g1 - g0) * t);
        int bl = (int) Math.round(b0 + (b1 - b0) * t);
        return (a << 24) | (r << 16) | (g << 8) | bl;
    }

    private static int blendRow(int top, int bottom, double t, int p00, int p10, int p01, int p11) {
        int a = (int) (((top >>> 24) & 0xFF) + (((bottom >>> 24) & 0xFF) - ((top >>> 24) & 0xFF)) * t);
        int r = (int) (((top >> 16) & 0xFF) + (((bottom >> 16) & 0xFF) - ((top >> 16) & 0xFF)) * t);
        int g = (int) (((top >> 8) & 0xFF) + (((bottom >> 8) & 0xFF) - ((top >> 8) & 0xFF)) * t);
        int b = (int) ((top & 0xFF) + ((bottom & 0xFF) - (top & 0xFF)) * t);
        a = Math.max(0, Math.min(255, a));
        r = Math.max(0, Math.min(255, r));
        g = Math.max(0, Math.min(255, g));
        b = Math.max(0, Math.min(255, b));
        return (a << 24) | (r << 16) | (g << 8) | b;
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
