package com.hivi.launcher.customview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import com.hivi.launcher.systemapps.model.AppDownloadStatus;

public final class AppDownloadProgressView extends View {
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRingBounds = new RectF();
    private final Path mPlayPath = new Path();
    private AppDownloadStatus.State mState = AppDownloadStatus.State.NOT_DOWNLOADED;
    private int mProgress = -1;

    public AppDownloadProgressView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setStatus(AppDownloadStatus status) {
        mState = status.getState();
        mProgress = status.getProgress();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        float radius = Math.min(getWidth(), getHeight()) * 0.42f;
        float strokeWidth = radius * 0.09f;
        mRingBounds.set(centerX - radius, centerY - radius,
                centerX + radius, centerY + radius);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(0x88000000);
        canvas.drawCircle(centerX, centerY, radius, mPaint);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setStrokeWidth(strokeWidth);
        mPaint.setColor(0x66ffffff);
        canvas.drawOval(mRingBounds, mPaint);
        mPaint.setColor(Color.WHITE);
        boolean installing = mState == AppDownloadStatus.State.INSTALLING;
        boolean animating = installing
                || mState == AppDownloadStatus.State.UNINSTALLING
                || (mState == AppDownloadStatus.State.DOWNLOADING && mProgress < 0);
        if (animating) {
            float startAngle = (SystemClock.uptimeMillis() % 1200L) * 360f / 1200f - 90f;
            canvas.drawArc(mRingBounds, startAngle, 90f, false, mPaint);
            postInvalidateOnAnimation();
        } else if (mProgress >= 0) {
            canvas.drawArc(mRingBounds, -90f, 360f * mProgress / 100f, false, mPaint);
        }
        mPaint.setStyle(Paint.Style.FILL);
        if (mState == AppDownloadStatus.State.DOWNLOADING) {
            float halfSize = radius * 0.26f;
            canvas.drawRoundRect(centerX - halfSize, centerY - halfSize,
                    centerX + halfSize, centerY + halfSize, strokeWidth, strokeWidth, mPaint);
        } else if (mState == AppDownloadStatus.State.PAUSED) {
            mPlayPath.reset();
            mPlayPath.moveTo(centerX - radius * 0.17f, centerY - radius * 0.32f);
            mPlayPath.lineTo(centerX + radius * 0.33f, centerY);
            mPlayPath.lineTo(centerX - radius * 0.17f, centerY + radius * 0.32f);
            mPlayPath.close();
            canvas.drawPath(mPlayPath, mPaint);
        }
    }
}
