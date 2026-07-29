package com.hivi.launcher.wifi.ui;

import com.hivi.launcher.base.BaseView;
import com.hivi.launcher.music.model.UpnpPlaybackState;
import com.hivi.launcher.wifi.model.WifiMusicApp;

public interface WifiMusicView extends BaseView {
    void renderWifiPlayback(UpnpPlaybackState state);

    void renderMusicAppSelection(WifiMusicApp app);
}
