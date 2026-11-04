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
    private static final String ENVIRONMENT_VERSION_KEY = "environment_version";
    private static final int ENVIRONMENT_VERSION = 2;

    public static String getCurrentBaseUrl(Context context) {
        return isTestEnvironment(context) ? TEST_BASE_URL : BASE_URL;
    }

    public static String getCurrentWsUrl(Context context) {
        return isTestEnvironment(context) ? TEST_WS_URL : AI_WEBSOCKET_URL;
    }

    public static boolean isTestEnvironment(Context context) {
        return getServerPreferences(context).getBoolean(TEST_ENVIRONMENT_KEY, false);
    }

    /**
     * Applies the production-server default once to installations that still carry the old
     * test-server default (or no stored value at all).
     *
     * @return true when the stored environment was previously the test server
     */
    public static boolean migrateOldTestEnvironmentDefault(Context context) {
        SharedPreferences preferences = getServerPreferences(context);
        if (preferences.getInt(ENVIRONMENT_VERSION_KEY, 1) >= ENVIRONMENT_VERSION) {
            return false;
        }
        boolean wasTestEnvironment = preferences.getBoolean(TEST_ENVIRONMENT_KEY, true);
        preferences.edit()
                .putBoolean(TEST_ENVIRONMENT_KEY, false)
                .putInt(ENVIRONMENT_VERSION_KEY, ENVIRONMENT_VERSION)
                .commit();
        return wasTestEnvironment;
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
