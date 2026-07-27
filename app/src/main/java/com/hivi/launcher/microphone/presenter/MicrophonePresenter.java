package com.hivi.launcher.microphone.presenter;

import com.hivi.launcher.base.BasePresenter;
import com.hivi.launcher.microphone.model.MicrophoneModel;
import com.hivi.launcher.microphone.ui.MicrophoneView;

public final class MicrophonePresenter extends BasePresenter<MicrophoneView> {
    private final MicrophoneModel mModel = new MicrophoneModel();

    public MicrophonePresenter(MicrophoneView view) {
        super(view);
    }
}
