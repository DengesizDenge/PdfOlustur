package com.pdfolustur.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

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
        if (getSupportActionBar() != null) setTitle("✂ Belgeyi Kırp");

        // Ekran boyutuna uygun alt çözünürlükte yükle (hız için)
        new Thread(() -> {
            Bitmap b = Glideless.decodeFull(this, srcUri, 2048);
            if (b != null) {
                runOnUiThread(() -> cropView.setBitmap(b));
            } else {
                runOnUiThread(() -> { finish(); });
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
        final Activity act = this;
        final Button apply = findViewById(R.id.btnApply);
        apply.setEnabled(false);

        final PointF[] quad = cropView.getQuadBitmap();

        new Thread(() -> {
            Bitmap out = null;
            String err = null;
            try {
                out = Perspective.warp(src, quad, 2400);
            } catch (Exception e) {
                err = e.getMessage();
            }
            final Bitmap fOut = out;
            final String fErr = err;
            act.runOnUiThread(() -> {
                if (fOut == null) {
                    apply.setEnabled(true);
                    android.widget.Toast.makeText(act,
                            "Kırpma hatası: " + fErr, android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                try {
                    File cropDir = new File(getCacheDir(), "cropped");
                    if (!cropDir.exists()) cropDir.mkdirs();
                    File result = new File(cropDir, "crop_" + System.currentTimeMillis() + ".jpg");
                    try (FileOutputStream fos = new FileOutputStream(result)) {
                        fOut.compress(Bitmap.CompressFormat.JPEG, 92, fos);
                    }
                    Intent i = new Intent();
                    i.putExtra("index", getIntent().getIntExtra("index", -1));
                    i.putExtra("path", result.getAbsolutePath());
                    setResult(RESULT_OK, i);
                    finish();
                } catch (Exception e) {
                    apply.setEnabled(true);
                    android.widget.Toast.makeText(act,
                            "Kaydetme hatası: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }
}
