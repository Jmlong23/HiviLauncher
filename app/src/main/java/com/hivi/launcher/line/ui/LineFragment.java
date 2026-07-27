package com.hivi.launcher.line.ui;

import com.hivi.launcher.R;
import com.hivi.launcher.line.presenter.LinePresenter;
import com.hivi.launcher.base.BaseFragment;

public final class LineFragment extends BaseFragment<LinePresenter>
        implements LineView {
    @Override
    protected LinePresenter createPresenter() {
        return new LinePresenter(this);
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.fragment_line;
    }

    @Override
    protected int getPageTitleResId() {
        return R.string.input_mode_line;
    }
}
