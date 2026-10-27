package com.hivi.launcher.utils;

import android.content.Context;
import android.content.SharedPreferences;

public final class Constants {
    public static final String BASE_URL = "https://network.hivi.com/api/";//正式服
    public static final String TEST_BASE_URL = "http://8.129.106.82/api/";//测试服
    public static final String AI_WEBSOCKET_URL = "ws://network.hivi.com/ws/swan-audio-ai/v1/";//正式服
    public static final String TEST_WS_URL = "ws://8.129.106.82/ws/swan-audio-ai/v1/";//测试服
    public static final String APP_UPDATE_PRODUCT_TYPE = "HIVI-LAUNCHER";
    private static final String SERVER_PREFERENCES = "server_environment";
    private static final String TEST_ENVIRONMENT_KEY = "test_environment";

    public static String getCurrentBaseUrl(Context context) {
        return isTestEnvironment(context) ? TEST_BASE_URL : BASE_URL;
    }

    public static String getCurrentWsUrl(Context context) {
        return isTestEnvironment(context) ? TEST_WS_URL : AI_WEBSOCKET_URL;
    }

    public static boolean isTestEnvironment(Context context) {
        return getServerPreferences(context).getBoolean(TEST_ENVIRONMENT_KEY, true);
    }

    public static boolean setTestEnvironment(Context context, boolean testEnvironment) {
        return getServerPreferences(context).edit()
                .putBoolean(TEST_ENVIRONMENT_KEY, testEnvironment)
                .commit();
    }

    private static SharedPreferences getServerPreferences(Context context) {
        return context.getApplicationContext().createDeviceProtectedStorageContext()
                .getSharedPreferences(SERVER_PREFERENCES, Context.MODE_PRIVATE);
    }

    private Constants() {
    }
}
