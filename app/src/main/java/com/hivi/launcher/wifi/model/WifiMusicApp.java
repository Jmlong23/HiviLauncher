package com.hivi.launcher.wifi.model;

import com.hivi.launcher.R;

/**
 * Music applications exposed on the Wi-Fi music page.
 *
 * <p>Set each package name when the corresponding third-party application package is confirmed.
 * Keeping the values here makes the launch integration explicit and avoids coupling UI resources
 * to application identifiers.</p>
 */
public enum WifiMusicApp {
    QQ_MUSIC(R.string.wifi_music_app_qq, ""),
    NETEASE_CLOUD_MUSIC(R.string.wifi_music_app_wyy, ""),
    KUGOU_MUSIC(R.string.wifi_music_app_kugou, "");

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
