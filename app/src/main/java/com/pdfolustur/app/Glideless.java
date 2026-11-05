package com.pdfolustur.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Basit, bağımlılıksız küçük resim yükleyici. */
public final class Glideless {

    private static final Map<String, Bitmap> CACHE = new ConcurrentHashMap<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Glideless() {}

    /** Önbellekten düşürür; recycle() KULLANMAZ: ImageView hâlâ çiziyor olabilir, GC halleder. */
    public static void remove(String key) {
        CACHE.remove(key);
    }

    public static void load(Context ctx, ImageView view, String key) {
        Bitmap cached = CACHE.get(key);
        if (cached != null && !cached.isRecycled()) {
            view.setImageBitmap(cached);
            return;
        }
        final Uri uri = Uri.parse(key);
        new Thread(() -> {
            Bitmap b = decode(ctx, uri, 256);
            if (b != null) {
                CACHE.put(key, b);
                MAIN.post(() -> view.setImageBitmap(b));
            }
        }).start();
    }

    private static Bitmap decode(Context ctx, Uri uri, int maxDim) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            openDecode(ctx, uri, bounds);

            int sample = 1;
            if (bounds.outWidth > 0) {
                while (bounds.outWidth / (sample * 2) >= maxDim) sample *= 2;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = Math.max(1, sample);
            return openDecode(ctx, uri, opts);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Tam boyuta yakın (en uzun kenar maxDim) çözünürlükte dekodlar; kırpma ekranı kullanır. */
    public static Bitmap decodeFull(Context ctx, Uri uri, int maxDim) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            openDecode(ctx, uri, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

            int sample = 1;
            while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) sample *= 2;

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = Math.max(1, sample);
            return openDecode(ctx, uri, opts);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Bitmap openDecode(Context ctx, Uri uri, BitmapFactory.Options opts) {
        if ("file".equals(uri.getScheme())) {
            return BitmapFactory.decodeFile(uri.getPath(), opts);
        }
        try (java.io.InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            return BitmapFactory.decodeStream(in, null, opts);
        } catch (Exception e) {
            return null;
        }
    }
}
