package com.hivi.launcher;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;

import com.hivi.launcher.utils.LocaleHelper;
import com.hivi.launcher.utils.network.NetworkManager;

public class HiviLauncherApplication extends Application {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleHelper.applyLocale(base));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        LocaleHelper.applyLocale(this);
        Context deviceProtectedContext = createDeviceProtectedStorageContext();
        NetworkManager.initialize(deviceProtectedContext);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        LocaleHelper.applyLocale(this);
    }
}
