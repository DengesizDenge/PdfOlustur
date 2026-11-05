package com.pdfolustur.app;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Fotoğraflardan A4 boyutunda PDF üretir.
 * Android'in yerleşik PdfDocument API'si kullanılır, ek bağımlılık yoktur.
 */
public final class PdfCreator {

    // A4 nokta (72 dpi): 595 x 842
    private static final int PAGE_W = 595;
    private static final int PAGE_H = 842;
    private static final int MARGIN = 20;
    private static final int MAX_DIM = Math.max(PAGE_W, PAGE_H) * 3; // ~1800 px yeterli kalite

    private PdfCreator() {}

    public static File photosToPdf(Context ctx, java.util.List<Uri> photos) throws IOException {
        File dir = new File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "PdfOlustur");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Klasör oluşturulamadı");
        File out = new File(dir, "Tarama_" + System.currentTimeMillis() + ".pdf");

        PdfDocument doc = new PdfDocument();
        try {
            int pageNum = 1;
            for (Uri photo : photos) {
                Bitmap bmp = decodeScaled(ctx, photo);
                if (bmp == null) continue;

                PdfDocument.PageInfo info = new PdfDocument.PageInfo
                        .Builder(PAGE_W, PAGE_H, pageNum).create();
                PdfDocument.Page page = doc.startPage(info);
                Canvas c = page.getCanvas();
                c.drawColor(Color.WHITE);

                float scale = Math.min(
                        (PAGE_W - 2f * MARGIN) / bmp.getWidth(),
                        (PAGE_H - 2f * MARGIN) / bmp.getHeight());
                float w = bmp.getWidth() * scale;
                float h = bmp.getHeight() * scale;
                float left = (PAGE_W - w) / 2f;
                float top = (PAGE_H - h) / 2f;
                c.drawBitmap(bmp, null, new RectF(left, top, left + w, top + h), new Paint());
                bmp.recycle();

                doc.finishPage(page);
                pageNum++;
            }
            if (pageNum == 1) throw new IOException("Hiçbir fotoğraf okunamadı");

            try (FileOutputStream fos = new FileOutputStream(out)) {
                doc.writeTo(fos);
            }
        } finally {
            doc.close();
        }

        // Android 10+: Dosyalar/Download klasöründe de görünsün
        if (Build.VERSION.SDK_INT >= 29) {
            try (InputStream in = new java.io.FileInputStream(out)) {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.DISPLAY_NAME, out.getName());
                cv.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
                Uri saved = ctx.getContentResolver()
                        .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (saved != null) {
                    try (OutputStream os = ctx.getContentResolver().openOutputStream(saved)) {
                        MainActivity.copy(in, os);
                    }
                }
            } catch (Exception ignored) {
                // İkincil kayıt başarısız olsa da PDF uygulamanın kendi klasöründe mevcut
            }
        }
        return out;
    }

    private static Bitmap decodeScaled(Context ctx, Uri uri) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            read(ctx, uri, bounds);

            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

            int sample = 1;
            while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_DIM) sample *= 2;

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = Math.max(1, sample);
            Bitmap b = read(ctx, uri, opts);
            if (b == null) return null;

            int longSide = Math.max(b.getWidth(), b.getHeight());
            if (longSide > MAX_DIM) {
                int newW = Math.round(b.getWidth() * (MAX_DIM / (float) longSide));
                int newH = Math.round(b.getHeight() * (MAX_DIM / (float) longSide));
                Bitmap scaled = Bitmap.createScaledBitmap(b, newW, newH, true);
                if (scaled != b) b.recycle();
                return scaled;
            }
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    private static Bitmap read(Context ctx, Uri uri, BitmapFactory.Options opts) throws IOException {
        if ("file".equals(uri.getScheme())) {
            return BitmapFactory.decodeFile(uri.getPath(), opts);
        }
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Akış açılamadı");
            return BitmapFactory.decodeStream(in, null, opts);
        }
    }
}
