package com.hivi.launcher.customview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Renders audio FFT data as a mirrored spectrum centered on a horizontal axis.
 *
 * <p>The Android {@link android.media.audiofx.Visualizer} callback supplies packed FFT data:
 * the first two entries represent DC/Nyquist values and the following entries are real/imaginary
 * pairs. Low frequencies are placed nearest the center so bass energy forms the larger central
 * bars shown in the input-mode page designs.</p>
 */
public final class AudioSpectrumView extends View {
    private static final int HALF_BAR_COUNT = 48;
    private static final float FFT_MAGNITUDE_MAX = (float) Math.hypot(127d, 127d);

    private final Paint mSpectrumPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBaselinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] mBandAmplitudes = new float[HALF_BAR_COUNT];

    private LinearGradient mSpectrumGradient;
    private int mGradientWidth;

    public AudioSpectrumView(Context context) {
        this(context, null);
    }

    public AudioSpectrumView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        mSpectrumPaint.setStyle(Paint.Style.STROKE);
        mSpectrumPaint.setStrokeCap(Paint.Cap.ROUND);
        mBaselinePaint.setColor(Color.argb(72, 25, 151, 209));
        mBaselinePaint.setStrokeWidth(dp(1f));
    }

    /**
     * Updates the view with packed data from {@link android.media.audiofx.Visualizer}.
     */
    public void setFftData(@Nullable byte[] fftData) {
        if (fftData == null || fftData.length < 4) {
            clear();
            return;
        }

        int complexBinCount = (fftData.length - 2) / 2;
        if (complexBinCount <= 0) {
            clear();
            return;
        }

        for (int bar = 0; bar < HALF_BAR_COUNT; bar++) {
            int startBin = getLogarithmicBin(bar, complexBinCount);
            int endBin = getLogarithmicBin(bar + 1, complexBinCount);
            endBin = Math.max(startBin + 1, endBin);
            endBin = Math.min(complexBinCount, endBin);

            float energy = 0f;
            for (int bin = startBin; bin < endBin; bin++) {
                int offset = 2 + bin * 2;
                if (offset + 1 >= fftData.length) {
                    break;
                }
                energy += (float) Math.hypot(fftData[offset], fftData[offset + 1]);
            }

            float averageEnergy = energy / Math.max(1, endBin - startBin);
            float targetAmplitude = (float) (Math.log1p(averageEnergy)
                    / Math.log1p(FFT_MAGNITUDE_MAX));
            // The visualizer callback is noisy; preserve fast attack while smoothing decay.
            float smoothing = targetAmplitude >= mBandAmplitudes[bar] ? 0.58f : 0.20f;
            mBandAmplitudes[bar] += (targetAmplitude - mBandAmplitudes[bar]) * smoothing;
        }
        postInvalidateOnAnimation();
    }

    /**
     * Clears all spectrum bars when the input signal is unavailable.
     */
    public void clear() {
        for (int i = 0; i < mBandAmplitudes.length; i++) {
            mBandAmplitudes[i] = 0f;
        }
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        ensureGradient(width);
        float centerY = height / 2f;
        float horizontalInset = Math.min(dp(54f), width * 0.17f);
        float verticalInset = Math.min(dp(28f), height * 0.16f);
        float spectrumWidth = Math.max(1f, width - horizontalInset * 2f);
        float slotWidth = spectrumWidth / (HALF_BAR_COUNT * 2f);
        float strokeWidth = Math.max(dp(1.4f), Math.min(dp(2.4f), slotWidth * 0.56f));
        float maxHalfHeight = Math.max(dp(3f), (height - verticalInset * 2f) / 2f);
        float minimumHalfHeight = dp(1.4f);

        mBaselinePaint.setStrokeWidth(dp(0.7f));
        canvas.drawLine(horizontalInset, centerY, width - horizontalInset, centerY,
                mBaselinePaint);

        mSpectrumPaint.setStrokeWidth(strokeWidth);
        mSpectrumPaint.setShader(mSpectrumGradient);
        for (int index = 0; index < HALF_BAR_COUNT; index++) {
            float amplitude = mBandAmplitudes[index];
            float halfHeight = minimumHalfHeight + amplitude * maxHalfHeight;
            float leftX = centerXForBar(width, horizontalInset, slotWidth, index, false);
            float rightX = centerXForBar(width, horizontalInset, slotWidth, index, true);
            canvas.drawLine(leftX, centerY - halfHeight, leftX, centerY + halfHeight,
                    mSpectrumPaint);
            canvas.drawLine(rightX, centerY - halfHeight, rightX, centerY + halfHeight,
                    mSpectrumPaint);
        }
        mSpectrumPaint.setShader(null);
    }

    private int getLogarithmicBin(int band, int complexBinCount) {
        float normalizedBand = band / (float) HALF_BAR_COUNT;
        double logarithmicPosition = Math.pow(complexBinCount, normalizedBand);
        return Math.min(complexBinCount - 1, Math.max(0, (int) logarithmicPosition - 1));
    }

    private float centerXForBar(int width, float horizontalInset, float slotWidth, int index,
            boolean rightSide) {
        float centerX = width / 2f;
        float offset = (index + 0.5f) * slotWidth;
        return rightSide ? centerX + offset : centerX - offset;
    }

    private void ensureGradient(int width) {
        if (mSpectrumGradient != null && mGradientWidth == width) {
            return;
        }
        mGradientWidth = width;
        mSpectrumGradient = new LinearGradient(
                0f,
                0f,
                width,
                0f,
                new int[] {
                        Color.argb(112, 22, 124, 205),
                        Color.rgb(34, 207, 239),
                        Color.rgb(205, 239, 218),
                        Color.rgb(34, 207, 239),
                        Color.argb(112, 22, 124, 205)
                },
                new float[] {0f, 0.33f, 0.5f, 0.67f, 1f},
                Shader.TileMode.CLAMP);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
