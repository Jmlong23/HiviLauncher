package com.hivi.launcher;

import android.app.Application;
import android.content.Context;

import com.hivi.launcher.utils.network.NetworkManager;

public class HiviLauncherApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Context deviceProtectedContext = createDeviceProtectedStorageContext();
        NetworkManager.initialize(deviceProtectedContext);
    }
}
