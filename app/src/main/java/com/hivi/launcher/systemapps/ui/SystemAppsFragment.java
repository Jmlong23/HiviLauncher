package com.hivi.launcher.systemapps.ui;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.LinearLayout;

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
    private float mLayoutScale = 1f;
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
        view.addOnLayoutChangeListener((root, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left > 0 && right - left != oldRight - oldLeft) {
                applyPageLayout(right - left);
            }
        });
        SystemAppsPresenter presenter = getPresenter();
        if (presenter != null) {
            presenter.loadApps();
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
        if (mBinding != null && mBinding.getRoot().getWidth() > 0) {
            applyPageLayout(mBinding.getRoot().getWidth());
        }
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
        setSize(itemBinding.appIconFrame, scaled(136), scaled(136));
        setSize(itemBinding.appIconContent, scaled(84), scaled(84));
        setSize(itemBinding.appLabelRow, ViewGroup.LayoutParams.MATCH_PARENT, scaled(42));
        LinearLayout.LayoutParams labelParams =
                (LinearLayout.LayoutParams) itemBinding.appLabelRow.getLayoutParams();
        labelParams.topMargin = scaled(18);
        itemBinding.appLabelRow.setLayoutParams(labelParams);
        itemBinding.appLabelRow.setPadding(scaled(4), 0, scaled(4), 0);
        itemBinding.appLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX, scaled(30));
        setSize(itemBinding.appDownloadIcon, scaled(48), scaled(48));
        setSize(itemBinding.appStatus, ViewGroup.LayoutParams.MATCH_PARENT, scaled(30));
        itemBinding.appStatus.setTextSize(TypedValue.COMPLEX_UNIT_PX, scaled(20));
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
                || state == AppDownloadState.FAILED;
        itemBinding.appIcon.setImageDrawable(app.getIcon());
        itemBinding.appIcon.setAlpha(showDownloadIcon ? 0.35f : 1f);
        itemBinding.appLabel.setText(app.getLabel());
        itemBinding.appLabel.setMaxWidth(scaled(212));
        itemBinding.appDownloadIcon.setVisibility(showDownloadIcon ? View.VISIBLE : View.GONE);
        itemBinding.appDownloadProgress.setVisibility(active ? View.VISIBLE : View.GONE);
        itemBinding.appDownloadProgress.setDownloadState(state, app.getDownloadProgress());
        String status = getAppStatus(app);
        itemBinding.appStatus.setText(status);
        itemBinding.appStatus.setVisibility(active || state == AppDownloadState.FAILED
                ? View.VISIBLE : View.GONE);
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
                return R.string.system_app_action_retry;
            case INSTALLED:
                return R.string.system_app_action_open;
            case INSTALLING:
                return R.string.system_app_installing;
            default:
                return R.string.system_app_action_download;
        }
    }

    private void applyPageLayout(int width) {
        if (mBinding == null) {
            return;
        }
        mLayoutScale = width / 1280f;
        mBinding.appsDialogRoot.setPadding(scaled(54), scaled(30), scaled(54), 0);
        setSize(mBinding.appsDialogHeader, ViewGroup.LayoutParams.MATCH_PARENT, scaled(66));
        setSize(mBinding.appsBackButton, scaled(156), ViewGroup.LayoutParams.MATCH_PARENT);
        setSize(mBinding.appsBackIcon, scaled(30), scaled(30));
        setSize(mBinding.appsVolumeButton, scaled(132), ViewGroup.LayoutParams.MATCH_PARENT);
        setSize(mBinding.appsVolumeIcon, scaled(38), scaled(38));
        setStartMargin(mBinding.appsVolumeButton, scaled(28));
        setStartMargin(mBinding.appsBackText, scaled(14));
        setStartMargin(mBinding.appsVolumeText, scaled(14));
        mBinding.appsBackText.setTextSize(TypedValue.COMPLEX_UNIT_PX, scaled(26));
        mBinding.appsVolumeText.setTextSize(TypedValue.COMPLEX_UNIT_PX, scaled(26));
        LinearLayout.LayoutParams scrollParams =
                (LinearLayout.LayoutParams) mBinding.appsScrollView.getLayoutParams();
        scrollParams.topMargin = scaled(56);
        mBinding.appsScrollView.setLayoutParams(scrollParams);
        mBinding.appsScrollView.setPadding(scaled(16), 0, scaled(16), 0);
        int contentWidth = width - scaled(54) * 2 - scaled(16) * 2;
        mAppBindings.clear();
        mBinding.appsGrid.removeAllViews();
        for (int index = 0; index < mApps.size(); index++) {
            View item = createAppGridItem(mApps.get(index), mBinding.appsGrid);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec(index / COLUMN_COUNT),
                    GridLayout.spec(index % COLUMN_COUNT));
            params.width = contentWidth / COLUMN_COUNT;
            params.height = scaled(276);
            mBinding.appsGrid.addView(item, params);
        }
    }

    private void setSize(View view, int width, int height) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        params.width = width;
        params.height = height;
        view.setLayoutParams(params);
    }

    private void setStartMargin(View view, int margin) {
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) view.getLayoutParams();
        params.leftMargin = margin;
        view.setLayoutParams(params);
    }

    private int scaled(int value) {
        return Math.round(value * mLayoutScale);
    }
}
