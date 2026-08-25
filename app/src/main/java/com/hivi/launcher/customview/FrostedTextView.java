package com.hivi.launcher.customview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.widget.TextView;

public final class FrostedTextView extends TextView {
    private static final int BACKDROP_ALPHA = 255;
    private static final int FROSTED_FILL_COLOR = 0x80FFFFFF;
    private static final int FROSTED_STROKE_COLOR = 0x33FFFFFF;
    private static final float FROSTED_STROKE_WIDTH_PX = 2f;
    private static final int BACKDROP_BLUR_SCALE = 24;
    private BitmapShader mBackdropShader;
    private int mBackdropResourceId;

    public FrostedTextView(Context context) {
        this(context, null);
    }

    public FrostedTextView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FrostedTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setBackdropResource(int resourceId) {
        if (mBackdropResourceId == resourceId) {
            return;
        }
        mBackdropResourceId = resourceId;
        mBackdropShader = null;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Paint paint = getPaint();
        int originalColor = paint.getColor();
        Shader originalShader = paint.getShader();
        int originalAlpha = paint.getAlpha();
        Paint.Style originalStyle = paint.getStyle();
        float originalStrokeWidth = paint.getStrokeWidth();

        paint.setStyle(Paint.Style.FILL);
        paint.setShader(getBackdropShader());
        paint.setAlpha(BACKDROP_ALPHA);
        drawText(canvas, paint);

        paint.setShader(null);
        paint.setAlpha(originalAlpha);
        paint.setColor(FROSTED_FILL_COLOR);
        drawText(canvas, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(FROSTED_STROKE_WIDTH_PX);
        paint.setColor(FROSTED_STROKE_COLOR);
        drawText(canvas, paint);

        paint.setColor(originalColor);
        paint.setShader(originalShader);
        paint.setAlpha(originalAlpha);
        paint.setStyle(originalStyle);
        paint.setStrokeWidth(originalStrokeWidth);
    }

    private void drawText(Canvas canvas, Paint paint) {
        CharSequence text = getText();
        canvas.drawText(text, 0, text.length(), getPaddingLeft(), getBaseline(), paint);
    }

    private BitmapShader getBackdropShader() {
        if (mBackdropShader == null) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;
            Bitmap original = BitmapFactory.decodeResource(getResources(), mBackdropResourceId,
                    options);
            int width = Math.max(1, original.getWidth() / BACKDROP_BLUR_SCALE);
            int height = Math.max(1, original.getHeight() / BACKDROP_BLUR_SCALE);
            Bitmap reduced = Bitmap.createScaledBitmap(original, width, height, true);
            Bitmap blurred = Bitmap.createScaledBitmap(reduced, original.getWidth(),
                    original.getHeight(), true);
            mBackdropShader = new BitmapShader(blurred, Shader.TileMode.CLAMP,
                    Shader.TileMode.CLAMP);
        }
        float rootWidth = getRootView().getWidth();
        float rootHeight = getRootView().getHeight();
        float scale = Math.max(rootWidth / 1280f, rootHeight / 800f);
        Matrix matrix = new Matrix();
        matrix.setScale(scale, scale);
        matrix.postTranslate((rootWidth - 1280f * scale) / 2f - getLeft(),
                (rootHeight - 800f * scale) / 2f - getTop());
        mBackdropShader.setLocalMatrix(matrix);
        return mBackdropShader;
    }
}
