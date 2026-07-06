package com.hivi.launcher;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

public class AppIconDrawable extends Drawable {
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mPath = new Path();
    private final String mType;

    public AppIconDrawable(String type) {
        mType = type;
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override
    public void draw(Canvas canvas) {
        RectF b = new RectF(getBounds());
        float r = b.width() * 0.16f;
        if ("image".equals(mType)) {
            mPaint.setShader(new LinearGradient(b.left, b.top, b.right, b.bottom,
                    0xffd9f5ff, 0xff6b9fbd, Shader.TileMode.CLAMP));
        } else {
            mPaint.setShader(new LinearGradient(b.left, b.top, b.right, b.bottom,
                    0xffdadada, 0xff6b6b6b, Shader.TileMode.CLAMP));
        }
        mPaint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(b, r, r, mPaint);
        mPaint.setShader(null);

        if ("gear".equals(mType)) {
            drawGear(canvas, b);
        } else if ("switch".equals(mType)) {
            drawSwitch(canvas, b);
        } else {
            drawImage(canvas, b);
        }
    }

    private void drawGear(Canvas canvas, RectF b) {
        float cx = b.centerX();
        float cy = b.centerY();
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(b.width() * 0.1f);
        mPaint.setColor(0xff3e3e3e);
        canvas.drawCircle(cx, cy, b.width() * 0.25f, mPaint);
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45);
            canvas.drawLine(cx + (float) Math.cos(a) * b.width() * 0.31f,
                    cy + (float) Math.sin(a) * b.width() * 0.31f,
                    cx + (float) Math.cos(a) * b.width() * 0.42f,
                    cy + (float) Math.sin(a) * b.width() * 0.42f, mPaint);
        }
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(0xff3e3e3e);
        canvas.drawCircle(cx, cy, b.width() * 0.08f, mPaint);
    }

    private void drawImage(Canvas canvas, RectF b) {
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(0xddffffff);
        mPath.reset();
        mPath.moveTo(b.left + b.width() * 0.18f, b.bottom - b.height() * 0.2f);
        mPath.lineTo(b.left + b.width() * 0.42f, b.top + b.height() * 0.44f);
        mPath.lineTo(b.left + b.width() * 0.62f, b.bottom - b.height() * 0.2f);
        mPath.close();
        canvas.drawPath(mPath, mPaint);
        mPaint.setColor(0x99ffffff);
        canvas.drawCircle(b.right - b.width() * 0.24f, b.top + b.height() * 0.22f,
                b.width() * 0.08f, mPaint);
    }

    private void drawSwitch(Canvas canvas, RectF b) {
        float cx = b.centerX();
        float cy = b.centerY();
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(b.width() * 0.08f);
        mPaint.setColor(0xffffffff);
        canvas.drawLine(b.left + b.width() * 0.24f, cy - b.height() * 0.16f,
                b.right - b.width() * 0.22f, cy - b.height() * 0.16f, mPaint);
        canvas.drawLine(b.left + b.width() * 0.22f, cy + b.height() * 0.16f,
                b.right - b.width() * 0.24f, cy + b.height() * 0.16f, mPaint);
        mPath.reset();
        mPath.moveTo(b.left + b.width() * 0.24f, cy - b.height() * 0.16f);
        mPath.lineTo(b.left + b.width() * 0.38f, cy - b.height() * 0.3f);
        mPath.moveTo(b.left + b.width() * 0.24f, cy - b.height() * 0.16f);
        mPath.lineTo(b.left + b.width() * 0.38f, cy - b.height() * 0.02f);
        mPath.moveTo(b.right - b.width() * 0.24f, cy + b.height() * 0.16f);
        mPath.lineTo(b.right - b.width() * 0.38f, cy + b.height() * 0.02f);
        mPath.moveTo(b.right - b.width() * 0.24f, cy + b.height() * 0.16f);
        mPath.lineTo(b.right - b.width() * 0.38f, cy + b.height() * 0.3f);
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
