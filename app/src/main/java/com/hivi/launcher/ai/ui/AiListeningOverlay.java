package com.hivi.launcher.ai.ui;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
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
 *
 * <p>系统级窗口：launcher 预装于 /system/priv-app（sharedUserId=android.uid.system，
 * SYSTEM_ALERT_WINDOW 已随 uid 授予）。首次 show 时把悬浮条挂到 WindowManager 的
 * TYPE_APPLICATION_OVERLAY 窗口——QQ 音乐等第三方应用在前台时悬浮条依然置顶
 * （触摸穿透）。addView 因权限被拒时自动降级为挂 MainActivity 根容器（仅 launcher 内）。</p>
 */
public final class AiListeningOverlay {
    private static final String TAG = "AiListeningOverlay";
    private static final long FADE_IN_DURATION_MS = 300L;
    private static final long FADE_OUT_DURATION_MS = 450L;
    /** 左上角，紧贴顶部状态栏下方。 */
    private static final int OVERLAY_LEFT_DP = 33;
    private static final int OVERLAY_TOP_DP = 55;

    private static final AiListeningOverlay INSTANCE = new AiListeningOverlay();

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private Context mAppContext;
    private boolean mSystemWindowMode;
    /** 系统窗口创建被拒（无悬浮窗权限）后置位，避免每次 show 重试。 */
    private boolean mSystemWindowDenied;
    /** 应用内回退模式的宿主容器。 */
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

    /** 记录上下文与回退宿主；系统窗口在首次 show 时懒创建，重复调用以最后一次为准。 */
    public void attach(Context context, ViewGroup rootView) {
        detach();
        Context appContext = context == null ? null : context.getApplicationContext();
        mAppContext = appContext;
        mRootView = rootView;
    }

    public void detach() {
        if (mSystemWindowMode) {
            removeSystemWindow();
        } else if (mOverlayView != null && mRootView != null) {
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
            if (mOverlayView == null && !ensureOverlayView()) {
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
                        if (mSystemWindowMode) {
                            removeSystemWindow();
                        } else {
                            mOverlayView.setVisibility(View.GONE);
                            mOverlayView.setAlpha(1f);
                        }
                        setStatusText(null);
                    })
                    .start();
        });
    }

    /** 懒创建悬浮条视图：先试系统窗口（真实权限校验在 addView 时发生），失败降级为应用内挂载。 */
    private boolean ensureOverlayView() {
        if (mOverlayView == null && !mSystemWindowDenied && mAppContext != null) {
            try {
                createSystemWindow();
                mSystemWindowMode = true;
                AppLog.i(TAG, "listening overlay uses system window mode");
            } catch (Throwable e) {
                // 无悬浮窗权限（普通安装）或窗口类型被拒：此后不再重试系统窗口。
                mSystemWindowDenied = true;
                AppLog.w(TAG, "add system overlay window failed, fallback to in-app");
            }
        }
        if (!mSystemWindowMode && mOverlayView == null && mRootView != null) {
            try {
                attachInAppView();
            } catch (Throwable e) {
                AppLog.e(TAG, "attach listening overlay failed", e);
            }
        }
        return mOverlayView != null;
    }

    private void createSystemWindow() {
        if (mOverlayView != null || mAppContext == null) {
            return;
        }
        View overlay = LayoutInflater.from(mAppContext)
                .inflate(R.layout.view_ai_listening, null, false);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = dp(OVERLAY_LEFT_DP);
        params.y = dp(OVERLAY_TOP_DP);
        windowManager().addView(overlay, params);
        bindView(overlay);
    }

    private void attachInAppView() {
        if (mOverlayView != null || mRootView == null) {
            return;
        }
        View overlay = LayoutInflater.from(mRootView.getContext())
                .inflate(R.layout.view_ai_listening, mRootView, false);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = dp(OVERLAY_LEFT_DP);
        params.topMargin = dp(OVERLAY_TOP_DP);
        overlay.setLayoutParams(params);
        overlay.setVisibility(View.GONE);
        mRootView.addView(overlay);
        bindView(overlay);
    }

    private void removeSystemWindow() {
        if (mOverlayView == null) {
            return;
        }
        try {
            windowManager().removeView(mOverlayView);
        } catch (Throwable ignored) {
        }
        mOverlayView = null;
        mWaveformView = null;
        mStatusView = null;
    }

    private WindowManager windowManager() {
        return (WindowManager) mAppContext.getSystemService(Context.WINDOW_SERVICE);
    }

    private void bindView(View overlay) {
        mOverlayView = overlay;
        mWaveformView = overlay.findViewById(R.id.voice_waveform_view);
        mStatusView = overlay.findViewById(R.id.tv_listening_status);
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
        mSystemWindowMode = false;
        mSystemWindowDenied = false;
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
