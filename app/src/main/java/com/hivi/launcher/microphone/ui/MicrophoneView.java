package com.hivi.launcher.microphone.ui;

import com.hivi.launcher.base.BaseView;

public interface MicrophoneView extends BaseView {
    void renderMicrophonePage(int amplifierVolumePercent, boolean amplifierMuted,
            int microphoneVolumePercent, boolean microphoneMuted, int effectVolumePercent,
            boolean effectMuted, boolean microphoneConnected);
}
