package com.hivi.launcher.systemapps.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import com.hivi.launcher.systemapps.model.AppDownloadState;

public final class AppDownloadProgressView extends View {
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mBounds = new RectF();
    private final RectF mRing = new RectF();
    private final Path mPlayPath = new Path();
    private AppDownloadState mState = AppDownloadState.NOT_INSTALLED;
    private int mProgress = -1;
    private float mRotation;
    private ValueAnimator mAnimator;

    public AppDownloadProgressView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setDownloadState(AppDownloadState state, int progress) {
        mState = state;
        mProgress = progress;
        updateAnimation();
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopAnimation();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        updateAnimation();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        updateAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth(), getHeight());
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        mBounds.set(0f, 0f, getWidth(), getHeight());
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(0x99000000);
        canvas.drawRoundRect(mBounds, size * 0.2f, size * 0.2f, mPaint);
        float radius = size * 0.26f;
        mRing.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(size * 0.035f);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setColor(0x66ffffff);
        canvas.drawOval(mRing, mPaint);
        mPaint.setColor(0xffeeeeee);
        boolean indeterminate = isIndeterminate();
        if (indeterminate) {
            canvas.drawArc(mRing, mRotation - 90f, 95f, false, mPaint);
        } else if (mProgress > 0) {
            canvas.drawArc(mRing, -90f, mProgress * 3.6f, false, mPaint);
        }
        mPaint.setStyle(Paint.Style.FILL);
        if (mState == AppDownloadState.PAUSED) {
            mPlayPath.reset();
            mPlayPath.moveTo(centerX - size * 0.06f, centerY - size * 0.1f);
            mPlayPath.lineTo(centerX + size * 0.1f, centerY);
            mPlayPath.lineTo(centerX - size * 0.06f, centerY + size * 0.1f);
            mPlayPath.close();
            canvas.drawPath(mPlayPath, mPaint);
        } else if (mState == AppDownloadState.DOWNLOADING) {
            float halfSide = size * 0.075f;
            canvas.drawRoundRect(centerX - halfSide, centerY - halfSide,
                    centerX + halfSide, centerY + halfSide, size * 0.015f,
                    size * 0.015f, mPaint);
        }
    }

    private boolean isIndeterminate() {
        return mState == AppDownloadState.WAITING || mState == AppDownloadState.INSTALLING
                || mState == AppDownloadState.UNINSTALLING
                || (mState == AppDownloadState.DOWNLOADING && mProgress < 0);
    }

    private void updateAnimation() {
        if (!isAttachedToWindow() || !isShown() || getWindowVisibility() != VISIBLE
                || !isIndeterminate()) {
            stopAnimation();
            return;
        }
        if (mAnimator == null) {
            mAnimator = ValueAnimator.ofFloat(0f, 360f);
            mAnimator.setDuration(1100L);
            mAnimator.setRepeatCount(ValueAnimator.INFINITE);
            mAnimator.setInterpolator(new LinearInterpolator());
            mAnimator.addUpdateListener(animation -> {
                mRotation = (float) animation.getAnimatedValue();
                invalidate();
            });
            mAnimator.start();
        }
    }

    private void stopAnimation() {
        if (mAnimator != null) {
            mAnimator.cancel();
            mAnimator = null;
        }
    }
}
