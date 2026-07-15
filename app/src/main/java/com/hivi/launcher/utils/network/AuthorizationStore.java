package com.hivi.launcher.utils.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

public final class AuthorizationStore {
    private static final String PREFERENCES_NAME = "account_authorization";
    private static final String TOKEN_KEY = "authorization_token";

    private AuthorizationStore() {
    }

    public static String getToken(Context context) {
        return getPreferences(context).getString(TOKEN_KEY, "");
    }

    public static boolean hasToken(Context context) {
        return !TextUtils.isEmpty(getToken(context));
    }

    public static void saveToken(Context context, String token) {
        String authorization = token.startsWith("Bearer ") ? token : "Bearer " + token;
        getPreferences(context).edit().putString(TOKEN_KEY, authorization).apply();
    }

    public static void clearToken(Context context) {
        getPreferences(context).edit().putString(TOKEN_KEY, "").apply();
    }

    private static SharedPreferences getPreferences(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }
}
