package com.hivi.launcher.microphone.presenter;

import android.content.Context;

import com.hivi.launcher.base.BasePresenter;
import com.hivi.launcher.microphone.model.MicrophoneModel;
import com.hivi.launcher.microphone.model.MicrophoneModel.VolumeChannel;
import com.hivi.launcher.microphone.ui.MicrophoneView;

public final class MicrophonePresenter extends BasePresenter<MicrophoneView> {
    private final MicrophoneModel mModel = new MicrophoneModel();

    public MicrophonePresenter(MicrophoneView view) {
        super(view);
    }

    public void init(Context context) {
        mModel.start(context, (amplifierVolumePercent, amplifierMuted, microphoneVolumePercent,
                microphoneMuted, effectVolumePercent, effectMuted, microphoneConnected) -> {
            MicrophoneView view = getView();
            if (view != null) {
                view.renderMicrophonePage(amplifierVolumePercent, amplifierMuted,
                        microphoneVolumePercent, microphoneMuted, effectVolumePercent,
                        effectMuted, microphoneConnected);
            }
        });
    }

    public void adjustVolume(VolumeChannel channel, int direction) {
        mModel.adjustVolume(channel, direction);
    }

    public void setVolumePercent(VolumeChannel channel, int volumePercent) {
        mModel.setVolumePercent(channel, volumePercent);
    }

    public void toggleMute(VolumeChannel channel) {
        mModel.toggleMute(channel);
    }

    @Override
    public void detach() {
        mModel.stop();
        super.detach();
    }
}
