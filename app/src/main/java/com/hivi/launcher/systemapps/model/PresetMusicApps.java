package com.hivi.launcher.systemapps.model;

import android.content.Context;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import com.hivi.launcher.R;
import com.hivi.launcher.utils.Constants;

/**
 * 应用菜单固定展示的 9 个音乐/音频应用，展示顺序即枚举声明顺序；除这些外不显示其他应用。
 *
 * <p>设备上已安装（任一候选包名命中）时显示真实图标与可启动入口；未安装时显示内置占位图标
 * 与“未安装”角标，点击后按与 launcher 自身更新一致的流程下载安装：按 APP类型 请求公司
 * Swan 后台 {@code version/details} 接口拿到最新 APK 地址（阿里云 OSS）→ 下载 → 静默安装。
 * 运营在后台“软件管理”里换包后无需改动 launcher。</p>
 *
 * <p>各类型当前发布的安装包（2026-10 上传）：QQ音乐车机版 3.13.0.3、网易云音乐车机版 6.2.81、
 * 汽水音乐 15.8.0、酷狗音乐车机版 6.1.2、酷我音乐HD 9.0.1.0、全民K歌TV版 6.1.2.1、
 * 喜马拉雅车机版 7.1.0.04、蜻蜓FM 11.1.7、懒人听书HD 3.0.3。
 * 图标为品牌风格的内置占位图，量产前可替换为官方授权图标资源。</p>
 */
public enum PresetMusicApps {
    QQ_MUSIC(R.string.preset_app_qq_music, R.drawable.ic_preset_qq_music, "QQ-MUSIC",
            "com.tencent.qqmusiccar", "com.tencent.qqmusicpad", "com.tencent.qqmusic"),
    NETEASE_CLOUD_MUSIC(R.string.preset_app_netease_music, R.drawable.ic_preset_netease_music,
            "NETEASE-MUSIC",
            "com.netease.cloudmusic.iot", "com.netease.cloudmusic"),
    SODA_MUSIC(R.string.preset_app_soda_music, R.drawable.ic_preset_soda_music, "SODA-MUSIC",
            "com.luna.music"),
    KUGOU_MUSIC(R.string.preset_app_kugou_music, R.drawable.ic_preset_kugou_music, "KUGOU-MUSIC",
            "com.kugou.android.auto", "com.kugou.android"),
    KUWO_MUSIC(R.string.preset_app_kuwo_music, R.drawable.ic_preset_kuwo_music, "KUWO-MUSIC",
            "cn.kuwo.kwauto", "cn.kuwo.kwmusichd", "cn.kuwo.player"),
    WE_SING(R.string.preset_app_we_sing, R.drawable.ic_preset_we_sing, "WE-SING",
            "com.tencent.karaoketv", "com.tencent.karaoke"),
    XIMALAYA(R.string.preset_app_ximalaya, R.drawable.ic_preset_ximalaya, "XIMALAYA",
            "com.ximalaya.ting.android.car", "com.ximalaya.ting.android"),
    QINGTING_FM(R.string.preset_app_qingting_fm, R.drawable.ic_preset_qingting_fm, "QINGTING-FM",
            "fm.qingting.qtradio"),
    LAZY_AUDIO(R.string.preset_app_lazy_audio, R.drawable.ic_preset_lazy_audio, "LAZY-AUDIO",
            "bubei.tingshu.hd", "bubei.tingshu");

    private final int mLabelResId;
    @DrawableRes
    private final int mIconResId;
    /** Swan 后台“软件管理”里的 APP类型，下载时用它查询最新安装包地址。 */
    private final String mServerType;
    private final String[] mCandidatePackages;

    PresetMusicApps(@StringRes int labelResId, @DrawableRes int iconResId, String serverType,
            String... candidatePackages) {
        mLabelResId = labelResId;
        mIconResId = iconResId;
        mServerType = serverType;
        mCandidatePackages = candidatePackages;
    }

    public int getLabelResId() {
        return mLabelResId;
    }

    public int getIconResId() {
        return mIconResId;
    }

    public String getServerType() {
        return mServerType;
    }

    public String[] getCandidatePackages() {
        return mCandidatePackages;
    }

    /** 版本查询地址，返回 JSON 中的 updateUrl 才是真正的 APK 下载地址。 */
    public String getDownloadUrl(Context context) {
        return Constants.getCurrentBaseUrl(context) + "version/details?type=" + mServerType;
    }

    /** QQ音乐车机版与系统深度联动，作为预置基础应用不允许卸载；其余应用长按可卸载。 */
    public boolean isUninstallable() {
        return this != QQ_MUSIC;
    }
}
