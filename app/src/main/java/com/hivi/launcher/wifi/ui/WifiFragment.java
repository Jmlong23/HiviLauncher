package com.hivi.launcher.wifi.ui;

import com.hivi.launcher.R;
import com.hivi.launcher.base.BaseFragment;
import com.hivi.launcher.wifi.presenter.WifiPresenter;

public final class WifiFragment extends BaseFragment<WifiPresenter>
        implements WifiView {
    @Override
    protected WifiPresenter createPresenter() {
        return new WifiPresenter(this);
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.fragment_wifi;
    }

    @Override
    protected int getPageTitleResId() {
        return R.string.input_mode_wifi_music;
    }
}
