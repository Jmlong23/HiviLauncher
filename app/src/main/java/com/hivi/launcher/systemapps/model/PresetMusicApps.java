package com.hivi.launcher.systemapps.model;

import android.text.TextUtils;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.hivi.launcher.R;

/**
 * 应用菜单固定展示的 8 个音乐/音频应用，展示顺序即枚举声明顺序；除这八个外不显示其他应用。
 *
 * <p>设备上已安装（任一候选包名命中）时显示真实图标与可启动入口；未安装时显示内置占位图标
 * 与“未安装”角标，点击走“下载 APK → 静默安装 → 启动”流程。</p>
 *
 * <p>版本说明：喜马拉雅配置为官方车机版（横屏）；QQ音乐/酷狗/网易云/全民K歌出厂预装车机版
 * （qqmusiccar/kugou auto/netease iot/karaoketv），未预装机型走链接下载的是手机版或 HD 版兜底；
 * 酷我有官方车机版但唯一分发地址（fq-audio.kuwo.cn，惠威侧服务器）尚未部署；汽水/蜻蜓官方无车机版。
 * 酷我/汽水暂用应用汇镜像（无官方公开安卓直链），量产前建议把 8 个 APK 全部自托管到
 * {@link #DOWNLOAD_BASE_URL} 指向的文件服务器，把对应地址换成相对文件名即可。
 * 图标为品牌风格的内置占位图，量产前可替换为官方授权图标资源。</p>
 */
public enum PresetMusicApps {
    /**
     * 腾讯官方发布槽位（已验证包名 com.tencent.qqmusicpad，适合横屏大屏的 HD/Pad 版）。
     * 出厂预装车机版 com.tencent.qqmusiccar 时直接命中候选列表，此链接仅作未预装机型的兜底。
     */
    QQ_MUSIC(R.string.preset_app_qq_music, R.drawable.ic_preset_qq_music,
            "https://dldir1v6.qq.com/music/release/upload/t_mm_file_publish/10162781.apk",
            "com.tencent.qqmusiccar", "com.tencent.qqmusicpad", "com.tencent.qqmusic"),
    /** 网易官方接口，302 跳官方 CDN 最新版（已验证包名 com.netease.cloudmusic）。 */
    NETEASE_CLOUD_MUSIC(R.string.preset_app_netease_music, R.drawable.ic_preset_netease_music,
            "https://music.163.com/api/android/download/latest",
            "com.netease.cloudmusic.iot", "com.netease.cloudmusic"),
    /** 应用汇镜像直链（字节跳动未提供公开安卓直链，临时方案，待自托管）。 */
    SODA_MUSIC(R.string.preset_app_soda_music, R.drawable.ic_preset_soda_music,
            "http://mobile.d.appchina.com/McDonald/e/7717605/0/0/0/1793066190708/package_100213030.1793066190708",
            "com.luna.music"),
    /** 酷狗官方下载跳转，302 跳官方 CDN 最新 arm64 版（已验证包名 com.kugou.android）。 */
    KUGOU_MUSIC(R.string.preset_app_kugou_music, R.drawable.ic_preset_kugou_music,
            "https://download.kugou.com/download/kugou_android",
            "com.kugou.android.auto", "com.kugou.android"),
    /**
     * 暂用应用汇镜像（已验证包名 cn.kuwo.player）。酷我官网的车机版直链
     * https://fq-audio.kuwo.cn/car/KuwoChangTing_car.apk 指向惠威侧文件服务器但尚未部署（404），
     * 部署后建议替换为该官方车机版。
     */
    KUWO_MUSIC(R.string.preset_app_kuwo_music, R.drawable.ic_preset_kuwo_music,
            "http://mobile.d.appchina.com/McDonald/e/7710591/0/0/0/1793067085699/package_12224.1793067085699",
            "cn.kuwo.kwauto", "cn.kuwo.player"),
    /**
     * 腾讯官方 CDN 直链（已验证包名 com.tencent.karaoke 手机版）。
     * 出厂预装 TV/车机版 com.tencent.karaoketv（横屏）时直接命中候选列表。
     */
    WE_SING(R.string.preset_app_we_sing, R.drawable.ic_preset_we_sing,
            "https://d3g.qq.com/musicapp/kge/20666/7.25.39.278-Kge_1100107582.apk",
            "com.tencent.karaoketv", "com.tencent.karaoke"),
    /**
     * 喜马拉雅官方车机版分发接口：返回 JSON，其中的 apk_url 指向最新车机版 APK，
     * 由 {@link PresetAppInstaller} 在下载前解析（已验证包名 com.ximalaya.ting.android.car，
     * 车机版横屏）。官方专门为车载场景维护，链接永不因版本更新失效。
     */
    XIMALAYA(R.string.preset_app_ximalaya, R.drawable.ic_preset_ximalaya,
            "https://api.ximalaya.com/iot/openapi-smart-device-api/customized/no_gateway_validate/car/website/get-new-apk",
            "com.ximalaya.ting.android.car", "com.ximalaya.ting.android"),
    /** 蜻蜓FM 官网首页直链（已验证包名 fm.qingting.qtradio）。 */
    QINGTING_FM(R.string.preset_app_qingting_fm, R.drawable.ic_preset_qingting_fm,
            "http://apk.qtfm.cn/releases/QingTingFm_DL.apk",
            "fm.qingting.qtradio");

    /**
     * 自托管下载基地址。部署自己的文件服务器后填入（例如 {@code https://ota.example.com/apk/}），
     * 并把各条目的下载地址改为相对文件名；为空时条目使用完整 URL。
     */
    public static final String DOWNLOAD_BASE_URL = "";

    private final int mLabelResId;
    @DrawableRes
    private final int mIconResId;
    private final String mDownloadUrlOrPath;
    private final String[] mCandidatePackages;

    PresetMusicApps(@StringRes int labelResId, @DrawableRes int iconResId, String downloadUrlOrPath,
            String... candidatePackages) {
        mLabelResId = labelResId;
        mIconResId = iconResId;
        mDownloadUrlOrPath = downloadUrlOrPath;
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

    /** 完整 APK 下载地址；配置了自托管基地址时优先按相对路径拼接。 */
    @Nullable
    public String getDownloadUrl() {
        if (!TextUtils.isEmpty(DOWNLOAD_BASE_URL)) {
            return DOWNLOAD_BASE_URL + mDownloadUrlOrPath;
        }
        if (mDownloadUrlOrPath.startsWith("http")) {
            return mDownloadUrlOrPath;
        }
        return null;
    }
}
