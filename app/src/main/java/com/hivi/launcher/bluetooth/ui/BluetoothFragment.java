package com.hivi.launcher.bluetooth.ui;

import com.hivi.launcher.R;
import com.hivi.launcher.bluetooth.presenter.BluetoothPresenter;
import com.hivi.launcher.base.BaseFragment;

public final class BluetoothFragment extends BaseFragment<BluetoothPresenter>
        implements BluetoothView {
    @Override
    protected BluetoothPresenter createPresenter() {
        return new BluetoothPresenter(this);
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.fragment_bluetooth;
    }

    @Override
    protected int getPageTitleResId() {
        return R.string.input_mode_bluetooth;
    }
}
