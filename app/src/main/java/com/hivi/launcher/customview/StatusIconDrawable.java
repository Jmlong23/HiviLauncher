package com.hivi.launcher.customview;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

public class StatusIconDrawable extends Drawable {
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String mType;

    public StatusIconDrawable(String type) {
        mType = type;
        mPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override
    public void draw(Canvas canvas) {
        RectF b = new RectF(getBounds());
        float cx = b.centerX();
        float cy = b.centerY();
        mPaint.setColor(0xffffffff);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(b.width() * 0.08f);

        if ("bt".equals(mType)) {
            Path path = new Path();
            path.moveTo(cx - b.width() * 0.1f, b.top + b.height() * 0.18f);
            path.lineTo(cx + b.width() * 0.22f, cy);
            path.lineTo(cx - b.width() * 0.1f, b.bottom - b.height() * 0.18f);
            path.close();
            canvas.drawPath(path, mPaint);
            canvas.drawLine(cx - b.width() * 0.24f, cy - b.height() * 0.26f,
                    cx + b.width() * 0.18f, cy + b.height() * 0.16f, mPaint);
            canvas.drawLine(cx - b.width() * 0.24f, cy + b.height() * 0.26f,
                    cx + b.width() * 0.18f, cy - b.height() * 0.16f, mPaint);
        } else {
            RectF arc = new RectF();
            for (int i = 0; i < 3; i++) {
                float inset = b.width() * (0.12f + i * 0.16f);
                arc.set(b.left + inset, b.top + inset, b.right - inset, b.bottom + inset);
                canvas.drawArc(arc, 220, 100, false, mPaint);
            }
            mPaint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(cx, b.bottom - b.height() * 0.18f, b.width() * 0.06f, mPaint);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        mPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        mPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return android.graphics.PixelFormat.TRANSLUCENT;
    }
}

