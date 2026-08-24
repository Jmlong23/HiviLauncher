package com.hivi.launcher.settings.model;

public final class SettingsModel {
    public static final int LANGUAGE_CHINESE = 0;
    public static final int LANGUAGE_ENGLISH = 1;

    public static final int SCREEN_SAVER_TIMEOUT_ONE_MINUTE = 0;
    public static final int SCREEN_SAVER_TIMEOUT_FIVE_MINUTES = 1;
    public static final int SCREEN_SAVER_TIMEOUT_TEN_MINUTES = 2;
    public static final int SCREEN_SAVER_TIMEOUT_THIRTY_MINUTES = 3;
    public static final int SCREEN_SAVER_TIMEOUT_NEVER = 4;
    public static final int SCREEN_SAVER_STYLE_SIMPLE = 0;
    public static final int SCREEN_SAVER_STYLE_WEATHER = 1;
    public static final int SCREEN_SAVER_STYLE_FLIP = 2;
    public static final int SCREEN_SAVER_STYLE_BLACK = 3;

    private int mSelectedSection;
    private int mLanguage = LANGUAGE_CHINESE;
    private boolean mLanguageOptionsExpanded;
    private boolean mScreenSaverEnabled;
    private int mScreenSaverTimeout = SCREEN_SAVER_TIMEOUT_ONE_MINUTE;
    private boolean mScreenSaverTimeoutOptionsExpanded;
    private int mScreenSaverStyle = SCREEN_SAVER_STYLE_SIMPLE;

    public int getSelectedSection() {
        return mSelectedSection;
    }

    public void setSelectedSection(int selectedSection) {
        mSelectedSection = selectedSection;
    }

    public int getLanguage() {
        return mLanguage;
    }

    public void setLanguage(int language) {
        mLanguage = language;
    }

    public boolean isLanguageOptionsExpanded() {
        return mLanguageOptionsExpanded;
    }

    public void setLanguageOptionsExpanded(boolean languageOptionsExpanded) {
        mLanguageOptionsExpanded = languageOptionsExpanded;
    }

    public boolean isScreenSaverEnabled() {
        return mScreenSaverEnabled;
    }

    public void setScreenSaverEnabled(boolean screenSaverEnabled) {
        mScreenSaverEnabled = screenSaverEnabled;
    }

    public int getScreenSaverTimeout() {
        return mScreenSaverTimeout;
    }

    public void setScreenSaverTimeout(int screenSaverTimeout) {
        mScreenSaverTimeout = screenSaverTimeout;
    }

    public boolean isScreenSaverTimeoutOptionsExpanded() {
        return mScreenSaverTimeoutOptionsExpanded;
    }

    public void setScreenSaverTimeoutOptionsExpanded(boolean screenSaverTimeoutOptionsExpanded) {
        mScreenSaverTimeoutOptionsExpanded = screenSaverTimeoutOptionsExpanded;
    }

    public int getScreenSaverStyle() {
        return mScreenSaverStyle;
    }

    public void setScreenSaverStyle(int screenSaverStyle) {
        mScreenSaverStyle = screenSaverStyle;
    }
}
