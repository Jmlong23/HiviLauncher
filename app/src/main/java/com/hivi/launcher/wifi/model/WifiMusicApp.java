package com.hivi.launcher.wifi.model;

import com.hivi.launcher.R;

/**
 * Music applications exposed on the Wi-Fi music page.
 *
 * <p>Package names are the Android Auto / IoT variants installed on the target device, confirmed
 * via {@code adb shell pm list packages}. Keeping the values here makes the launch integration
 * explicit and avoids coupling UI resources to application identifiers.</p>
 */
public enum WifiMusicApp {
    QQ_MUSIC(R.string.wifi_music_app_qq, "com.tencent.qqmusiccar"),
    NETEASE_CLOUD_MUSIC(R.string.wifi_music_app_wyy, "com.netease.cloudmusic.iot"),
    KUGOU_MUSIC(R.string.wifi_music_app_kugou, "com.kugou.android.auto");

    private final int mLabelResId;
    private final String mPackageName;

    WifiMusicApp(int labelResId, String packageName) {
        mLabelResId = labelResId;
        mPackageName = packageName;
    }

    public int getLabelResId() {
        return mLabelResId;
    }

    public String getPackageName() {
        return mPackageName;
    }
}
