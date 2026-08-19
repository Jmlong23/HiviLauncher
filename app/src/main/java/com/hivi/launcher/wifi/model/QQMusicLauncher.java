package com.hivi.launcher.wifi.model;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Base64;

import com.hivi.launcher.utils.log.AppLog;

import java.nio.charset.StandardCharsets;

/**
 * 调起车机版 QQ 音乐（com.tencent.qqmusiccar）。
 *
 * <p>带关键字时走第三方 deep link 打开搜索结果页（协议自 APK 反编译确认）：
 * DispacherActivityForThird 解析 qqmusiccar:// 的 query（原样切分，不做 URL 解码），
 * action=8 进搜索分支，search_key 为 Base64(UTF-8 关键字)，m1 映射为 direct_play。
 * 注意：m1 的自动播放是启动页内 1 秒一次性尝试（网络慢即失效），且 QQ 音乐未开放
 * 第三方 AIDL（未注册包一律 code=5 No permission），故自动起播不可依赖——
 * 当前稳定效果是打开搜索结果页，由用户点第一条播放。deep link 不可用时回退普通启动。</p>
 */
public final class QQMusicLauncher {
    private static final String TAG = "QQMusicLauncher";

    private QQMusicLauncher() {
    }

    /** keyword 为空时仅打开 QQ 音乐。 */
    public static void openForSearch(Context context, String keyword) {
        Context appContext = context.getApplicationContext();
        String packageName = WifiMusicApp.QQ_MUSIC.getPackageName();
        Intent fallback = appContext.getPackageManager().getLaunchIntentForPackage(packageName);
        if (fallback == null) {
            AppLog.w(TAG, "QQ Music is not installed, skip launch");
            return;
        }

        String key = keyword == null ? "" : keyword.trim();
        if (!key.isEmpty()) {
            String encoded = Base64.encodeToString(
                    key.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
            Intent deepLink = new Intent(Intent.ACTION_VIEW, Uri.parse(
                    "qqmusiccar://qqmusic.com/?action=8&search_key=" + encoded + "&m1=true"));
            deepLink.setPackage(packageName);
            deepLink.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                appContext.startActivity(deepLink);
                AppLog.i(TAG, "QQ Music search launched, keyword=" + key);
                return;
            } catch (ActivityNotFoundException e) {
                AppLog.w(TAG, "QQ Music search deep link unsupported, fallback to plain launch");
            }
        }

        fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            appContext.startActivity(fallback);
        } catch (Exception e) {
            AppLog.e(TAG, "launch QQ Music failed", e);
        }
    }
}
