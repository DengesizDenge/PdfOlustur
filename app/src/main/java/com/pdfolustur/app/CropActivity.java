package com.pdfolustur.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;

public class CropActivity extends AppCompatActivity {

    private CropView cropView;
    private Uri srcUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crop);

        cropView = findViewById(R.id.cropView);

        String uriStr = getIntent().getStringExtra("uri");
        srcUri = Uri.parse(uriStr);

        // Ekran boyutuna uygun alt çözünürlükte yükle (hız için)
        new Thread(() -> {
            Bitmap b = Glideless.decodeFull(this, srcUri, 2048);
            if (b != null) {
                runOnUiThread(() -> cropView.setBitmap(b));
            } else {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Görüntü yüklenemedi", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        }).start();

        findViewById(R.id.btnRotate).setOnClickListener(v -> {
            if (cropView.getBitmap() != null) cropView.rotate90();
        });
        findViewById(R.id.btnReset).setOnClickListener(v -> {
            if (cropView.getBitmap() != null) cropView.resetCorners();
        });
        findViewById(R.id.btnApply).setOnClickListener(v -> applyCrop());
    }

    private void applyCrop() {
        Bitmap src = cropView.getBitmap();
        if (src == null) return;
        final Button apply = findViewById(R.id.btnApply);
        apply.setEnabled(false);
        apply.setText("Kırpılıyor…");

        final PointF[] quad = cropView.getQuadBitmap();

        new Thread(() -> {
            File savedFile = null;
            String err = null;
            Bitmap out = null;
            int[] dims = {1600, 1024};
            try {
                // Önce hedef çözünürlükte; bellek yetersiz kalırsa alt çözünürlükte tekrar dene
                for (int maxDim : dims) {
                    try {
                        out = Perspective.warp(src, quad, maxDim);
                        break;
                    } catch (OutOfMemoryError oom) {
                        out = null;
                    }
                }
                if (out == null) {
                    err = "Yetersiz bellek (görüntü çok büyük)";
                } else {
                    File cropDir = new File(getCacheDir(), "cropped");
                    if (!cropDir.exists() && !cropDir.mkdirs()) {
                        throw new IllegalStateException("Klasör oluşturulamadı");
                    }
                    savedFile = new File(cropDir, "crop_" + System.currentTimeMillis() + ".jpg");
                    try (FileOutputStream fos = new FileOutputStream(savedFile)) {
                        out.compress(Bitmap.CompressFormat.JPEG, 92, fos);
                    }
                    out.recycle();
                }
            } catch (Throwable t) {
                err = t.getClass().getSimpleName() + ": " + t.getMessage();
                savedFile = null;
            }

            final File fSaved = savedFile;
            final String fErr = err;
            runOnUiThread(() -> {
                if (fSaved != null) {
                    cropView.releaseBitmap();
                    Intent i = new Intent();
                    i.putExtra("index", getIntent().getIntExtra("index", -1));
                    i.putExtra("path", fSaved.getAbsolutePath());
                    setResult(RESULT_OK, i);
                    finish();
                } else {
                    apply.setEnabled(true);
                    apply.setText("Kırp ve Kaydet");
                    Toast.makeText(this, "Kırpma hatası: " + fErr, Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }
}
