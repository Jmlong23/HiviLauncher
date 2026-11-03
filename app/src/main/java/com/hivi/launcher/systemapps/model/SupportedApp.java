package com.hivi.launcher.systemapps.model;

import com.hivi.launcher.R;

public enum SupportedApp {
    QQ_MUSIC(R.string.system_app_qq, R.drawable.ic_app_qq_music,
            R.string.system_app_qq_download_url, "QQ-MUSIC",
            "com.tencent.qqmusiccar", "com.tencent.qqmusicpad", "com.tencent.qqmusic"),
    NETEASE_MUSIC(R.string.system_app_netease, R.drawable.ic_app_wyy,
            R.string.system_app_netease_download_url, "NETEASE-MUSIC",
            "com.netease.cloudmusic.iot", "com.netease.cloudmusic"),
    KUGOU_MUSIC(R.string.system_app_kugou, R.drawable.ic_app_kugou,
            R.string.system_app_kugou_download_url, "KUGOU-MUSIC",
            "com.kugou.android.auto", "com.kugou.android"),
    SODA_MUSIC(R.string.system_app_soda, R.drawable.ic_app_soda_music,
            R.string.system_app_soda_download_url, "SODA-MUSIC", "com.luna.music", "com.luna.music.car"),
    KARAOKE(R.string.system_app_karaoke, R.drawable.ic_app_kge,
            R.string.system_app_karaoke_download_url, "WE-SING",
            "com.tencent.karaoketv", "com.tencent.karaoke"),
    KUWO_MUSIC(R.string.system_app_kuwo, R.drawable.ic_app_kuwo_music,
            R.string.system_app_kuwo_download_url, "KUWO-MUSIC",
            "cn.kuwo.kwauto", "cn.kuwo.kwmusichd", "cn.kuwo.kwmusiccar", "cn.kuwo.player"),
    XIMALAYA(R.string.system_app_ximalaya, R.drawable.ic_app_ximalaya,
            R.string.system_app_ximalaya_download_url, "XIMALAYA",
            "com.ximalaya.ting.android.car", "com.ximalaya.ting.android"),
    QINGTING_FM(R.string.system_app_qingting, R.drawable.ic_app_qingting_fm,
            R.string.system_app_qingting_download_url, "QINGTING-FM",
            "fm.qingting.qtradio", "fm.qingting.qtradio.car"),
    LANREN_AUDIO(R.string.system_app_lanren, R.drawable.ic_app_lazy_audio,
            R.string.system_app_lanren_download_url, "LAZY-AUDIO", "bubei.tingshu.hd", "bubei.tingshu");

    private final int mLabelResId;
    private final int mIconResId;
    private final int mDownloadUrlResId;
    private final String mServerType;
    private final String[] mPackageNames;

    SupportedApp(int labelResId, int iconResId, int downloadUrlResId, String serverType,
            String... packageNames) {
        mLabelResId = labelResId;
        mIconResId = iconResId;
        mDownloadUrlResId = downloadUrlResId;
        mServerType = serverType;
        mPackageNames = packageNames;
    }

    public int getLabelResId() {
        return mLabelResId;
    }

    public int getIconResId() {
        return mIconResId;
    }

    public int getDownloadUrlResId() {
        return mDownloadUrlResId;
    }

    public String getServerType() {
        return mServerType;
    }

    public String[] getPackageNames() {
        return mPackageNames.clone();
    }

    public boolean matchesPackage(String packageName) {
        for (String candidate : mPackageNames) {
            if (candidate.equals(packageName)) {
                return true;
            }
        }
        return false;
    }
}
