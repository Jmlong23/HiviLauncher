package com.hivi.launcher.settings.ui;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;

import com.hivi.launcher.R;
import com.hivi.launcher.base.BaseFragment;
import com.hivi.launcher.databinding.LayoutSettingsContentBinding;
import com.hivi.launcher.settings.presenter.SettingsPresenter;

public final class SettingsFragment extends BaseFragment<SettingsPresenter>
        implements SettingsView {
    private LayoutSettingsContentBinding mBinding;
    private View[] mSectionTabs;
    private View[] mSectionPanels;

    @Override
    protected SettingsPresenter createPresenter() {
        return new SettingsPresenter(this);
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.layout_settings_content;
    }

    @Override
    protected int getPageTitleResId() {
        return R.string.settings_title;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mBinding = LayoutSettingsContentBinding.bind(view);
        mSectionTabs = new View[] {
                mBinding.settingsTabNetwork,
                mBinding.settingsTabDisplay,
                mBinding.settingsTabSystem,
                mBinding.settingsTabAbout,
                mBinding.settingsTabMaintenance
        };
        mSectionPanels = new View[] {
                mBinding.settingsPanelNetwork,
                mBinding.settingsPanelDisplay,
                mBinding.settingsPanelSystem,
                mBinding.settingsPanelAbout,
                mBinding.settingsPanelMaintenance
        };

        SettingsPresenter presenter = getPresenter();
        if (presenter == null) {
            return;
        }
        for (int i = 0; i < mSectionTabs.length; i++) {
            final int section = i;
            mSectionTabs[i].setOnClickListener(v -> presenter.onSectionSelected(section));
        }
        presenter.init();
    }

    @Override
    public void onDestroyView() {
        mSectionTabs = null;
        mSectionPanels = null;
        mBinding = null;
        super.onDestroyView();
    }

    @Override
    public void renderSettingsSection(int section) {
        if (mSectionTabs == null || mSectionPanels == null) {
            return;
        }
        for (int i = 0; i < mSectionTabs.length; i++) {
            boolean selected = i == section;
            mSectionTabs[i].setSelected(selected);
            mSectionPanels[i].setVisibility(selected ? View.VISIBLE : View.GONE);
        }
    }
}
