package com.hivi.launcher.optical.ui;

import com.hivi.launcher.R;
import com.hivi.launcher.base.BaseFragment;
import com.hivi.launcher.optical.presenter.OpticalPresenter;

public final class OpticalFragment extends BaseFragment<OpticalPresenter>
        implements OpticalView {
    @Override
    protected OpticalPresenter createPresenter() {
        return new OpticalPresenter(this);
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.fragment_optical;
    }

    @Override
    protected int getPageTitleResId() {
        return R.string.input_mode_optical;
    }
}
