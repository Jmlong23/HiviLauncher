package com.hivi.launcher.utils;

import android.content.Context;

public final class Constants {
    public static final String BASE_URL = "https://network.hivi.com/api/";//正式服
    public static final String TEST_BASE_URL = "http://8.129.106.82/api/";//测试服
    public static final String AI_WEBSOCKET_URL = "ws://network.hivi.com/ws/swan-audio-ai/v1/";//正式服
    public static final String TEST_WS_URL = "ws://8.129.106.82/ws/swan-audio-ai/v1/";//测试服
    public static final String APP_UPDATE_PRODUCT_TYPE = "HIVI-LAUNCHER";

    /**
     * Launcher 当前的账号授权接口使用测试环境；AI WebSocket 必须使用同一环境，
     * 否则测试环境签发的 Token 会被正式服以 403 拒绝。
     */
    public static String getCurrentBaseUrl(Context context) {
        return TEST_BASE_URL;
    }

    public static String getCurrentWsUrl(Context context) {
        return TEST_WS_URL;
    }

    private Constants() {
    }
}
