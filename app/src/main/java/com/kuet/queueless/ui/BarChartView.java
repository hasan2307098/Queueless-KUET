package com.kuet.queueless.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.kuet.queueless.R;

/** Minimal 24-bar chart (one bar per hour of day). No external chart library needed. */
public class BarChartView extends View {
    private int[] data = new int[24];
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;

    public BarChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = context.getResources().getDisplayMetrics().density;
        barPaint.setColor(ContextCompat.getColor(context, R.color.primary));
        textPaint.setColor(ContextCompat.getColor(context, R.color.text_secondary));
        textPaint.setTextSize(10 * density);
        textPaint.setTextAlign(Paint.Align.CENTER);
        linePaint.setColor(ContextCompat.getColor(context, R.color.text_secondary));
        linePaint.setStrokeWidth(density);
    }

    public void setData(int[] values) {
        data = values == null ? new int[24] : values;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        int max = 1;
        for (int v : data) max = Math.max(max, v);

        float w = getWidth();
        float h = getHeight();
        float bottom = h - 20 * density;
        float top = 14 * density;
        float slot = w / data.length;

        for (int i = 0; i < data.length; i++) {
            float bh = (bottom - top) * data[i] / max;
            float left = i * slot + slot * 0.15f;
            float right = (i + 1) * slot - slot * 0.15f;
            c.drawRect(left, bottom - bh, right, bottom, barPaint);
            if (data[i] > 0) {
                c.drawText(String.valueOf(data[i]), i * slot + slot / 2f, bottom - bh - 3 * density, textPaint);
            }
            if (i % 3 == 0) {
                c.drawText(String.valueOf(i), i * slot + slot / 2f, h - 4 * density, textPaint);
            }
        }
        c.drawLine(0, bottom, w, bottom, linePaint);
    }
}
