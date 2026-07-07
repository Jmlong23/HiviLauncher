package com.hivi.launcher.customview;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

public class DashedBorderDrawable extends Drawable {
    private final Paint mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private final float mRadius;
    private final float mStrokeWidth;

    public DashedBorderDrawable(int strokeColor, int fillColor, float strokeWidth, float radius) {
        mRadius = radius;
        mStrokeWidth = strokeWidth;
        mFillPaint.setStyle(Paint.Style.FILL);
        mFillPaint.setColor(fillColor);
        mStrokePaint.setStyle(Paint.Style.STROKE);
        mStrokePaint.setStrokeWidth(strokeWidth);
        mStrokePaint.setColor(strokeColor);
        mStrokePaint.setPathEffect(new DashPathEffect(new float[]{6f, 5f}, 0));
    }

    @Override
    public void draw(Canvas canvas) {
        mRect.set(getBounds());
        mRect.inset(mStrokeWidth / 2f, mStrokeWidth / 2f);
        canvas.drawRoundRect(mRect, mRadius, mRadius, mFillPaint);
        canvas.drawRoundRect(mRect, mRadius, mRadius, mStrokePaint);
    }

    @Override
    public void setAlpha(int alpha) {
        mFillPaint.setAlpha(alpha);
        mStrokePaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        mFillPaint.setColorFilter(colorFilter);
        mStrokePaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return android.graphics.PixelFormat.TRANSLUCENT;
    }
}

