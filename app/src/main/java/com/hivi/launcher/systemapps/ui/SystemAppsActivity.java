package com.hivi.launcher.systemapps.ui;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.GridLayout;

import androidx.annotation.Nullable;

import com.hivi.launcher.R;
import com.hivi.launcher.base.BaseActivity;
import com.hivi.launcher.customview.RoundRectDrawable;
import com.hivi.launcher.databinding.ActivitySystemAppsBinding;
import com.hivi.launcher.databinding.ItemAppGridBinding;
import com.hivi.launcher.systemapps.model.AppEntry;
import com.hivi.launcher.systemapps.presenter.SystemAppsPresenter;
import com.hivi.launcher.utils.UiUtils;

import java.util.List;

public class SystemAppsActivity extends BaseActivity<ActivitySystemAppsBinding, SystemAppsPresenter>
        implements SystemAppsView {

    @Override
    protected ActivitySystemAppsBinding createBinding() {
        return ActivitySystemAppsBinding.inflate(getLayoutInflater());
    }

    @Override
    protected SystemAppsPresenter createPresenter() {
        return new SystemAppsPresenter(this, this);
    }

    @Override
    protected void initView(@Nullable Bundle savedInstanceState) {
        getWindow().setLayout(getAppsPageWidth(), WindowManager.LayoutParams.MATCH_PARENT);
        binding.appsDialogRoot.setBackground(new RoundRectDrawable(0xee303030, dp(8)));
        binding.appsCloseButton.setBackground(new RoundRectDrawable(0x66454545, dp(20)));
        binding.appsCloseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    @Override
    protected void initData() {
        presenter.loadApps();
    }

    @Override
    public void showApps(List<AppEntry> apps) {
        int contentWidth = getAppsPageWidth() - dp(52);
        int columnCount = Math.max(3, Math.min(6, contentWidth / dp(142)));
        int itemWidth = Math.max(dp(112), (contentWidth - columnCount * dp(16)) / columnCount);
        binding.appsGrid.setColumnCount(columnCount);
        binding.appsGrid.removeAllViews();

        for (final AppEntry app : apps) {
            View item = createAppGridItem(app, binding.appsGrid);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = itemWidth;
            params.height = dp(116);
            params.setMargins(dp(8), dp(8), dp(8), dp(8));
            binding.appsGrid.addView(item, params);
        }
        binding.appsCountText.setText(getResources().getQuantityString(
                R.plurals.system_apps_count, apps.size(), apps.size()));
    }

    @Override
    public void closePage() {
        finish();
    }

    private View createAppGridItem(final AppEntry app, ViewGroup parent) {
        ItemAppGridBinding itemBinding = ItemAppGridBinding.inflate(getLayoutInflater(), parent, false);
        itemBinding.appGridItem.setBackground(new RoundRectDrawable(0x24454545, dp(8)));
        itemBinding.appGridItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
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
        return UiUtils.dp(this, value);
    }
}
