package com.hivi.launcher.microphone.ui;

import com.hivi.launcher.R;
import com.hivi.launcher.base.BaseFragment;
import com.hivi.launcher.microphone.presenter.MicrophonePresenter;

public final class MicrophoneFragment extends BaseFragment<MicrophonePresenter>
        implements MicrophoneView {
    @Override
    protected MicrophonePresenter createPresenter() {
        return new MicrophonePresenter(this);
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.fragment_microphone;
    }

    @Override
    protected int getPageTitleResId() {
        return R.string.input_mode_microphone;
    }
}
