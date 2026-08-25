package com.hivi.launcher.customview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.widget.TextView;

public final class FrostedTextView extends TextView {
    private static final int FROSTED_FILL_COLOR = 0x80FFFFFF;
    private static final int FROSTED_STROKE_COLOR = 0x33FFFFFF;
    private static final float FROSTED_STROKE_WIDTH_PX = 2f;

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
        Paint.Style originalStyle = paint.getStyle();
        float originalStrokeWidth = paint.getStrokeWidth();

        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(FROSTED_FILL_COLOR);
        drawText(canvas, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(FROSTED_STROKE_WIDTH_PX);
        paint.setColor(FROSTED_STROKE_COLOR);
        drawText(canvas, paint);

        paint.setColor(originalColor);
        paint.setShader(originalShader);
        paint.setStyle(originalStyle);
        paint.setStrokeWidth(originalStrokeWidth);
    }

    private void drawText(Canvas canvas, Paint paint) {
        CharSequence text = getText();
        canvas.drawText(text, 0, text.length(), getPaddingLeft(), getBaseline(), paint);
    }
}
