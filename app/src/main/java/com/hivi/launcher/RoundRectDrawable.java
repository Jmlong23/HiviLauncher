package com.hivi.launcher;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

public class RoundRectDrawable extends Drawable {
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private final float mRadius;

    public RoundRectDrawable(int color, float radius) {
        mPaint.setColor(color);
        mPaint.setStyle(Paint.Style.FILL);
        mRadius = radius;
    }

    @Override
    public void draw(Canvas canvas) {
        mRect.set(getBounds());
        canvas.drawRoundRect(mRect, mRadius, mRadius, mPaint);
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
