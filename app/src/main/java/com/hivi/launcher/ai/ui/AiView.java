package com.hivi.launcher.ai.ui;

import com.hivi.launcher.base.BaseView;
import com.hivi.launcher.customview.ParticleVisualizerView;

public interface AiView extends BaseView {
    void renderConversationState(ParticleVisualizerView.State state, String statusText);

    void clearAssistantResponse();

    void appendAssistantResponse(String responseText);

    void setParticleVolume(float volume);

    void requestRecordAudioPermission();
}
