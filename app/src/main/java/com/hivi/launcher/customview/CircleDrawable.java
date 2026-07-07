package com.hivi.launcher.customview;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;

public class CircleDrawable extends Drawable {
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public CircleDrawable(int color) {
        mPaint.setColor(color);
    }

    @Override
    public void draw(Canvas canvas) {
        float radius = Math.min(getBounds().width(), getBounds().height()) / 2f;
        canvas.drawCircle(getBounds().centerX(), getBounds().centerY(), radius, mPaint);
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

    @Override
    public int getIntrinsicWidth() {
        return 28;
    }

    @Override
    public int getIntrinsicHeight() {
        return 28;
    }
}

