package com.fantest.pokervision;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.Collections;
import java.util.List;

final class ScannerOverlayView extends View {
    private final Paint zonePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<ScanModels.TrackedDetection> detections = Collections.emptyList();
    private boolean capacityError;
    private boolean arabic;

    ScannerOverlayView(Context context) { super(context); init(); }
    ScannerOverlayView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        setWillNotDraw(false);
        zonePaint.setStyle(Paint.Style.STROKE);
        zonePaint.setStrokeWidth(dp(2));
        zonePaint.setColor(0xAAFFFFFF);
        labelPaint.setTextSize(sp(12));
        labelPaint.setFakeBoldText(true);
        labelPaint.setColor(Color.WHITE);
        cardPaint.setStyle(Paint.Style.STROKE);
        cardPaint.setStrokeWidth(dp(3));
    }

    void setArabic(boolean arabic) { this.arabic = arabic; invalidate(); }

    void setDetections(List<ScanModels.TrackedDetection> detections, TableZoneMapper.Result zones) {
        this.detections = detections == null ? Collections.emptyList() : detections;
        this.capacityError = zones != null && zones.capacityError;
        invalidate();
    }

    void clear() {
        detections = Collections.emptyList();
        capacityError = false;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        drawZone(canvas, VisionConstants.BOARD_X_MIN, VisionConstants.BOARD_Y_MIN,
                VisionConstants.BOARD_X_MAX, VisionConstants.BOARD_Y_MAX,
                arabic ? "الطاولة" : "BOARD", w, h);
        drawZone(canvas, VisionConstants.HOLE_X_MIN, VisionConstants.HOLE_Y_MIN,
                VisionConstants.HOLE_X_MAX, VisionConstants.HOLE_Y_MAX,
                arabic ? "بطاقاتك" : "YOUR CARDS", w, h);

        for (ScanModels.TrackedDetection d : detections) {
            if (d == null || d.quad == null) continue;
            cardPaint.setColor(colorFor(d.status));
            Path path = new Path();
            path.moveTo((float)(d.quad.p0.x * w), (float)(d.quad.p0.y * h));
            path.lineTo((float)(d.quad.p1.x * w), (float)(d.quad.p1.y * h));
            path.lineTo((float)(d.quad.p2.x * w), (float)(d.quad.p2.y * h));
            path.lineTo((float)(d.quad.p3.x * w), (float)(d.quad.p3.y * h));
            path.close();
            canvas.drawPath(path, cardPaint);
            if (d.card != null) {
                String text = d.card.pretty() + "  " + Math.round(d.confidence * 100) + "%";
                labelPaint.setColor(colorFor(d.status));
                canvas.drawText(text, (float)(d.quad.left() * w), Math.max(sp(14), (float)(d.quad.top() * h) - dp(5)), labelPaint);
            }
        }

        if (capacityError) {
            labelPaint.setColor(Color.rgb(255, 100, 100));
            labelPaint.setTextSize(sp(15));
            canvas.drawText(arabic ? "بطاقات كثيرة في هذه المنطقة" : "Too many cards in this area", dp(14), h / 2f, labelPaint);
            labelPaint.setTextSize(sp(12));
        }
    }

    private void drawZone(Canvas c, double l, double t, double r, double b, String label, float w, float h) {
        RectF rect = new RectF((float)(l*w), (float)(t*h), (float)(r*w), (float)(b*h));
        c.drawRoundRect(rect, dp(10), dp(10), zonePaint);
        labelPaint.setColor(Color.WHITE);
        c.drawText(label, rect.left + dp(8), rect.top + sp(15), labelPaint);
    }

    private int colorFor(ScanModels.Status status) {
        if (status == ScanModels.Status.STABLE || status == ScanModels.Status.MANUAL) return Color.rgb(52, 211, 153);
        if (status == ScanModels.Status.AMBER) return Color.rgb(245, 158, 11);
        if (status == ScanModels.Status.INVALID) return Color.rgb(239, 68, 68);
        return Color.rgb(190, 195, 200);
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
    private float sp(float v) { return v * getResources().getDisplayMetrics().scaledDensity; }
}
