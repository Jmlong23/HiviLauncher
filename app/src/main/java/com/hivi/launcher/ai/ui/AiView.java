package com.hivi.launcher.ai.ui;

import com.hivi.launcher.base.BaseView;
import com.hivi.launcher.customview.ParticleVisualizerView;

public interface AiView extends BaseView {
    void renderConversationState(ParticleVisualizerView.State state, String statusText);

    void clearAssistantResponse();

    void appendAssistantResponse(String responseText);

    void setParticleVolume(float volume);

    void requestRecordAudioPermission();

    void requestHomeNavigation();

    /** IoT 指令已在本机执行：悬浮条模式隐藏悬浮条，页面模式无需处理。 */
    default void onIotCommandHandled() {
    }

    /**
     * 当前会话是否渲染在 AI 对话页面上。headless 悬浮条模式返回 false；
     * 页面模式下 IoT 指令执行后应继续对话，而不是收尾会话退出页面。
     */
    default boolean isConversationPageActive() {
        return false;
    }
}
