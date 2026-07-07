package com.hivi.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import java.util.Calendar;

public class ClockFaceView extends View {
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public ClockFaceView(Context context) {
        super(context);
        init();
    }

    public ClockFaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        mTextPaint.setColor(0xddffffff);
        mTextPaint.setTextSize(sp(10));
        mTextPaint.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = Math.min(getWidth(), getHeight()) * 0.30f;

        mPaint.setShader(new LinearGradient(cx - radius, cy - radius, cx + radius, cy + radius,
                0xff8d9097, 0xffe1e3e8, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, radius, mPaint);
        mPaint.setShader(null);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(ui(11));
        mPaint.setColor(0x66575c66);
        canvas.drawCircle(cx, cy, radius + ui(7), mPaint);

        mPaint.setStrokeWidth(ui(1));
        mPaint.setColor(0x99ffffff);
        for (int i = 0; i < 60; i++) {
            double angle = Math.toRadians(i * 6 - 90);
            float inner = radius + (i % 5 == 0 ? ui(12) : ui(18));
            float outer = radius + ui(22);
            canvas.drawLine(cx + (float) Math.cos(angle) * inner,
                    cy + (float) Math.sin(angle) * inner,
                    cx + (float) Math.cos(angle) * outer,
                    cy + (float) Math.sin(angle) * outer, mPaint);
        }

        canvas.drawText("12", cx, cy - radius - ui(20), mTextPaint);
        canvas.drawText("3", cx + radius + ui(27), cy + ui(4), mTextPaint);
        canvas.drawText("6", cx, cy + radius + ui(30), mTextPaint);
        canvas.drawText("9", cx - radius - ui(27), cy + ui(4), mTextPaint);

        Calendar calendar = Calendar.getInstance();
        float minute = calendar.get(Calendar.MINUTE);
        float hour = calendar.get(Calendar.HOUR) + minute / 60f;
        drawHand(canvas, cx, cy, hour * 30f - 90f, radius * 0.56f, 0xffededed, ui(4));
        drawHand(canvas, cx, cy, minute * 6f - 90f, radius * 0.84f, 0xffe43f5a, ui(2));

        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(0xffffffff);
        canvas.drawCircle(cx, cy, ui(3), mPaint);
    }

    private void drawHand(Canvas canvas, float cx, float cy, float degrees, float length,
            int color, float width) {
        double angle = Math.toRadians(degrees);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setStrokeWidth(width);
        mPaint.setColor(color);
        canvas.drawLine(cx, cy, cx + (float) Math.cos(angle) * length,
                cy + (float) Math.sin(angle) * length, mPaint);
        mPaint.setStrokeCap(Paint.Cap.BUTT);
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
