package com.hivi.launcher.systemapps.ui;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import com.hivi.launcher.R;

public final class HeaderButtonBackgroundDrawable extends Drawable {
    private final Drawable mNormalBackground;
    private final Paint mBitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final float mReferenceHeight;
    private final float mCornerRadius;
    private final float mInnerStrokeWidth;
    private final float mBlurRadius;
    private final float mFillOpacity;
    private Bitmap mPressedBackground;
    private boolean mActive;

    public HeaderButtonBackgroundDrawable(Resources resources, Resources.Theme theme) {
        mNormalBackground = resources.getDrawable(R.drawable.main_status_pill_background, theme)
                .mutate();
        mReferenceHeight = resources.getDimension(R.dimen.system_apps_header_design_height);
        mCornerRadius = resources.getDimension(R.dimen.system_apps_header_pressed_corner_radius);
        mInnerStrokeWidth = resources.getDimension(R.dimen.system_apps_header_pressed_stroke_width);
        mBlurRadius = resources.getDimension(R.dimen.system_apps_header_pressed_blur_radius);
        mFillOpacity = resources.getFraction(R.fraction.system_apps_header_pressed_opacity, 1, 1);
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (mActive && !bounds.isEmpty()) {
            if (mPressedBackground == null) {
                mPressedBackground = createPressedBackground(bounds);
            }
            canvas.drawBitmap(mPressedBackground, bounds.left, bounds.top, mBitmapPaint);
        } else {
            mNormalBackground.draw(canvas);
        }
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        mNormalBackground.setBounds(bounds);
        mPressedBackground = null;
    }

    @Override
    public boolean isStateful() {
        return true;
    }

    @Override
    protected boolean onStateChange(int[] states) {
        boolean enabled = false;
        boolean highlighted = false;
        for (int state : states) {
            enabled |= state == android.R.attr.state_enabled;
            highlighted |= state == android.R.attr.state_pressed
                    || state == android.R.attr.state_focused;
        }
        boolean active = enabled && highlighted;
        boolean changed = active != mActive;
        mActive = active;
        boolean normalChanged = mNormalBackground.setState(states);
        if (changed || normalChanged) {
            invalidateSelf();
        }
        return changed || normalChanged;
    }

    @Override
    public void setAlpha(int alpha) {
        mNormalBackground.setAlpha(alpha);
        mBitmapPaint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        mNormalBackground.setColorFilter(colorFilter);
        mBitmapPaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    private Bitmap createPressedBackground(Rect bounds) {
        int width = bounds.width();
        int height = bounds.height();
        Bitmap background = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(background);
        canvas.translate(-bounds.left, -bounds.top);
        int alpha = mNormalBackground.getAlpha();
        ColorFilter colorFilter = mNormalBackground.getColorFilter();
        mNormalBackground.setAlpha(255);
        mNormalBackground.setColorFilter(null);
        try {
            mNormalBackground.draw(canvas);
        } finally {
            mNormalBackground.setAlpha(alpha);
            mNormalBackground.setColorFilter(colorFilter);
        }
        int[] pixels = new int[width * height];
        background.getPixels(pixels, 0, width, 0, 0, width, height);
        Rect bodyBounds = new Rect(width, height, 0, 0);
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                if (Color.alpha(pixels[row * width + column]) >= 128) {
                    bodyBounds.left = Math.min(bodyBounds.left, column);
                    bodyBounds.top = Math.min(bodyBounds.top, row);
                    bodyBounds.right = Math.max(bodyBounds.right, column + 1);
                    bodyBounds.bottom = Math.max(bodyBounds.bottom, row + 1);
                }
            }
        }
        if (bodyBounds.isEmpty()) {
            return background;
        }
        Bitmap overlay = createPressedOverlay(width, height, bodyBounds);
        int[] overlayPixels = new int[pixels.length];
        overlay.getPixels(overlayPixels, 0, width, 0, 0, width, height);
        for (int index = 0; index < pixels.length; index++) {
            int color = pixels[index];
            if (Color.alpha(color) >= 128) {
                float brightness = 1f - Color.alpha(overlayPixels[index]) / 255f;
                pixels[index] = Color.argb(Color.alpha(color),
                        Math.round(Color.red(color) * brightness),
                        Math.round(Color.green(color) * brightness),
                        Math.round(Color.blue(color) * brightness));
            }
        }
        background.setPixels(pixels, 0, width, 0, 0, width, height);
        overlay.recycle();
        return background;
    }

    private Bitmap createPressedOverlay(int width, int height, Rect bodyBounds) {
        float scale = bodyBounds.height() / mReferenceHeight;
        float radiusY = Math.min(mCornerRadius * scale, bodyBounds.height() / 2f);
        float radiusX = Math.min(bodyBounds.width() / 2f, radiusY * width
                * mNormalBackground.getIntrinsicHeight()
                / (height * mNormalBackground.getIntrinsicWidth()));
        float strokeWidth = mInnerStrokeWidth * scale;
        RectF outerBounds = new RectF(bodyBounds);
        Path outerShape = new Path();
        outerShape.addRoundRect(outerBounds, radiusX, radiusY, Path.Direction.CW);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.clipPath(outerShape);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.argb(Math.round(255f * mFillOpacity), 0, 0, 0));
        canvas.drawPath(outerShape, paint);
        RectF innerBounds = new RectF(outerBounds);
        innerBounds.inset(strokeWidth, strokeWidth);
        Path innerStroke = new Path(outerShape);
        innerStroke.setFillType(Path.FillType.EVEN_ODD);
        if (!innerBounds.isEmpty()) {
            innerStroke.addRoundRect(innerBounds, Math.max(0f, radiusX - strokeWidth),
                    Math.max(0f, radiusY - strokeWidth), Path.Direction.CW);
        }
        paint.setColor(Color.BLACK);
        paint.setMaskFilter(new BlurMaskFilter(mBlurRadius * scale, BlurMaskFilter.Blur.NORMAL));
        canvas.drawPath(innerStroke, paint);
        return bitmap;
    }
}
