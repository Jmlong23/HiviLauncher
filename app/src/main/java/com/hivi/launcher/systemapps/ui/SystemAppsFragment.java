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
import com.hivi.launcher.customview.RoundRectDrawable;
import com.hivi.launcher.databinding.FragmentSystemAppsBinding;
import com.hivi.launcher.databinding.ItemAppGridBinding;
import com.hivi.launcher.main.ui.MainActivity;
import com.hivi.launcher.systemapps.model.AppEntry;
import com.hivi.launcher.systemapps.presenter.SystemAppsPresenter;
import com.hivi.launcher.utils.UiUtils;

import java.util.List;

public final class SystemAppsFragment extends BaseFragment<SystemAppsPresenter>
        implements SystemAppsView {
    private FragmentSystemAppsBinding mBinding;

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
        mBinding.appsDialogRoot.setBackground(new RoundRectDrawable(0xee303030, dp(8)));
        mBinding.appsCloseButton.setBackground(new RoundRectDrawable(0x66454545, dp(20)));
        mBinding.appsCloseButton.setOnClickListener(ignored -> closePage());
        SystemAppsPresenter presenter = getPresenter();
        if (presenter != null) {
            presenter.loadApps();
        }
    }

    @Override
    public void onDestroyView() {
        mBinding = null;
        super.onDestroyView();
    }

    @Override
    public void showApps(List<AppEntry> apps) {
        if (mBinding == null) {
            return;
        }
        int contentWidth = getAppsPageWidth() - dp(52);
        int columnCount = Math.max(3, Math.min(6, contentWidth / dp(142)));
        int itemWidth = Math.max(dp(112), (contentWidth - columnCount * dp(16)) / columnCount);
        mBinding.appsGrid.setColumnCount(columnCount);
        mBinding.appsGrid.removeAllViews();

        for (final AppEntry app : apps) {
            View item = createAppGridItem(app, mBinding.appsGrid);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = itemWidth;
            params.height = dp(116);
            params.setMargins(dp(8), dp(8), dp(8), dp(8));
            mBinding.appsGrid.addView(item, params);
        }
        mBinding.appsCountText.setText(getResources().getQuantityString(
                R.plurals.system_apps_count, apps.size(), apps.size()));
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
        itemBinding.appGridItem.setBackground(new RoundRectDrawable(0x24454545, dp(8)));
        itemBinding.appGridItem.setOnClickListener(ignored -> {
            SystemAppsPresenter presenter = getPresenter();
            if (presenter != null) {
                presenter.launchApp(app);
            }
        });
        itemBinding.appIcon.setImageDrawable(app.getIcon());
        itemBinding.appLabel.setText(app.getLabel());
        return itemBinding.getRoot();
    }

    private int getAppsPageWidth() {
        return Math.min(getResources().getDisplayMetrics().widthPixels - dp(72), dp(980));
    }

    private int dp(int value) {
        return UiUtils.dp(getActivity(), value);
    }
}
