package com.hivi.launcher.ai.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import com.hivi.launcher.utils.log.AppLog;
import android.view.View;
import android.view.animation.LinearInterpolator;

public class VoiceWaveformView extends View {
    // 常量定义
    private static final int BAR_COUNT = 5;
    private static final int ANIM_DURATION = 1500;
    private static final float MAX_HEIGHT_RATIO = 0.95f;
    private static final float MIN_HEIGHT_RATIO = 0.02f;
    private static final int BAR_WIDTH_DP = 9;
    private static final int BAR_SPACING_DP = 6;
    private static final int BAR_CORNER_DP = 4;

    // 音频处理参数
    private static final int SMOOTHING_WINDOW = 3;
    private static final float DECAY_FACTOR = 0.85f;
    private static final float RESPONSE_THRESHOLD = 0.05f;
    private static final long MIN_UPDATE_INTERVAL = 30; // 更快的响应

    // 画笔和图形对象
    private Paint[] mBarPaints = new Paint[BAR_COUNT];
    private RectF mBarRect = new RectF();

    // 尺寸参数
    private int mBarWidth;
    private int mBarSpacing;
    private float mBarCornerRadius;

    // 动画和状态
    private float mAnimProgress;
    private float[] mBarHeights = new float[BAR_COUNT];

    // 音频数据
    private float[] mAudioAmplitudes = new float[BAR_COUNT];
    private float[] mSmoothedAmplitudes = new float[BAR_COUNT];
    private float[] mAmplitudeHistory = new float[SMOOTHING_WINDOW];
    private int mHistoryIndex = 0;
    private long mLastUpdateTime = 0;

    // 音频特征检测
    private float mCurrentAmplitude = 0f;
    private float mPeakAmplitude = 0f;
    private long mLastPeakTime = 0;
    private float mAverageAmplitude = 0f;
    private int mSampleCount = 0;

    // 响应模式参数
    private float mSensitivity = 10f;
    private float[] mResponsePattern = new float[BAR_COUNT]; // 每个柱子的响应模式

    public VoiceWaveformView(Context context) {
        super(context);
        init();
    }

    public VoiceWaveformView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public VoiceWaveformView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public VoiceWaveformView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        try {
            // 配合阴影实现发光效果
            setLayerType(LAYER_TYPE_SOFTWARE, null);

            // 每个柱子的默认颜色
            int[] barColors = new int[]{
                    Color.parseColor("#f9f7ce"),
                    Color.parseColor("#ffc08d"),
                    Color.parseColor("#a488fe"),
                    Color.parseColor("#6ea8ff"),
                    Color.parseColor("#abd8d2")
            };

            float glowRadius = dp2px(3); // 发光模糊半径

            for (int i = 0; i < BAR_COUNT; i++) {
                Paint p = new Paint();
                int color = barColors[i % barColors.length];
                p.setColor(color);
                p.setAntiAlias(true);
                p.setStyle(Paint.Style.FILL);
                p.setShadowLayer(glowRadius, 0, 0, color);
                mBarPaints[i] = p;
            }

            mBarWidth = dp2px(BAR_WIDTH_DP);
            mBarSpacing = dp2px(BAR_SPACING_DP);
            mBarCornerRadius = dp2px(BAR_CORNER_DP);

            resetAudioData();
            // 动画由外部（悬浮条 show/hide）显式启停，避免视图不可见时空转。
        } catch (Exception e) {
            AppLog.e("VoiceWaveformView", "初始化失败", e);
            resetAudioData();
        }
    }

    ValueAnimator animator;

    public void startAnimation() {
        try {
            if (animator != null) {
                stopAnimation();
            }

            animator = ValueAnimator.ofFloat(0, 1);
            animator.setDuration(ANIM_DURATION);
            animator.setInterpolator(new LinearInterpolator());
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.addUpdateListener(animation -> {
                mAnimProgress = (float) animation.getAnimatedValue();
                calculateBarHeights();
                invalidate();
            });
            animator.start();
        } catch (Exception e) {
            AppLog.e("VoiceWaveformView", "启动动画失败", e);
        }
    }

    public void stopAnimation(){
        try {
            if (animator != null) {
                animator.cancel();
                animator = null;
            }
        } catch (Exception e) {
            AppLog.e("VoiceWaveformView", "停止动画失败", e);
            animator = null;
        }
    }

    /**
     * 更新音频数据 - 主要入口方法
     */
    public void updateAudioData(byte[] audioData, int samplingRate) {
        if (audioData == null || audioData.length == 0) return;

        long currentTime = System.currentTimeMillis();
        if (currentTime - mLastUpdateTime < MIN_UPDATE_INTERVAL) {
            return;
        }
        mLastUpdateTime = currentTime;

        // 计算当前振幅
        float amplitude = calculateAmplitude(audioData);
        mCurrentAmplitude = amplitude;

        // 更新音频特征
        updateAudioFeatures(amplitude, currentTime);

        // 根据音频特征分配振幅
        distributeAmplitudesByFeature();

        // 平滑处理
        smoothAmplitudes();
    }

    /**
     * 计算音频振幅（改进版本）
     */
    private float calculateAmplitude(byte[] audioData) {
        if (audioData.length < 2) return 0f;

        float sum = 0;
        int sampleCount = 0;
        float maxSample = 0;

        // 处理16位PCM音频数据
        for (int i = 0; i < audioData.length - 1; i += 2) {
            short sample = (short) ((audioData[i + 1] << 8) | (audioData[i] & 0xFF));
            float absSample = Math.abs(sample);
            sum += absSample;
            if (absSample > maxSample) {
                maxSample = absSample;
            }
            sampleCount++;
        }

        if (sampleCount == 0) return 0f;

        // 使用均值和最大值结合的方式，更敏感
        float average = sum / sampleCount;
        float normalizedAvg = average / 32768.0f;
        float normalizedMax = maxSample / 32768.0f;

        return Math.min(1.0f, (normalizedAvg * 0.7f + normalizedMax * 0.3f) * mSensitivity);
    }

    /**
     * 更新音频特征检测
     */
    private void updateAudioFeatures(float amplitude, long currentTime) {
        // 更新峰值检测
        if (amplitude > mPeakAmplitude) {
            mPeakAmplitude = amplitude;
            mLastPeakTime = currentTime;
        }

        // 峰值衰减
        if (currentTime - mLastPeakTime > 200) { // 200ms后开始衰减峰值
            mPeakAmplitude *= 0.95f;
        }

        // 更新平均值
        mAverageAmplitude = (mAverageAmplitude * mSampleCount + amplitude) / (mSampleCount + 1);
        mSampleCount = Math.min(mSampleCount + 1, 100); // 限制历史长度
    }

    /**
     * 基于音频特征分配振幅
     */
    private void distributeAmplitudesByFeature() {
        float amplitude = mCurrentAmplitude;

        // 只有明显的声音才更新
        if (amplitude < RESPONSE_THRESHOLD) {
            for (int i = 0; i < BAR_COUNT; i++) {
                mAudioAmplitudes[i] *= DECAY_FACTOR * 0.7f;
            }
            return;
        }

        // 检测节奏变化 - 使用峰值和平均值的差异
        float rhythmIntensity = Math.abs(mPeakAmplitude - mAverageAmplitude);
        float volumeIntensity = amplitude;

        // 五个柱子不同的响应模式（参考3柱逻辑，扩展到5柱）：

        // 柱子0（最左）：响应节奏变化（鼓点、音节起始）
        float leftResponse = rhythmIntensity * 1.5f;
        if (rhythmIntensity > 0.1f) {
            leftResponse *= 1.2f; // 强节奏时增强响应
        }

        // 柱子1：响应低音和节奏
        float leftMidResponse = volumeIntensity * 0.9f + rhythmIntensity * 0.6f;
        if (volumeIntensity > 0.2f && volumeIntensity < 0.5f) {
            leftMidResponse *= 1.2f;
        }

        // 柱子2（中间）：响应持续音量（说话持续部分）
        float middleResponse = volumeIntensity * 1.0f;
        // 中音柱对中等音量最敏感
        if (volumeIntensity > 0.2f && volumeIntensity < 0.6f) {
            middleResponse *= 1.3f;
        }

        // 柱子3：响应中高音
        float rightMidResponse = volumeIntensity * 0.9f + rhythmIntensity * 0.5f;
        if (amplitude > 0.3f) {
            rightMidResponse *= 1.2f;
        }

        // 柱子4（最右）：响应高音和突发声音（强调、高音部分）
        float rightResponse = volumeIntensity * 0.8f + rhythmIntensity * 0.7f;
        if (amplitude > 0.4f) { // 高音量时右柱响应更强
            rightResponse *= 1.4f;
        }

        // 应用响应，保留最大值（参考文件使用Math.max）
        mAudioAmplitudes[0] = Math.max(mAudioAmplitudes[0] * DECAY_FACTOR, leftResponse);
        mAudioAmplitudes[1] = Math.max(mAudioAmplitudes[1] * DECAY_FACTOR, leftMidResponse);
        mAudioAmplitudes[2] = Math.max(mAudioAmplitudes[2] * DECAY_FACTOR, middleResponse);
        mAudioAmplitudes[3] = Math.max(mAudioAmplitudes[3] * DECAY_FACTOR, rightMidResponse);
        mAudioAmplitudes[4] = Math.max(mAudioAmplitudes[4] * DECAY_FACTOR, rightResponse);

        // 添加随机变化，模拟真实音频的细微差别（参考文件逻辑）
        if (amplitude > 0.3f) {
            int randomBar = (int) (Math.random() * BAR_COUNT);
            float randomBoost = 1.0f + (float) (Math.random() * 0.4f - 0.2f);
            mAudioAmplitudes[randomBar] = Math.max(mAudioAmplitudes[randomBar], amplitude * randomBoost);
        }

        // 限制最大值
        for (int i = 0; i < BAR_COUNT; i++) {
            mAudioAmplitudes[i] = Math.min(1.0f, mAudioAmplitudes[i]);
        }
    }

    /**
     * 平滑处理振幅
     */
    private void smoothAmplitudes() {
        for (int i = 0; i < BAR_COUNT; i++) {
            // 较强的平滑，让变化更自然（参考文件：60%旧值 + 40%新值）
            mSmoothedAmplitudes[i] = mSmoothedAmplitudes[i] * 0.6f + mAudioAmplitudes[i] * 0.4f;
        }
    }

    private void calculateBarHeights() {
        int availableHeight = getHeight();
        if (availableHeight == 0) return;

        float maxHeight = availableHeight * MAX_HEIGHT_RATIO;
        float minHeight = availableHeight * MIN_HEIGHT_RATIO;
        float amplitudeRange = maxHeight - minHeight;

        for (int i = 0; i < BAR_COUNT; i++) {
            // 音频数据主导（80%），动画效果辅助（20%）
            float audioFactor = mSmoothedAmplitudes[i];
            float animFactor = (float) Math.abs(Math.sin(mAnimProgress * Math.PI * 2 + i * 0.5f));

            // 结合音频和动画，但以音频为主
            float combinedFactor = audioFactor * 0.8f + animFactor * 0.2f;

            float height = minHeight + amplitudeRange * combinedFactor;
            mBarHeights[i] = height * dbPercent / 100;

            // 确保最小高度可见
            if (mBarHeights[i] < minHeight * 0.5f) {
                mBarHeights[i] = minHeight * 0.5f;
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stopAnimation();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() == 0 || getHeight() == 0) return;

        int totalBarsWidth = BAR_COUNT * mBarWidth + (BAR_COUNT - 1) * mBarSpacing;
        int startX = (getWidth() - totalBarsWidth) / 2;
        int centerY = getHeight() / 2;

        for (int i = 0; i < BAR_COUNT; i++) {
            int barX = startX + i * (mBarWidth + mBarSpacing);
            float halfHeight = mBarHeights[i] / 2;
            int top = (int) (centerY - halfHeight);
            int bottom = (int) (centerY + halfHeight);

            mBarRect.set(barX, top, barX + mBarWidth, bottom);
            Paint paint = mBarPaints[i];
            if (paint == null) {
                // 理论上不会发生，兜底使用第一个画笔
                paint = mBarPaints[0];
            }
            canvas.drawRoundRect(mBarRect, mBarCornerRadius, mBarCornerRadius, paint);
        }
    }

    /**
     * 重置音频数据
     */
    public void resetAudioData() {
        for (int i = 0; i < BAR_COUNT; i++) {
            mAudioAmplitudes[i] = 0f;
            mSmoothedAmplitudes[i] = 0f;
            mResponsePattern[i] = 0f;
        }
        for (int i = 0; i < SMOOTHING_WINDOW; i++) {
            mAmplitudeHistory[i] = 0f;
        }
        mHistoryIndex = 0;
        mCurrentAmplitude = 0f;
        mPeakAmplitude = 0f;
        mAverageAmplitude = 0f;
        mSampleCount = 0;
        mLastPeakTime = 0;
    }

    /**
     * 设置灵敏度
     */
    public void setSensitivity(float sensitivity) {
        mSensitivity = Math.max(1.0f, Math.min(5.0f, sensitivity));
    }

    /**
     * 设置柱子颜色
     */
    public void setBarColor(int color) {
        for (int i = 0; i < BAR_COUNT; i++) {
            if (mBarPaints[i] != null) {
                mBarPaints[i].setColor(color);
                // 同步更新阴影颜色，保证发光颜色一致
                mBarPaints[i].setShadowLayer(dp2px(3), 0, 0, color);
            }
        }
        invalidate();
    }

    /**
     * 获取当前音频振幅（用于调试）
     */
    public float getCurrentAmplitude() {
        return mCurrentAmplitude;
    }

    /**
     * 获取各柱子振幅（用于调试）
     */
    public float[] getBarAmplitudes() {
        return mSmoothedAmplitudes.clone();
    }

    // dp转px工具方法
    private int dp2px(int dp) {
        float density = getContext().getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }

    int dbPercent = 100;
    public void setDbPercent(int currentPercent) {
        dbPercent = Math.max(0, Math.min(100, currentPercent));
    }
}