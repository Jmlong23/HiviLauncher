package com.hivi.launcher.microphone.presenter;

import android.content.Context;

import com.hivi.launcher.base.BasePresenter;
import com.hivi.launcher.microphone.model.MicrophoneModel;
import com.hivi.launcher.microphone.ui.MicrophoneView;

public final class MicrophonePresenter extends BasePresenter<MicrophoneView> {
    private final MicrophoneModel mModel = new MicrophoneModel();

    public MicrophonePresenter(MicrophoneView view) {
        super(view);
    }

    public void init(Context context) {
        mModel.start(context, (volumePercent, muted, microphoneConnected) -> {
            MicrophoneView view = getView();
            if (view != null) {
                view.renderMicrophonePage(volumePercent, muted, microphoneConnected);
            }
        });
    }

    public void adjustVolume(int direction) {
        mModel.adjustVolume(direction);
    }

    public void setVolumePercent(int volumePercent) {
        mModel.setVolumePercent(volumePercent);
    }

    public void toggleMute() {
        mModel.toggleMute();
    }

    @Override
    public void detach() {
        mModel.stop();
        super.detach();
    }
}
