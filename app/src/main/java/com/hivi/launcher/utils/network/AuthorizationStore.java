package com.hivi.launcher.utils.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.UserManager;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.hivi.launcher.account.model.AuthorizedUserInfo;

public final class AuthorizationStore {
    private static final String PREFERENCES_NAME = "account_authorization";
    private static final String TOKEN_KEY = "authorization_token";
    private static final String ACCOUNT_NAME_KEY = "authorized_account_name";
    private static final String AUTHORIZED_AT_KEY = "authorized_at";
    private static final String USER_ID_KEY = "authorized_user_id";
    private static final String AVATAR_URL_KEY = "authorized_avatar_url";
    private static final String AREA_KEY = "authorized_area";
    private static final String PHONE_KEY = "authorized_phone";
    private static final String PREFERRED_KEY = "authorized_preferred";
    private static final String CREATE_TIME_KEY = "authorized_create_time";
    private static final String DELETED_KEY = "authorized_is_deleted";

    private AuthorizationStore() {
    }

    public static String getToken(Context context) {
        SharedPreferences preferences = getPreferencesIfUserUnlocked(context);
        return preferences == null ? "" : preferences.getString(TOKEN_KEY, "");
    }

    public static boolean hasToken(Context context) {
        return !TextUtils.isEmpty(getToken(context));
    }

    public static void saveToken(Context context, String token) {
        saveAuthorization(context, token, "");
    }

    public static void saveAuthorization(Context context, String token, String accountName) {
        SharedPreferences preferences = getPreferencesIfUserUnlocked(context);
        if (preferences == null) {
            return;
        }
        String authorization = token.startsWith("Bearer ") ? token : "Bearer " + token;
        SharedPreferences.Editor editor = preferences.edit()
                .putString(TOKEN_KEY, authorization)
                .putLong(AUTHORIZED_AT_KEY, System.currentTimeMillis())
                .remove(ACCOUNT_NAME_KEY);
        clearUserInfo(editor);
        if (TextUtils.isEmpty(accountName)) {
            editor.apply();
            return;
        }
        editor.putString(ACCOUNT_NAME_KEY, accountName);
        editor.apply();
    }

    public static String getAccountName(Context context) {
        SharedPreferences preferences = getPreferencesIfUserUnlocked(context);
        return preferences == null ? "" : preferences.getString(ACCOUNT_NAME_KEY, "");
    }

    public static void saveUserInfo(Context context, AuthorizedUserInfo userInfo) {
        if (userInfo == null) {
            return;
        }
        SharedPreferences preferences = getPreferencesIfUserUnlocked(context);
        if (preferences == null) {
            return;
        }
        SharedPreferences.Editor editor = preferences.edit();
        putStringOrRemove(editor, USER_ID_KEY, userInfo.getId());
        putStringOrRemove(editor, AVATAR_URL_KEY, userInfo.getAvatarUrl());
        putStringOrRemove(editor, AREA_KEY, userInfo.getArea());
        putStringOrRemove(editor, PHONE_KEY, userInfo.getPhone());
        putStringOrRemove(editor, PREFERRED_KEY, userInfo.getPreferred());
        putStringOrRemove(editor, CREATE_TIME_KEY, userInfo.getCreateTime());
        editor.putInt(DELETED_KEY, userInfo.getDeleted());
        if (!TextUtils.isEmpty(userInfo.getName())) {
            editor.putString(ACCOUNT_NAME_KEY, userInfo.getName());
        }
        editor.apply();
    }

    public static AuthorizedUserInfo getUserInfo(Context context) {
        SharedPreferences preferences = getPreferencesIfUserUnlocked(context);
        if (preferences == null) {
            return null;
        }
        String id = preferences.getString(USER_ID_KEY, "");
        String name = preferences.getString(ACCOUNT_NAME_KEY, "");
        String avatarUrl = preferences.getString(AVATAR_URL_KEY, "");
        String area = preferences.getString(AREA_KEY, "");
        String phone = preferences.getString(PHONE_KEY, "");
        String preferred = preferences.getString(PREFERRED_KEY, "");
        String createTime = preferences.getString(CREATE_TIME_KEY, "");
        if (TextUtils.isEmpty(id) && TextUtils.isEmpty(name) && TextUtils.isEmpty(avatarUrl)
                && TextUtils.isEmpty(phone)) {
            return null;
        }
        return new AuthorizedUserInfo(id, name, avatarUrl, area, phone, preferred, createTime,
                preferences.getInt(DELETED_KEY, 0));
    }

    public static long getAuthorizedAt(Context context) {
        SharedPreferences preferences = getPreferencesIfUserUnlocked(context);
        return preferences == null ? 0L : preferences.getLong(AUTHORIZED_AT_KEY, 0L);
    }

    public static void clearToken(Context context) {
        SharedPreferences preferences = getPreferencesIfUserUnlocked(context);
        if (preferences == null) {
            return;
        }
        SharedPreferences.Editor editor = preferences.edit()
                .putString(TOKEN_KEY, "")
                .remove(ACCOUNT_NAME_KEY)
                .remove(AUTHORIZED_AT_KEY);
        clearUserInfo(editor);
        editor.apply();
    }

    /**
     * Clears all account information persisted by the Launcher for a factory reset.
     */
    public static boolean clear(Context context) {
        SharedPreferences preferences = getPreferencesIfUserUnlocked(context);
        return preferences == null || preferences.edit().clear().commit();
    }

    @Nullable
    private static SharedPreferences getPreferencesIfUserUnlocked(Context context) {
        Context appContext = context.getApplicationContext();
        UserManager userManager = (UserManager) appContext.getSystemService(Context.USER_SERVICE);
        if (userManager != null && !userManager.isUserUnlocked()) {
            return null;
        }
        return appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    private static void putStringOrRemove(SharedPreferences.Editor editor, String key, String value) {
        if (TextUtils.isEmpty(value)) {
            editor.remove(key);
        } else {
            editor.putString(key, value);
        }
    }

    private static void clearUserInfo(SharedPreferences.Editor editor) {
        editor.remove(USER_ID_KEY)
                .remove(AVATAR_URL_KEY)
                .remove(AREA_KEY)
                .remove(PHONE_KEY)
                .remove(PREFERRED_KEY)
                .remove(CREATE_TIME_KEY)
                .remove(DELETED_KEY);
    }
}
