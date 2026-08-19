package com.hivi.launcher.ai.ui;

import com.hivi.launcher.customview.ParticleVisualizerView;
import com.hivi.launcher.main.model.MainPage;
import com.hivi.launcher.main.ui.MainActivity;

import java.lang.ref.WeakReference;

/**
 * 唤醒后、AI 页面打开前的无界面渲染目标：把会话状态渲染到左上角监听悬浮条。
 *
 * <p>首个对话正文（appendAssistantResponse）到达时才请求打开 AI 页——IoT 指令、
 * 提示音 TTS 不会触发页面切换，与 HiviAudio 的分流一致。</p>
 */
public final class AiHeadlessConversationView implements AiView {
    private final WeakReference<MainActivity> mActivity;
    private boolean mAiPageRequested;

    public AiHeadlessConversationView(MainActivity activity) {
        mActivity = new WeakReference<>(activity);
    }

    @Override
    public void renderConversationState(ParticleVisualizerView.State state, String statusText) {
        AiListeningOverlay overlay = AiListeningOverlay.getInstance();
        if (state == ParticleVisualizerView.State.LISTENING) {
            overlay.show(statusText);
        } else if (state == ParticleVisualizerView.State.IDLE) {
            overlay.hide();
        } else {
            // THINKING（STT 文案）/ SPEAKING：悬浮条保留并更新文字。
            overlay.updateStatusText(statusText);
        }
    }

    @Override
    public void clearAssistantResponse() {
        mAiPageRequested = false;
    }

    @Override
    public void appendAssistantResponse(String responseText) {
        if (mAiPageRequested) {
            return;
        }
        mAiPageRequested = true;
        MainActivity activity = mActivity.get();
        if (activity != null) {
            // 其他应用（如 QQ 音乐）在前台时拉回 launcher 前台再打开 AI 对话页。
            activity.bringToFrontAndShowPage(MainPage.AI);
            AiListeningOverlay.getInstance().hide();
        }
    }

    @Override
    public void setParticleVolume(float volume) {
        AiListeningOverlay.getInstance().updateWaveform(volume);
    }

    @Override
    public void onIotCommandHandled() {
        AiListeningOverlay.getInstance().hide();
    }

    @Override
    public void requestRecordAudioPermission() {
        // 系统应用已持有 RECORD_AUDIO，唤醒链路不会走到这里。
    }

    @Override
    public void requestHomeNavigation() {
        // 悬浮条模式没有 AI 页可退：停留在当前页面，仅隐藏悬浮条。
        AiListeningOverlay.getInstance().hide();
    }

    @Override
    public void requestMusicPageNavigation() {
        // 点播音乐：隐藏悬浮条并切到 WiFi 音乐页，QQ 音乐随后打开覆盖其上；
        // QQ 音乐已在前台（连续点播）时也先把 launcher 拉回前台再切页。
        MainActivity activity = mActivity.get();
        if (activity != null) {
            activity.bringToFrontAndShowPage(MainPage.WIFI);
        }
        AiListeningOverlay.getInstance().hide();
    }

    @Override
    public void showToast(String message) {
        MainActivity activity = mActivity.get();
        if (activity != null) {
            activity.showToast(message);
        }
    }
}
