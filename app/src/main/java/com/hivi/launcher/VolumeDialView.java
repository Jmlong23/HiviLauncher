package com.hivi.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

public class VolumeDialView extends View {
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mArc = new RectF();
    private int mValue = 60;

    public VolumeDialView(Context context) {
        super(context);
        init();
    }

    public VolumeDialView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        mTextPaint.setColor(0xfff3b351);
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint.setTextSize(sp(38));
        mTextPaint.setFakeBoldText(true);
    }

    public void setValue(int value) {
        mValue = Math.max(0, Math.min(100, value));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = Math.min(getWidth(), getHeight()) * 0.29f;

        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(ui(3));
        for (int i = 0; i < 60; i++) {
            double angle = Math.toRadians(i * 6 - 210);
            float inner = radius + ui(16);
            float outer = radius + (i % 5 == 0 ? ui(29) : ui(22));
            mPaint.setColor(i * 100 / 60 <= mValue ? 0xfff2a33c : 0x44ffffff);
            canvas.drawLine(cx + (float) Math.cos(angle) * inner,
                    cy + (float) Math.sin(angle) * inner,
                    cx + (float) Math.cos(angle) * outer,
                    cy + (float) Math.sin(angle) * outer, mPaint);
        }

        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setShader(new LinearGradient(cx - radius, cy - radius, cx + radius, cy + radius,
                0xff797d86, 0xff3e4148, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, radius, mPaint);
        mPaint.setShader(null);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(ui(6));
        mPaint.setColor(0x66ffffff);
        canvas.drawCircle(cx, cy, radius + ui(7), mPaint);
        mPaint.setColor(0xffc98020);
        mArc.set(cx - radius - ui(7), cy - radius - ui(7),
                cx + radius + ui(7), cy + radius + ui(7));
        canvas.drawArc(mArc, -210, 300f * mValue / 100f, false, mPaint);

        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(0xffffffff);
        canvas.drawCircle(cx - radius * 0.45f, cy - radius * 0.43f, ui(3), mPaint);
        Paint.FontMetrics fm = mTextPaint.getFontMetrics();
        canvas.drawText(String.valueOf(mValue), cx, cy - (fm.ascent + fm.descent) / 2f, mTextPaint);
    }

    private float ui(int value) {
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        float scale = Math.min(metrics.widthPixels / 1280f, metrics.heightPixels / 800f);
        return value * scale;
    }

    private float sp(int value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
