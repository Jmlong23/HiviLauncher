package com.hivi.launcher.settings.ui;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.hivi.launcher.R;
import com.hivi.launcher.base.BaseFragment;
import com.hivi.launcher.databinding.DialogWifiPasswordBinding;
import com.hivi.launcher.databinding.LayoutSettingsContentBinding;
import com.hivi.launcher.settings.presenter.SettingsPresenter;
import com.hivi.launcher.wifi.model.WifiNetwork;
import com.hivi.launcher.wifi.presenter.WifiPresenter;
import com.hivi.launcher.wifi.ui.WifiNetworkAdapter;
import com.hivi.launcher.wifi.ui.WifiView;

import java.util.List;

public final class SettingsFragment extends BaseFragment<SettingsPresenter>
        implements SettingsView, WifiView {
    private LayoutSettingsContentBinding mBinding;
    private View[] mSectionTabs;
    private View[] mSectionPanels;
    private WifiPresenter mWifiPresenter;
    private WifiNetworkAdapter mWifiNetworkAdapter;
    private ObjectAnimator mWifiRefreshAnimator;
    private Dialog mWifiPasswordDialog;
    private boolean mWifiRefreshing;

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
        setupWifiSettings();
        presenter.init();
    }

    @Override
    public void onDestroyView() {
        dismissWifiPasswordDialog();
        if (mWifiRefreshAnimator != null) {
            mWifiRefreshAnimator.cancel();
            mWifiRefreshAnimator = null;
        }
        if (mWifiPresenter != null) {
            mWifiPresenter.destroy();
            mWifiPresenter = null;
        }
        mWifiNetworkAdapter = null;
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

    @Override
    public void renderWifiNetworks(List<WifiNetwork> networks, String connectedSsid) {
        if (mBinding == null || mWifiNetworkAdapter == null) {
            return;
        }
        mWifiNetworkAdapter.submitNetworks(networks);
        boolean hasNetworks = networks != null && !networks.isEmpty();
        mBinding.settingsWifiList.setVisibility(hasNetworks ? View.VISIBLE : View.GONE);
        if (hasNetworks) {
            mBinding.settingsWifiEmptyState.setVisibility(View.GONE);
        }
        if (TextUtils.isEmpty(connectedSsid)) {
            mBinding.settingsNetworkSummary.setText(R.string.main_disconnected);
        } else {
            mBinding.settingsNetworkSummary.setText(connectedSsid);
        }
    }

    @Override
    public void setWifiRefreshing(boolean refreshing) {
        if (mBinding == null) {
            return;
        }
        mWifiRefreshing = refreshing;
        mBinding.settingsWifiRefresh.setEnabled(!refreshing);
        if (refreshing) {
            if (mWifiRefreshAnimator == null) {
                mWifiRefreshAnimator = ObjectAnimator.ofFloat(mBinding.settingsWifiRefresh,
                        View.ROTATION, 0f, 360f);
                mWifiRefreshAnimator.setDuration(900L);
                mWifiRefreshAnimator.setRepeatCount(ObjectAnimator.INFINITE);
            }
            mWifiRefreshAnimator.start();
        } else {
            if (mWifiRefreshAnimator != null) {
                mWifiRefreshAnimator.cancel();
            }
            mBinding.settingsWifiRefresh.setRotation(0f);
            mBinding.settingsWifiLoading.setVisibility(View.GONE);
        }
    }

    @Override
    public void showWifiEmptyState(String message) {
        if (mBinding == null) {
            return;
        }
        mBinding.settingsWifiList.setVisibility(View.GONE);
        mBinding.settingsWifiEmptyText.setText(message);
        mBinding.settingsWifiEmptyState.setVisibility(mWifiRefreshing ? View.GONE : View.VISIBLE);
        mBinding.settingsWifiLoading.setVisibility(mWifiRefreshing ? View.VISIBLE : View.GONE);
        mBinding.settingsNetworkSummary.setText(R.string.main_disconnected);
    }

    @Override
    public void showWifiPasswordDialog(WifiNetwork network) {
        if (!isAdded() || network == null) {
            return;
        }
        dismissWifiPasswordDialog();
        Dialog dialog = new Dialog(getHostActivity());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        DialogWifiPasswordBinding dialogBinding = DialogWifiPasswordBinding.inflate(
                getLayoutInflater());
        dialog.setContentView(dialogBinding.getRoot());
        dialog.setCanceledOnTouchOutside(true);
        dialogBinding.wifiPasswordTitle.setText(network.getSsid());
        dialogBinding.wifiPasswordInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        dialogBinding.wifiPasswordShow.setOnCheckedChangeListener((buttonView, isChecked) -> {
            int selection = dialogBinding.wifiPasswordInput.getSelectionStart();
            dialogBinding.wifiPasswordInput.setInputType(isChecked
                    ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            dialogBinding.wifiPasswordInput.setSelection(Math.max(0, selection));
        });
        dialogBinding.wifiPasswordCancel.setOnClickListener(view -> dialog.dismiss());
        dialogBinding.wifiPasswordConnect.setOnClickListener(view -> {
            String password = dialogBinding.wifiPasswordInput.getText() == null ? ""
                    : dialogBinding.wifiPasswordInput.getText().toString();
            if (TextUtils.isEmpty(password.trim())) {
                showToast(getString(R.string.settings_wifi_password_required));
                return;
            }
            if (mWifiPresenter != null) {
                mWifiPresenter.connectWithPassword(network, password);
            }
            dialog.dismiss();
        });
        dialog.setOnDismissListener(ignored -> {
            if (mWifiPasswordDialog == dialog) {
                mWifiPasswordDialog = null;
            }
        });
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.dimAmount = 0.68f;
            window.setAttributes(attributes);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        mWifiPasswordDialog = dialog;
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(dp(690), WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private void setupWifiSettings() {
        mBinding.settingsNetworkSummary.setText(R.string.main_disconnected);
        mWifiNetworkAdapter = new WifiNetworkAdapter(getHostActivity(),
                network -> {
                    if (mWifiPresenter != null) {
                        mWifiPresenter.onWifiNetworkSelected(network);
                    }
                });
        mBinding.settingsWifiList.setLayoutManager(new LinearLayoutManager(getHostActivity()));
        mBinding.settingsWifiList.setAdapter(mWifiNetworkAdapter);
        mBinding.settingsWifiRefresh.setOnClickListener(view -> {
            if (mWifiPresenter != null) {
                mWifiPresenter.refresh();
            }
        });
        mWifiPresenter = new WifiPresenter(this);
        mWifiPresenter.init(getHostActivity());
    }

    private Activity getHostActivity() {
        Activity activity = getActivity();
        if (activity == null) {
            throw new IllegalStateException("SettingsFragment is not attached to an activity.");
        }
        return activity;
    }

    private void dismissWifiPasswordDialog() {
        if (mWifiPasswordDialog != null) {
            mWifiPasswordDialog.dismiss();
            mWifiPasswordDialog = null;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
