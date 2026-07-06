package com.hivi.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

public class WeatherView extends View {
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();

    public WeatherView(Context context) {
        super(context);
    }

    public WeatherView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float sunCx = w * 0.63f;
        float sunCy = h * 0.38f;
        float sunR = h * 0.31f;

        mPaint.setShader(new LinearGradient(sunCx - sunR, sunCy - sunR, sunCx + sunR, sunCy + sunR,
                0xfffff192, 0xffff7d26, Shader.TileMode.CLAMP));
        canvas.drawCircle(sunCx, sunCy, sunR, mPaint);
        mPaint.setShader(null);

        mPaint.setShader(new RadialGradient(w * 0.43f, h * 0.66f, w * 0.42f,
                new int[]{0xffd7d7d5, 0xff9a9a96, 0xff686964},
                new float[]{0.1f, 0.62f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(w * 0.35f, h * 0.68f, h * 0.28f, mPaint);
        canvas.drawCircle(w * 0.54f, h * 0.68f, h * 0.22f, mPaint);
        mRect.set(w * 0.15f, h * 0.62f, w * 0.84f, h * 0.91f);
        canvas.drawRoundRect(mRect, dp(8), dp(8), mPaint);
        mPaint.setShader(null);
    }

    private float dp(int value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
