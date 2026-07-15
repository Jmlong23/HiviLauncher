package com.hivi.launcher;

import android.app.Application;

import com.hivi.launcher.utils.network.NetworkManager;

public class HiviLauncherApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        NetworkManager.initialize(this);
    }
}
