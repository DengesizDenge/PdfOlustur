package com.pdfolustur.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private final ArrayList<Uri> photos = new ArrayList<>();
    private PhotoAdapter adapter;
    private TextView tvCount;
    private ProgressBar progress;
    private Button btnCreatePdf;
    private File pendingCameraFile;

    private final ActivityResultLauncher<Uri> takePhotoLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
                if (ok && pendingCameraFile != null && pendingCameraFile.exists()) {
                    // Kalıcı bir kopya al (cache silinmeden önce PDF'e girmesi için)
                    File copy = new File(getCacheDir(), "scan_" + System.currentTimeMillis() + ".jpg");
                    try {
                        copy(pendingCameraFile, copy);
                        photos.add(Uri.fromFile(copy));
                        adapter.notifyItemInserted(photos.size() - 1);
                        updateCount();
                    } catch (IOException e) {
                        toast("Fotoğraf kopyalanamadı: " + e.getMessage());
                    }
                }
            });

    private final ActivityResultLauncher<PickVisualMediaRequest> pickMultipleLauncher =
            registerForActivityResult(new ActivityResultContracts.PickMultipleVisualMedia(20), uris -> {
                if (uris != null) {
                    for (Uri u : uris) {
                        photos.add(u);
                    }
                    adapter.notifyDataSetChanged();
                    updateCount();
                }
            });

    private final ActivityResultLauncher<String> requestCameraPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) openCamera();
                else toast(getString(R.string.camera_permission));
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvCount = findViewById(R.id.tvCount);
        progress = findViewById(R.id.progress);
        btnCreatePdf = findViewById(R.id.btnCreatePdf);

        RecyclerView recycler = findViewById(R.id.recyclerPhotos);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PhotoAdapter(this, photos, this::updateCount);
        recycler.setAdapter(adapter);
        updateCount();

        findViewById(R.id.btnCamera).setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                requestCameraPermission.launch(Manifest.permission.CAMERA);
            }
        });

        findViewById(R.id.btnGallery).setOnClickListener(v -> {
            PickVisualMediaRequest req = new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                    .build();
            pickMultipleLauncher.launch(req);
        });

        btnCreatePdf.setOnClickListener(v -> createPdf());
    }

    private void openCamera() {
        try {
            File cacheDir = new File(getCacheDir(), "camera");
            if (!cacheDir.exists()) cacheDir.mkdirs();
            File tmp = File.createTempFile("scan_", ".jpg", cacheDir);
            pendingCameraFile = tmp;
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", tmp);
            takePhotoLauncher.launch(uri);
        } catch (IOException e) {
            toast("Kamera hatası: " + e.getMessage());
        }
    }

    private void createPdf() {
        if (photos.isEmpty()) {
            toast(getString(R.string.no_photos));
            return;
        }
        progress.setVisibility(View.VISIBLE);
        btnCreatePdf.setEnabled(false);
        new Thread(() -> {
            File out = null;
            String err = null;
            try {
                out = PdfCreator.photosToPdf(this, photos);
            } catch (Exception e) {
                err = e.getMessage();
            }
            final File fOut = out;
            final String fErr = err;
            runOnUiThread(() -> {
                progress.setVisibility(View.GONE);
                btnCreatePdf.setEnabled(true);
                if (fOut != null) {
                    toast(getString(R.string.pdf_created, fOut.getName()));
                    sharePdf(fOut);
                } else {
                    toast(getString(R.string.pdf_error, String.valueOf(fErr)));
                }
            });
        }).start();
    }

    private void sharePdf(File pdf) {
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", pdf);
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("application/pdf");
        share.putExtra(Intent.EXTRA_STREAM, uri);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Intent send = Intent.createChooser(share, pdf.getName());
        startActivity(send);
    }

    private void updateCount() {
        tvCount.setText(photos.size() + " fotoğraf — sıralarını ▲▼ ile değiştir, PDF bu sırayla olur");
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }

    private static void copy(File src, File dst) throws IOException {
        try (java.io.FileInputStream in = new java.io.FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            copy(in, out);
        }
    }
}
