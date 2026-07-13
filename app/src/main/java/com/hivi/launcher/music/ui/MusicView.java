package com.hivi.launcher.music.ui;

import com.hivi.launcher.base.BaseView;
import com.hivi.launcher.music.model.UpnpPlaybackState;

public interface MusicView extends BaseView {
    void renderPlayback(UpnpPlaybackState state);
}
