package com.hivi.launcher.customview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

public class RecordView extends View {
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public RecordView(Context context) {
        super(context);
    }

    public RecordView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() * 0.47f;
        float cy = getHeight() * 0.53f;
        float r = Math.min(getWidth(), getHeight()) * 0.38f;

        mPaint.setShader(new RadialGradient(cx, cy, r,
                new int[]{0xff1c1c1c, 0xff101010, 0xff363636},
                new float[]{0.24f, 0.72f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, r, mPaint);
        mPaint.setShader(null);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(dp(1));
        mPaint.setColor(0x33ffffff);
        for (int i = 1; i < 6; i++) {
            canvas.drawCircle(cx, cy, r * i / 6f, mPaint);
        }

        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setShader(new LinearGradient(cx - r * 0.3f, cy - r * 0.3f,
                cx + r * 0.3f, cy + r * 0.3f, 0xffdceeff, 0xff5b86bd, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, r * 0.26f, mPaint);
        mPaint.setShader(null);
        mPaint.setColor(0xff202020);
        canvas.drawCircle(cx, cy, r * 0.06f, mPaint);

        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(dp(3));
        mPaint.setColor(0xff7d7d7d);
        float armStartX = getWidth() * 0.82f;
        float armStartY = getHeight() * 0.05f;
        canvas.drawLine(armStartX, armStartY, getWidth() * 0.68f, getHeight() * 0.46f, mPaint);
        canvas.drawLine(getWidth() * 0.68f, getHeight() * 0.46f,
                getWidth() * 0.62f, getHeight() * 0.58f, mPaint);
        mPaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(armStartX, armStartY, dp(6), mPaint);
    }

    private float dp(int value) {
        return value * getResources().getDisplayMetrics().density;
    }
}

