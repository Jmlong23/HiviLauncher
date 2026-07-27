package com.hivi.launcher.hdmi.ui;

import com.hivi.launcher.R;
import com.hivi.launcher.hdmi.presenter.HdmiPresenter;
import com.hivi.launcher.base.BaseFragment;

public final class HdmiFragment extends BaseFragment<HdmiPresenter>
        implements HdmiView {
    @Override
    protected HdmiPresenter createPresenter() {
        return new HdmiPresenter(this);
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.fragment_hdmi;
    }

    @Override
    protected int getPageTitleResId() {
        return R.string.input_mode_hdmi;
    }
}
