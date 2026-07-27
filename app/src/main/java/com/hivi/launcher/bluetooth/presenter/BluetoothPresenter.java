package com.hivi.launcher.bluetooth.presenter;

import com.hivi.launcher.base.BasePresenter;
import com.hivi.launcher.bluetooth.model.BluetoothModel;
import com.hivi.launcher.bluetooth.ui.BluetoothView;

public final class BluetoothPresenter extends BasePresenter<BluetoothView> {
    private final BluetoothModel mModel = new BluetoothModel();

    public BluetoothPresenter(BluetoothView view) {
        super(view);
    }
}
