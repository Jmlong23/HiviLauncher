package com.hivi.launcher.customview;

import android.content.Context;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.widget.TextView;

public class GradientTextView extends TextView {
    private LinearGradient gradient;

    public GradientTextView(Context context) {
        super(context);
    }

    public GradientTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        gradient = new LinearGradient(0, 0, 0, Math.max(1, height),
                0xFF7E7E7E, 0xFF131313, Shader.TileMode.CLAMP);
    }

    @Override
    protected void onDraw(android.graphics.Canvas canvas) {
        getPaint().setShader(gradient);
        super.onDraw(canvas);
        getPaint().setShader(null);
    }
}
