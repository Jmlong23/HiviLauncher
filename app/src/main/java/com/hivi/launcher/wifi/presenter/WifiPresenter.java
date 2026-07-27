package com.hivi.launcher.wifi.presenter;

import com.hivi.launcher.base.BasePresenter;
import com.hivi.launcher.wifi.model.WifiModel;
import com.hivi.launcher.wifi.ui.WifiView;

public final class WifiPresenter extends BasePresenter<WifiView> {
    private final WifiModel mModel = new WifiModel();

    public WifiPresenter(WifiView view) {
        super(view);
    }
}
