package com.hivi.launcher.utils;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.LocaleList;
import com.hivi.launcher.utils.log.AppLog;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Persists and applies the Launcher language selection.
 */
public final class LocaleHelper {
    private static final String TAG = "LocaleHelper";
    private static final String PREFERENCES_NAME = "locale_pref";
    private static final String LANGUAGE_KEY = "selected_language";

    public static final String LANGUAGE_ZH = "zh";
    public static final String LANGUAGE_EN = "en";

    private LocaleHelper() {
    }

    public static String getLanguage(Context context) {
        String language = getPreferences(context).getString(LANGUAGE_KEY, LANGUAGE_ZH);
        return LANGUAGE_EN.equals(language) ? LANGUAGE_EN : LANGUAGE_ZH;
    }

    public static void setLanguage(Context context, String language) {
        getPreferences(context).edit()
                .putString(LANGUAGE_KEY,
                        LANGUAGE_EN.equals(language) ? LANGUAGE_EN : LANGUAGE_ZH)
                .commit();
    }

    /**
     * Wraps a Context with the persisted locale. Call this from attachBaseContext.
     */
    public static Context applyLocale(Context context) {
        Locale locale = getLocale(getLanguage(context));
        Locale.setDefault(locale);
        return updateResources(context, locale);
    }

    /**
     * Updates application-level resources after the selected language changes.
     */
    public static void applyLocale(Application application) {
        Locale locale = getLocale(getLanguage(application));
        Locale.setDefault(locale);
        Configuration configuration = new Configuration(
                application.getResources().getConfiguration());
        configuration.setLocales(new LocaleList(locale));
        application.getResources().updateConfiguration(configuration,
                application.getResources().getDisplayMetrics());
    }

    /**
     * Mirrors the language selection to Android system settings when this system-signed build has
     * CHANGE_CONFIGURATION permission. The app locale remains effective even if this call fails.
     */
    public static boolean applySystemLocale(Context context, String language) {
        if (context.checkSelfPermission(android.Manifest.permission.CHANGE_CONFIGURATION)
                != PackageManager.PERMISSION_GRANTED) {
            AppLog.w(TAG, "CHANGE_CONFIGURATION permission denied");
            return false;
        }

        Locale locale = getLocale(language);
        Locale.setDefault(locale);
        if (updateSystemLocaleByActivityManager(locale)) {
            return true;
        }
        return updateSystemLocaleBySystemProperties(locale);
    }

    private static SharedPreferences getPreferences(Context context) {
        Context applicationContext = context.getApplicationContext();
        Context preferencesContext = applicationContext == null ? context : applicationContext;
        return preferencesContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    private static Locale getLocale(String language) {
        return LANGUAGE_EN.equals(language) ? Locale.US : Locale.SIMPLIFIED_CHINESE;
    }

    private static Context updateResources(Context context, Locale locale) {
        Resources resources = context.getResources();
        Configuration configuration = new Configuration(resources.getConfiguration());
        configuration.setLocales(new LocaleList(locale));
        resources.updateConfiguration(configuration, resources.getDisplayMetrics());
        return context.createConfigurationContext(configuration);
    }

    private static boolean updateSystemLocaleByActivityManager(Locale locale) {
        try {
            Class<?> activityManagerClass = Class.forName("android.app.ActivityManager");
            Method getService = activityManagerClass.getDeclaredMethod("getService");
            Object activityManager = getService.invoke(null);

            Method getConfiguration = activityManager.getClass().getMethod("getConfiguration");
            Configuration configuration = (Configuration) getConfiguration.invoke(activityManager);
            configuration.setLocales(new LocaleList(locale));
            setUserSetLocale(configuration);

            Method updatePersistentConfiguration = activityManager.getClass().getMethod(
                    "updatePersistentConfiguration", Configuration.class);
            updatePersistentConfiguration.invoke(activityManager, configuration);
            return true;
        } catch (Throwable firstError) {
            try {
                Class<?> activityManagerNativeClass =
                        Class.forName("android.app.ActivityManagerNative");
                Method getDefault = activityManagerNativeClass.getDeclaredMethod("getDefault");
                Object activityManager = getDefault.invoke(null);

                Method getConfiguration = activityManager.getClass().getMethod("getConfiguration");
                Configuration configuration = (Configuration) getConfiguration.invoke(activityManager);
                configuration.setLocales(new LocaleList(locale));
                setUserSetLocale(configuration);

                Method updatePersistentConfiguration = activityManager.getClass().getMethod(
                        "updatePersistentConfiguration", Configuration.class);
                updatePersistentConfiguration.invoke(activityManager, configuration);
                return true;
            } catch (Throwable secondError) {
                AppLog.w(TAG, "Unable to update system locale through ActivityManager", secondError);
                return false;
            }
        }
    }

    private static boolean updateSystemLocaleBySystemProperties(Locale locale) {
        String languageTag = locale.toLanguageTag();
        boolean localeUpdated = setSystemProperty("persist.sys.locale", languageTag);
        boolean languageUpdated = setSystemProperty("persist.sys.language", locale.getLanguage());
        boolean countryUpdated = setSystemProperty("persist.sys.country", locale.getCountry());
        boolean updated = localeUpdated || (languageUpdated && countryUpdated);
        if (!updated) {
            AppLog.w(TAG, "Unable to update system locale through system properties");
        }
        return updated;
    }

    private static boolean setSystemProperty(String key, String value) {
        try {
            Class<?> systemPropertiesClass = Class.forName("android.os.SystemProperties");
            Method set = systemPropertiesClass.getDeclaredMethod("set", String.class, String.class);
            set.invoke(null, key, value);
            return true;
        } catch (Throwable error) {
            AppLog.w(TAG, "Unable to update system property: " + key, error);
            return false;
        }
    }

    private static void setUserSetLocale(Configuration configuration) {
        try {
            Field userSetLocale = Configuration.class.getField("userSetLocale");
            userSetLocale.setBoolean(configuration, true);
        } catch (Throwable ignored) {
            // The field is not present on all Android versions.
        }
    }
}
