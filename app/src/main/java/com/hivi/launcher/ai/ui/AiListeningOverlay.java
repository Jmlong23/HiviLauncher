package com.hivi.launcher.ai.ui;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.hivi.launcher.R;
import com.hivi.launcher.utils.log.AppLog;

import java.nio.ByteBuffer;

/**
 * 左上角 AI 监听悬浮条（自 HiviAudio 迁移）。
 *
 * <p>唤醒后先显示悬浮条进行聆听；普通对话进入 AI 页面前、IoT 指令执行后隐藏。
 * 波形由麦克风音量驱动：{@link #updateWaveform(float)} 把归一化音量合成为 16bit PCM
 * 喂给 {@link VoiceWaveformView#updateAudioData(byte[], int)}，保持与 HiviAudio 相同的
 * 振幅分配与平滑逻辑。</p>
 */
public final class AiListeningOverlay {
    private static final String TAG = "AiListeningOverlay";
    private static final long FADE_IN_DURATION_MS = 300L;
    private static final long FADE_OUT_DURATION_MS = 450L;
    /** 悬挂在 launcher_root 左上角，紧贴顶部状态栏下方。 */
    private static final int OVERLAY_LEFT_DP = 33;
    private static final int OVERLAY_TOP_DP = 55;

    private static final AiListeningOverlay INSTANCE = new AiListeningOverlay();

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private ViewGroup mRootView;
    private View mOverlayView;
    private VoiceWaveformView mWaveformView;
    private TextView mStatusView;
    private boolean mShowing;

    private AiListeningOverlay() {
    }

    public static AiListeningOverlay getInstance() {
        return INSTANCE;
    }

    /** 挂到 MainActivity 根容器；重复调用以最后一次为准。 */
    public void attach(Context context, ViewGroup rootView) {
        if (rootView == null) {
            return;
        }
        if (mRootView == rootView) {
            return;
        }
        detach();
        try {
            View overlay = LayoutInflater.from(context)
                    .inflate(R.layout.view_ai_listening, rootView, false);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.leftMargin = dp(OVERLAY_LEFT_DP);
            params.topMargin = dp(OVERLAY_TOP_DP);
            overlay.setLayoutParams(params);
            overlay.setVisibility(View.GONE);
            rootView.addView(overlay);
            mRootView = rootView;
            mOverlayView = overlay;
            mWaveformView = overlay.findViewById(R.id.voice_waveform_view);
            mStatusView = overlay.findViewById(R.id.tv_listening_status);
        } catch (Throwable e) {
            AppLog.e(TAG, "attach listening overlay failed", e);
            reset();
        }
    }

    public void detach() {
        if (mOverlayView != null && mRootView != null) {
            try {
                mRootView.removeView(mOverlayView);
            } catch (Throwable ignored) {
            }
        }
        reset();
    }

    public boolean isShowing() {
        return mShowing;
    }

    public void show(final String statusText) {
        runOnMain(() -> {
            if (mOverlayView == null) {
                return;
            }
            setStatusText(statusText);
            if (mShowing) {
                return;
            }
            mShowing = true;
            mOverlayView.bringToFront();
            mOverlayView.animate().cancel();
            mOverlayView.clearAnimation();
            mOverlayView.setAlpha(0f);
            mOverlayView.setVisibility(View.VISIBLE);
            mOverlayView.animate()
                    .alpha(1f)
                    .setDuration(FADE_IN_DURATION_MS)
                    .start();
            if (mWaveformView != null) {
                mWaveformView.setVisibility(View.VISIBLE);
                mWaveformView.resetAudioData();
                mWaveformView.post(() -> {
                    if (mWaveformView != null && mShowing) {
                        mWaveformView.startAnimation();
                    }
                });
            }
        });
    }

    public void updateStatusText(final String statusText) {
        runOnMain(() -> {
            if (!mShowing) {
                return;
            }
            setStatusText(statusText);
        });
    }

    public void updateWaveform(final float volume) {
        runOnMain(() -> {
            if (!mShowing || mWaveformView == null) {
                return;
            }
            mWaveformView.updateAudioData(synthesizePcm(volume), 16_000);
        });
    }

    public void hide() {
        runOnMain(() -> {
            if (mOverlayView == null || !mShowing) {
                return;
            }
            mShowing = false;
            if (mWaveformView != null) {
                mWaveformView.stopAnimation();
                mWaveformView.resetAudioData();
            }
            mOverlayView.animate().cancel();
            mOverlayView.animate()
                    .alpha(0f)
                    .setDuration(FADE_OUT_DURATION_MS)
                    .withEndAction(() -> {
                        if (mOverlayView == null || mShowing) {
                            return;
                        }
                        mOverlayView.setVisibility(View.GONE);
                        mOverlayView.setAlpha(1f);
                        setStatusText(null);
                    })
                    .start();
        });
    }

    private void setStatusText(String statusText) {
        if (mStatusView == null) {
            return;
        }
        mStatusView.setText(statusText == null ? "" : statusText);
    }

    private void runOnMain(Runnable action) {
        if (Looper.myLooper() == mMainHandler.getLooper()) {
            action.run();
        } else {
            mMainHandler.post(action);
        }
    }

    private void reset() {
        mRootView = null;
        mOverlayView = null;
        mWaveformView = null;
        mStatusView = null;
        mShowing = false;
    }

    /**
     * 归一化音量（0..1）转 16bit 单声道噪声 PCM。振幅缩放 0.12 与
     * VoiceWaveformView 的灵敏度 10 相乘后约等于原始音量，波形高度随人声音量起伏。
     */
    private static byte[] synthesizePcm(float volume) {
        float clamped = Math.max(0f, Math.min(1f, volume));
        float amplitude = clamped * 0.12f;
        ByteBuffer buffer = ByteBuffer.allocate(512);
        for (int i = 0; i < 256; i++) {
            float noise = (float) (Math.random() * 2.0 - 1.0);
            buffer.putShort((short) (noise * amplitude * 32767f));
        }
        return buffer.array();
    }

    private static int dp(int value) {
        return Math.round(value
                * android.content.res.Resources.getSystem().getDisplayMetrics().density);
    }
}
