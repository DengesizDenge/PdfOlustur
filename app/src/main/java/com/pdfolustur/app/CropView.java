package com.pdfolustur.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Resmi gösterip 4 köşeli seçim çerçevesini sürüklemeye imza veren özel görünüm.
 * Köşeler bitmap koordinatlarında tutulur; çizimde ölçek uygulanır.
 */
public class CropView extends View {

    private Bitmap bmp;
    private float scale = 1f;
    private final RectF bmpRect = new RectF();
    private final RectF viewRect = new RectF();
    private final PointF[] corners = new PointF[4]; // TL, TR, BR, BL (bitmap koordinatı)
    private int dragIndex = -1;
    private float cornerRadius = 20f;

    private final Paint darkPaint = new Paint();
    private final Paint linePaint = new Paint();
    private final Paint dotFill = new Paint();
    private final Paint dotStroke = new Paint();
    private final Path overlay = new Path();

    public CropView(Context context) { super(context); init(); }

    public CropView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        darkPaint.setColor(Color.argb(140, 0, 0, 0));
        darkPaint.setStyle(Paint.Style.FILL);
        linePaint.setColor(Color.parseColor("#FFD54F"));
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(4f);
        linePaint.setAntiAlias(true);
        dotFill.setColor(Color.WHITE);
        dotFill.setStyle(Paint.Style.FILL);
        dotFill.setAntiAlias(true);
        dotStroke.setColor(Color.parseColor("#1565C0"));
        dotStroke.setStyle(Paint.Style.STROKE);
        dotStroke.setStrokeWidth(4f);
        dotStroke.setAntiAlias(true);
    }

    public void setBitmap(Bitmap b) {
        bmp = b;
        if (bmp != null) {
            resetCorners();
        }
        invalidate();
    }

    public Bitmap getBitmap() { return bmp; }

    public void resetCorners() {
        if (bmp == null) return;
        bmpRect.set(0, 0, bmp.getWidth(), bmp.getHeight());
        corners[0] = new PointF(bmpRect.left, bmpRect.top);
        corners[1] = new PointF(bmpRect.right, bmpRect.top);
        corners[2] = new PointF(bmpRect.right, bmpRect.bottom);
        corners[3] = new PointF(bmpRect.left, bmpRect.bottom);
        invalidate();
    }

    public void rotate90() {
        if (bmp == null) return;
        Matrix m = new Matrix();
        m.postRotate(90);
        Bitmap rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
        bmp = rotated;
        resetCorners();
    }

    /** Kaynak bitmap referansını bırakır (recycle etmez; GC halleder). */
    public void releaseBitmap() {
        bmp = null;
        invalidate();
    }

    /** Seçilen dörtlüyü bitmap koordinatlarında döndürür. */
    public PointF[] getQuadBitmap() {
        PointF[] q = new PointF[4];
        for (int i = 0; i < 4; i++) q[i] = new PointF(corners[i].x, corners[i].y);
        return q;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bmp == null) return;
        float bw = bmp.getWidth(), bh = bmp.getHeight();
        viewRect.set(getPaddingLeft(), getPaddingTop(),
                getWidth() - getPaddingRight(), getHeight() - getPaddingBottom());
        scale = Math.max(0.001f, Math.min(viewRect.width() / bw, viewRect.height() / bh));
        float dx = viewRect.left + (viewRect.width() - bw * scale) / 2f;
        float dy = viewRect.top + (viewRect.height() - bh * scale) / 2f;

        // resmi çiz
        Matrix m = new Matrix();
        m.setScale(scale, scale);
        m.postTranslate(dx, dy);
        canvas.drawBitmap(bmp, m, null);

        // seçim bölgesi dışını karart
        Path quad = quadPath(dx, dy);
        overlay.reset();
        overlay.addRect(viewRect, Path.Direction.CW);
        overlay.addPath(quad);
        overlay.setFillType(Path.FillType.EVEN_ODD);
        canvas.drawPath(overlay, darkPaint);

        canvas.drawPath(quad, linePaint);

        cornerRadius = Math.max(dp(16), (getWidth() + getHeight()) * 0.012f);
        for (PointF c : corners) {
            if (c == null) continue;
            canvas.drawCircle(dx + c.x * scale, dy + c.y * scale, cornerRadius, dotFill);
            canvas.drawCircle(dx + c.x * scale, dy + c.y * scale, cornerRadius, dotStroke);
        }
    }

    private Path quadPath(float dx, float dy) {
        Path p = new Path();
        for (int i = 0; i < 4; i++) {
            PointF c = corners[i];
            float x = dx + c.x * scale, y = dy + c.y * scale;
            if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
        }
        p.close();
        return p;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (bmp == null) return false;
        float bw = bmp.getWidth(), bh = bmp.getHeight();
        viewRect.set(getPaddingLeft(), getPaddingTop(),
                getWidth() - getPaddingRight(), getHeight() - getPaddingBottom());
        scale = Math.max(0.001f, Math.min(viewRect.width() / bw, viewRect.height() / bh));
        float dx = viewRect.left + (viewRect.width() - bw * scale) / 2f;
        float dy = viewRect.top + (viewRect.height() - bh * scale) / 2f;

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                float x = ev.getX(), y = ev.getY();
                int best = -1; float bestDist = Float.MAX_VALUE;
                for (int i = 0; i < 4; i++) {
                    float d = (float) Math.hypot(x - (dx + corners[i].x * scale),
                            y - (dy + corners[i].y * scale));
                    if (d < bestDist) { bestDist = d; best = i; }
                }
                if (best >= 0 && bestDist <= Math.max(cornerRadius * 2.2f, dp(60))) {
                    dragIndex = best;
                    return true;
                }
                return false;
            }
            case MotionEvent.ACTION_MOVE: {
                if (dragIndex < 0) return false;
                float bx = (ev.getX() - dx) / scale;
                float by = (ev.getY() - dy) / scale;
                // bitmap sınırına çakıştır
                float m = cornerRadius;
                bx = Math.max(m, Math.min(bw - m, bx));
                by = Math.max(m, Math.min(bh - m, by));
                corners[dragIndex].set(bx, by);
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragIndex = -1;
                return true;
        }
        return true;
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
}
