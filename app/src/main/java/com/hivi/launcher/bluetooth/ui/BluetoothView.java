package com.hivi.launcher.bluetooth.ui;

import com.hivi.launcher.base.BaseView;
import com.hivi.launcher.bluetooth.model.BluetoothPageState;

public interface BluetoothView extends BaseView {
    void renderBluetoothPage(BluetoothPageState state);
}
