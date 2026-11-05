package com.pdfolustur.app;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class PhotoAdapter extends RecyclerView.Adapter<PhotoAdapter.Holder> {

    public interface OnChangeListener {
        void onChange();
    }

    private final MainActivity activity;
    private final ArrayList<Uri> photos;
    private final OnChangeListener listener;

    public PhotoAdapter(MainActivity activity, ArrayList<Uri> photos, OnChangeListener listener) {
        this.activity = activity;
        this.photos = photos;
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_photo, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int pos) {
        final int position = h.getBindingAdapterPosition();
        if (position == RecyclerView.NO_POSITION) return;

        h.tvIndex.setText(String.valueOf(position + 1));
        Glideless.load(activity, h.imgThumb, photos.get(position).toString());
        h.btnUp.setEnabled(position > 0);
        h.btnDown.setEnabled(position < photos.size() - 1);

        h.btnUp.setOnClickListener(v -> {
            int p = h.getBindingAdapterPosition();
            if (p <= 0) return;
            Uri first = photos.get(p - 1);
            photos.set(p - 1, photos.get(p));
            photos.set(p, first);
            notifyItemMoved(p - 1, p);
            notifyItemRangeChanged(0, photos.size());
            if (listener != null) listener.onChange();
        });

        h.btnDown.setOnClickListener(v -> {
            int p = h.getBindingAdapterPosition();
            if (p < 0 || p >= photos.size() - 1) return;
            Uri second = photos.get(p + 1);
            photos.set(p + 1, photos.get(p));
            photos.set(p, second);
            notifyItemMoved(p, p + 1);
            notifyItemRangeChanged(0, photos.size());
            if (listener != null) listener.onChange();
        });

        h.btnDelete.setOnClickListener(v -> {
            int p = h.getBindingAdapterPosition();
            if (p < 0) return;
            photos.remove(p);
            notifyItemRemoved(p);
            notifyItemRangeChanged(p, photos.size() - p);
            if (listener != null) listener.onChange();
        });

        h.btnCrop.setOnClickListener(v -> {
            int p = h.getBindingAdapterPosition();
            if (p < 0) return;
            activity.startCrop(p);
        });
    }

    @Override
    public int getItemCount() {
        return photos.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        TextView tvIndex;
        ImageView imgThumb;
        Button btnUp, btnDown, btnDelete, btnCrop;

        Holder(View v) {
            super(v);
            tvIndex = v.findViewById(R.id.tvIndex);
            imgThumb = v.findViewById(R.id.imgThumb);
            btnUp = v.findViewById(R.id.btnUp);
            btnDown = v.findViewById(R.id.btnDown);
            btnDelete = v.findViewById(R.id.btnDelete);
            btnCrop = v.findViewById(R.id.btnCrop);
        }
    }
}
