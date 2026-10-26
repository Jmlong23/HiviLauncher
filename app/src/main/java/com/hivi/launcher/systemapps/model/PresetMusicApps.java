package com.hivi.launcher.systemapps.model;

import android.text.TextUtils;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.hivi.launcher.R;

/**
 * 预置在线音乐服务目录：设备上未安装时也在“全部应用”列表中展示官方名称与内置图标，
 * 点击后走“下载 APK → 静默安装 → 启动”流程（与竞品参考视频一致），不跳转应用商店；
 * 任一候选包名已安装则不再展示占位，由系统真实应用替代。
 *
 * <p>每个服务可配置多个候选包名（手机版 / 车机版 / IoT 版），命中任意一个即视为已安装。
 * 图标为品牌风格的内置占位图，量产前可替换为官方授权图标资源。</p>
 */
public enum PresetMusicApps {
    QQ_MUSIC(R.string.preset_app_qq_music, R.drawable.ic_preset_qq_music, "qq-music.apk",
            "com.tencent.qqmusiccar", "com.tencent.qqmusic"),
    NETEASE_CLOUD_MUSIC(R.string.preset_app_netease_music, R.drawable.ic_preset_netease_music,
            "netease-cloud-music.apk", "com.netease.cloudmusic.iot", "com.netease.cloudmusic"),
    KUGOU_MUSIC(R.string.preset_app_kugou_music, R.drawable.ic_preset_kugou_music,
            "kugou-music.apk", "com.kugou.android.auto", "com.kugou.android"),
    KUWO_MUSIC(R.string.preset_app_kuwo_music, R.drawable.ic_preset_kuwo_music,
            "kuwo-music.apk", "cn.kuwo.kwauto", "cn.kuwo.player"),
    XIMALAYA(R.string.preset_app_ximalaya, R.drawable.ic_preset_ximalaya,
            "ximalaya.apk", "com.ximalaya.ting.android", "com.ximalaya.ting.car"),
    SODA_MUSIC(R.string.preset_app_soda_music, R.drawable.ic_preset_soda_music,
            "soda-music.apk", "com.luna.music"),
    SPOTIFY(R.string.preset_app_spotify, R.drawable.ic_preset_spotify,
            "spotify.apk", "com.spotify.music"),
    APPLE_MUSIC(R.string.preset_app_apple_music, R.drawable.ic_preset_apple_music,
            "apple-music.apk", "com.apple.android.music"),
    APPLE_MUSIC_CLASSICAL(R.string.preset_app_apple_classical, R.drawable.ic_preset_apple_classical,
            "apple-music-classical.apk", "com.apple.android.music.classical"),
    TIDAL(R.string.preset_app_tidal, R.drawable.ic_preset_tidal,
            "tidal.apk", "com.aspiro.tidal");

    /**
     * 预置应用 APK 下载基地址，由运维部署文件服务器后填入（例如 {@code https://ota.example.com/apk/}）；
     * 为空时点击未安装应用会提示暂未提供下载。
     */
    public static final String DOWNLOAD_BASE_URL = "";

    private final int mLabelResId;
    @DrawableRes
    private final int mIconResId;
    private final String mDownloadPath;
    private final String[] mCandidatePackages;

    PresetMusicApps(@StringRes int labelResId, @DrawableRes int iconResId, String downloadPath,
            String... candidatePackages) {
        mLabelResId = labelResId;
        mIconResId = iconResId;
        mDownloadPath = downloadPath;
        mCandidatePackages = candidatePackages;
    }

    public int getLabelResId() {
        return mLabelResId;
    }

    public int getIconResId() {
        return mIconResId;
    }

    public String[] getCandidatePackages() {
        return mCandidatePackages;
    }

    /** 完整 APK 下载地址；下载服务未部署时返回 null。 */
    @Nullable
    public String getDownloadUrl() {
        if (TextUtils.isEmpty(DOWNLOAD_BASE_URL)) {
            return null;
        }
        return DOWNLOAD_BASE_URL + mDownloadPath;
    }
}
