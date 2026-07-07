package com.hivi.launcher.customview;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;

public class PlayDrawable extends Drawable {
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mPath = new Path();

    @Override
    public void draw(Canvas canvas) {
        float left = getBounds().left;
        float top = getBounds().top;
        float w = getBounds().width();
        float h = getBounds().height();
        mPath.reset();
        mPath.moveTo(left + w * 0.24f, top + h * 0.16f);
        mPath.lineTo(left + w * 0.78f, top + h * 0.5f);
        mPath.lineTo(left + w * 0.24f, top + h * 0.84f);
        mPath.close();
        mPaint.setColor(0xffffffff);
        mPaint.setStyle(Paint.Style.FILL);
        canvas.drawPath(mPath, mPaint);
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

