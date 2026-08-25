package com.hivi.launcher.customview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.widget.TextView;

public final class FrostedTextView extends TextView {
    private static final int FROSTED_TOP_COLOR = 0x90D8E4F7;
    private static final int FROSTED_BOTTOM_COLOR = 0x90ADBFDF;

    public FrostedTextView(Context context) {
        this(context, null);
    }

    public FrostedTextView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FrostedTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Paint paint = getPaint();
        int originalColor = paint.getColor();
        Shader originalShader = paint.getShader();
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0f, 0f, 0f, getHeight(), 0xCCD8E4F7,
                0xCCADBFDF, Shader.TileMode.CLAMP));
        super.onDraw(canvas);
        paint.setColor(originalColor);
        paint.setShader(originalShader);
    }
}
