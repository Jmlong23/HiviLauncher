package com.hivi.launcher.coax.ui;

import com.hivi.launcher.R;
import com.hivi.launcher.coax.presenter.CoaxPresenter;
import com.hivi.launcher.base.BaseFragment;

public final class CoaxFragment extends BaseFragment<CoaxPresenter>
        implements CoaxView {
    @Override
    protected CoaxPresenter createPresenter() {
        return new CoaxPresenter(this);
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.fragment_coax;
    }

    @Override
    protected int getPageTitleResId() {
        return R.string.input_mode_coax;
    }
}
