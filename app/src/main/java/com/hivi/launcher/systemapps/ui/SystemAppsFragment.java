package com.hivi.launcher.systemapps.ui;

import android.app.Activity;
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
import com.hivi.launcher.systemapps.model.AppDownloadStatus;
import com.hivi.launcher.systemapps.presenter.SystemAppsPresenter;
import com.hivi.launcher.utils.UiUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SystemAppsFragment extends BaseFragment<SystemAppsPresenter>
        implements SystemAppsView {
    private static final int APP_GRID_COLUMN_COUNT = 5;
    private FragmentSystemAppsBinding mBinding;
    private final Map<String, ItemAppGridBinding> mAppItems = new HashMap<>();

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
        SystemAppsPresenter presenter = getPresenter();
        if (presenter != null) {
            presenter.loadApps();
        }
    }

    @Override
    public void onDestroyView() {
        mAppItems.clear();
        mBinding = null;
        super.onDestroyView();
    }

    @Override
    public void showApps(List<AppEntry> apps) {
        if (mBinding == null) {
            return;
        }
        mBinding.appsGrid.removeAllViews();
        mAppItems.clear();
        mBinding.appsGrid.setColumnCount(APP_GRID_COLUMN_COUNT);
        mBinding.appsGrid.setRowCount(
                (apps.size() + APP_GRID_COLUMN_COUNT - 1) / APP_GRID_COLUMN_COUNT);

        for (int index = 0; index < apps.size(); index++) {
            AppEntry app = apps.get(index);
            View item = createAppGridItem(app, mBinding.appsGrid);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec(index / APP_GRID_COLUMN_COUNT, GridLayout.TOP),
                    GridLayout.spec(index % APP_GRID_COLUMN_COUNT, GridLayout.FILL, 1f));
            params.width = 0;
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.topMargin = index >= APP_GRID_COLUMN_COUNT ? dp(64) : 0;
            mBinding.appsGrid.addView(item, params);
            mAppItems.put(app.getPackageName(), ItemAppGridBinding.bind(item));
            renderDownloadStatus(app, getPresenter() == null
                    ? new AppDownloadStatus(AppDownloadStatus.State.NOT_DOWNLOADED, -1)
                    : getPresenter().getDownloadStatus(app));
        }
    }

    @Override
    public void showDownloadStatus(AppEntry app, AppDownloadStatus status) {
        if (mBinding == null) {
            return;
        }
        renderDownloadStatus(app, status);
    }

    private void renderDownloadStatus(AppEntry app, AppDownloadStatus status) {
        ItemAppGridBinding itemBinding = mAppItems.get(app.getPackageName());
        if (itemBinding == null) {
            return;
        }
        AppDownloadPresentation presentation = new AppDownloadPresentation(status);
        itemBinding.appIcon.setAlpha(presentation.isDimmed() ? 0.55f : 1f);
        itemBinding.appDownloadScrim.setVisibility(
                presentation.isDimmed() ? View.VISIBLE : View.GONE);
        itemBinding.appDownload.setVisibility(
                presentation.showsDownloadIcon() ? View.VISIBLE : View.GONE);
        itemBinding.appProgress.setVisibility(
                presentation.showsProgress() ? View.VISIBLE : View.GONE);
        itemBinding.appProgress.setStatus(status);
        int labelResId = presentation.getLabelResId();
        String label = labelResId == 0 ? app.getLabel() : getString(labelResId);
        itemBinding.appLabel.setText(label);
        boolean downloading = status.getState() == AppDownloadStatus.State.DOWNLOADING;
        boolean paused = status.getState() == AppDownloadStatus.State.PAUSED;
        String description = app.getLabel() + (labelResId == 0 ? "" : ", " + label);
        if ((downloading || paused) && status.getProgress() >= 0) {
            description += ", " + status.getProgress() + "%";
        }
        if (downloading || paused) {
            description += ", " + getString(downloading
                    ? R.string.system_apps_pause_download : R.string.system_apps_resume_download);
        }
        itemBinding.appGridItem.setContentDescription(description);
        itemBinding.appGridItem.setEnabled(presentation.isEnabled());
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
        itemBinding.appGridItem.setOnClickListener(ignored -> {
            SystemAppsPresenter presenter = getPresenter();
            if (presenter != null) {
                presenter.launchApp(app);
            }
        });
        itemBinding.appGridItem.setOnLongClickListener(ignored -> {
            SystemAppsPresenter presenter = getPresenter();
            if (presenter != null) {
                presenter.onAppLongPressed(app);
            }
            return true;
        });
        itemBinding.appIcon.setImageDrawable(app.getIcon());
        itemBinding.appLabel.setText(app.getLabel());
        return itemBinding.getRoot();
    }

    @Override
    public void showAppActions(final AppEntry app) {
        Activity activity = getActivity();
        if (activity == null || activity.isFinishing()) {
            return;
        }
        // 受保护的预置应用（如 QQ音乐车机版）不提供卸载，只保留应用信息
        final boolean uninstallable = app.isUninstallable();
        CharSequence[] items = uninstallable
                ? new CharSequence[]{getString(R.string.system_apps_action_uninstall),
                        getString(R.string.system_apps_action_app_info)}
                : new CharSequence[]{getString(R.string.system_apps_action_app_info)};
        new android.app.AlertDialog.Builder(activity)
                .setTitle(app.getLabel())
                .setItems(items, (dialog, which) -> {
                    SystemAppsPresenter presenter = getPresenter();
                    if (presenter == null) {
                        return;
                    }
                    if (uninstallable && which == 0) {
                        presenter.uninstallApp(app);
                    } else {
                        presenter.openAppInfo(app);
                    }
                })
                .show();
    }

    private int dp(int value) {
        return UiUtils.dp(getActivity(), value);
    }
}
