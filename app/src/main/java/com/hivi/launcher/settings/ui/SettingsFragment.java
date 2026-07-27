package com.hivi.launcher.settings.ui;

import com.hivi.launcher.R;
import com.hivi.launcher.base.BaseFragment;
import com.hivi.launcher.settings.presenter.SettingsPresenter;

public final class SettingsFragment extends BaseFragment<SettingsPresenter>
        implements SettingsView {
    @Override
    protected SettingsPresenter createPresenter() {
        return new SettingsPresenter(this);
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.fragment_settings;
    }

    @Override
    protected int getPageTitleResId() {
        return R.string.main_bottom_navigation_settings;
    }
}
