package com.hivi.launcher.systemapps.ui;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;

import androidx.annotation.Nullable;

import com.hivi.launcher.R;
import com.hivi.launcher.base.BaseFragment;
import com.hivi.launcher.databinding.FragmentSystemAppsBinding;
import com.hivi.launcher.databinding.ItemAppGridBinding;
import com.hivi.launcher.main.ui.MainActivity;
import com.hivi.launcher.systemapps.model.AppEntry;
import com.hivi.launcher.systemapps.model.AppDownloadState;
import com.hivi.launcher.systemapps.model.SupportedApp;
import com.hivi.launcher.systemapps.presenter.SystemAppsPresenter;
import com.hivi.launcher.utils.FeedbackSound;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class SystemAppsFragment extends BaseFragment<SystemAppsPresenter>
        implements SystemAppsView {
    private static final int COLUMN_COUNT = 5;
    private final List<AppEntry> mApps = new ArrayList<>();
    private final Map<SupportedApp, ItemAppGridBinding> mAppBindings =
            new EnumMap<>(SupportedApp.class);
    private FragmentSystemAppsBinding mBinding;
    private final BroadcastReceiver mPackageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            SystemAppsPresenter presenter = getPresenter();
            if (presenter != null) {
                presenter.loadApps();
            }
        }
    };

    @Override
    protected SystemAppsPresenter createPresenter() {
        Activity activity = getActivity();
        if (activity == null) {
            throw new IllegalStateException("System apps fragment is not attached.");
        }
        return new SystemAppsPresenter(activity, this);
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.fragment_system_apps;
    }

    @Override
    protected int getPageTitleResId() {
        return R.string.system_apps_title;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mBinding = FragmentSystemAppsBinding.bind(view);
        mBinding.appsBackButton.setBackground(new HeaderButtonBackgroundDrawable(
                getResources(), getActivity().getTheme()));
        mBinding.appsVolumeButton.setBackground(new HeaderButtonBackgroundDrawable(
                getResources(), getActivity().getTheme()));
        mBinding.appsBackButton.setOnClickListener(ignored -> {
            FeedbackSound.playClick();
            closePage();
        });
        mBinding.appsVolumeButton.setOnClickListener(ignored -> {
            Activity activity = getActivity();
            if (activity instanceof MainActivity) {
                FeedbackSound.playClick();
                ((MainActivity) activity).showVolumeControl();
            }
        });
        Activity activity = getActivity();
        if (activity instanceof MainActivity) {
            renderVolume(((MainActivity) activity).getDisplayedVolumePercent());
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addDataScheme("package");
        getActivity().registerReceiver(mPackageReceiver, filter);
    }

    @Override
    public void onResume() {
        super.onResume();
        SystemAppsPresenter presenter = getPresenter();
        if (presenter != null) {
            presenter.loadApps();
        }
    }

    @Override
    public void onStop() {
        getActivity().unregisterReceiver(mPackageReceiver);
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        mAppBindings.clear();
        mApps.clear();
        mBinding = null;
        super.onDestroyView();
    }

    @Override
    public void showApps(List<AppEntry> apps) {
        mApps.clear();
        mApps.addAll(apps);
        if (mBinding == null) {
            return;
        }
        boolean sameCatalog = mAppBindings.size() == apps.size();
        for (AppEntry app : apps) {
            sameCatalog &= mAppBindings.containsKey(app.getSupportedApp());
        }
        if (sameCatalog) {
            for (AppEntry app : apps) {
                renderApp(mAppBindings.get(app.getSupportedApp()), app);
            }
            return;
        }
        mBinding.appsGrid.removeAllViews();
        mAppBindings.clear();
        for (int index = 0; index < apps.size(); index++) {
            View item = createAppGridItem(apps.get(index), mBinding.appsGrid);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec(index / COLUMN_COUNT),
                    GridLayout.spec(index % COLUMN_COUNT));
            mBinding.appsGrid.addView(item, params);
        }
        mBinding.getRoot().onItemsChanged();
    }

    @Override
    public void updateApp(AppEntry app) {
        for (int index = 0; index < mApps.size(); index++) {
            if (mApps.get(index).getSupportedApp() == app.getSupportedApp()) {
                mApps.set(index, app);
                break;
            }
        }
        ItemAppGridBinding itemBinding = mAppBindings.get(app.getSupportedApp());
        if (itemBinding != null) {
            renderApp(itemBinding, app);
        }
    }

    public void renderVolume(int volumePercent) {
        if (mBinding == null) {
            return;
        }
        mBinding.appsVolumeText.setText(String.valueOf(volumePercent));
        mBinding.appsVolumeIcon.setImageResource(volumePercent == 0
                ? R.drawable.ic_volume_mute : R.drawable.ic_volume);
        mBinding.appsVolumeButton.setContentDescription(getString(
                R.string.main_bottom_navigation_volume_format, volumePercent));
    }

    @Override
    public void closePage() {
        Activity activity = getActivity();
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).navigateBack();
        }
    }

    private View createAppGridItem(final AppEntry app, ViewGroup parent) {
        ItemAppGridBinding itemBinding = ItemAppGridBinding.inflate(
                LayoutInflater.from(getActivity()), parent, false);
        mAppBindings.put(app.getSupportedApp(), itemBinding);
        renderApp(itemBinding, app);
        return itemBinding.getRoot();
    }

    private void renderApp(ItemAppGridBinding itemBinding, AppEntry app) {
        AppDownloadState state = app.getDownloadState();
        boolean active = state == AppDownloadState.WAITING
                || state == AppDownloadState.DOWNLOADING || state == AppDownloadState.PAUSED
                || state == AppDownloadState.INSTALLING;
        boolean showDownloadIcon = state == AppDownloadState.NOT_INSTALLED
                || state == AppDownloadState.FAILED || state == AppDownloadState.INSTALL_FAILED;
        itemBinding.appIcon.setImageDrawable(app.getIcon());
        itemBinding.appIcon.setAlpha(showDownloadIcon ? 0.35f : 1f);
        itemBinding.appLabel.setText(app.getLabel());
        itemBinding.appDownloadIcon.setVisibility(showDownloadIcon ? View.VISIBLE : View.GONE);
        itemBinding.appDownloadProgress.setVisibility(active ? View.VISIBLE : View.GONE);
        itemBinding.appDownloadProgress.setDownloadState(state, app.getDownloadProgress());
        String status = getAppStatus(app);
        itemBinding.appStatus.setText(status);
        itemBinding.appStatus.setVisibility(active || state == AppDownloadState.FAILED
                || state == AppDownloadState.INSTALL_FAILED
                ? View.VISIBLE : View.GONE);
        itemBinding.appGridItem.setEnabled(state != AppDownloadState.INSTALLING);
        itemBinding.appGridItem.setContentDescription(getString(R.string.system_app_accessibility,
                app.getLabel(), status, getString(getAppAction(state))));
        itemBinding.appGridItem.setOnClickListener(ignored -> {
            FeedbackSound.playClick();
            SystemAppsPresenter presenter = getPresenter();
            if (presenter != null) {
                presenter.onAppClicked(app);
            }
        });
    }

    private String getAppStatus(AppEntry app) {
        switch (app.getDownloadState()) {
            case WAITING:
                return getString(R.string.system_app_waiting);
            case DOWNLOADING:
                return app.getDownloadProgress() >= 0
                        ? getString(R.string.system_app_download_progress, app.getDownloadProgress())
                        : getString(R.string.system_app_downloading);
            case PAUSED:
                return getString(R.string.system_app_paused);
            case INSTALLING:
                return getString(R.string.system_app_installing);
            case FAILED:
                return getString(R.string.system_app_failed);
            case INSTALL_FAILED:
                return getString(R.string.system_app_install_failed);
            case INSTALLED:
                return getString(R.string.system_app_action_open);
            default:
                return getString(R.string.system_app_not_installed);
        }
    }

    private int getAppAction(AppDownloadState state) {
        switch (state) {
            case WAITING:
            case DOWNLOADING:
                return R.string.system_app_action_pause;
            case PAUSED:
                return R.string.system_app_action_resume;
            case FAILED:
            case INSTALL_FAILED:
                return R.string.system_app_action_retry;
            case INSTALLED:
                return R.string.system_app_action_open;
            case INSTALLING:
                return R.string.system_app_installing;
            default:
                return R.string.system_app_action_download;
        }
    }

}
