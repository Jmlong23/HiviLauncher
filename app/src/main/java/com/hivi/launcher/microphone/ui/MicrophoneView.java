package com.hivi.launcher.microphone.ui;

import com.hivi.launcher.base.BaseView;

public interface MicrophoneView extends BaseView {
    void renderMicrophonePage(int volumePercent, boolean muted, boolean microphoneConnected);
}
