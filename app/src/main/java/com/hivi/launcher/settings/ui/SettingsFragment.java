package com.hivi.launcher.settings.ui;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.PopupWindow;
import android.widget.SeekBar;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.hivi.launcher.R;
import com.hivi.launcher.base.BaseFragment;
import com.hivi.launcher.databinding.DialogWifiPasswordBinding;
import com.hivi.launcher.databinding.LayoutSettingsContentBinding;
import com.hivi.launcher.databinding.PopupSettingsLanguageBinding;
import com.hivi.launcher.databinding.PopupSettingsScreenSaverBinding;
import com.hivi.launcher.main.ui.MainActivity;
import com.hivi.launcher.settings.model.SettingsModel;
import com.hivi.launcher.settings.presenter.SettingsPresenter;
import com.hivi.launcher.utils.LocaleHelper;
import com.hivi.launcher.wifi.model.WifiNetwork;
import com.hivi.launcher.wifi.presenter.WifiPresenter;
import com.hivi.launcher.wifi.ui.WifiNetworkAdapter;
import com.hivi.launcher.wifi.ui.WifiView;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SettingsFragment extends BaseFragment<SettingsPresenter>
        implements SettingsView, WifiView {
    private static final String TAG = "SettingsFragment";
    private static final int DISPLAY_POPUP_NONE = 0;
    private static final int DISPLAY_POPUP_LANGUAGE = 1;
    private static final int DISPLAY_POPUP_SCREEN_SAVER_TIMEOUT = 2;
    private static final int LANGUAGE_POPUP_WIDTH_DP = 253;
    private static final int LANGUAGE_POPUP_HEIGHT_DP = 86;
    private static final int SCREEN_SAVER_POPUP_WIDTH_DP = 253;
    private static final int SCREEN_SAVER_POPUP_HEIGHT_DP = 209;
    private static final int SCREEN_BRIGHTNESS_MIN = 0;
    private static final int SCREEN_BRIGHTNESS_MAX = 255;
    private static final int DEFAULT_SCREEN_BRIGHTNESS = 128;
    private static final int NO_PENDING_SCREEN_BRIGHTNESS = -1;
    private static final long BRIGHTNESS_SETTINGS_WRITE_THROTTLE_MS = 120L;

    private LayoutSettingsContentBinding mBinding;
    private View[] mSectionTabs;
    private View[] mSectionPanels;
    private WifiPresenter mWifiPresenter;
    private WifiNetworkAdapter mWifiNetworkAdapter;
    private ObjectAnimator mWifiRefreshAnimator;
    private Dialog mWifiPasswordDialog;
    private PopupWindow mDisplayOptionsPopupWindow;
    private ExecutorService mLanguageSwitchExecutor;
    private final Handler mBrightnessSettingsHandler = new Handler(Looper.getMainLooper());
    private final Runnable mPendingBrightnessSettingsWriteRunnable =
            this::flushPendingScreenBrightnessWrite;
    private int mDisplayPopupType = DISPLAY_POPUP_NONE;
    private int mPendingScreenBrightness = NO_PENDING_SCREEN_BRIGHTNESS;
    private boolean mWifiRefreshing;
    private boolean mLanguageSwitchInProgress;

    @Override
    protected SettingsPresenter createPresenter() {
        return new SettingsPresenter(this, getSavedLanguage());
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.layout_settings_content;
    }

    @Override
    protected int getPageTitleResId() {
        return R.string.settings_title;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mBinding = LayoutSettingsContentBinding.bind(view);
        mLanguageSwitchExecutor = Executors.newSingleThreadExecutor();
        mSectionTabs = new View[] {
                mBinding.settingsTabNetwork,
                mBinding.settingsTabDisplay,
                mBinding.settingsTabSystem,
                mBinding.settingsTabAbout,
                mBinding.settingsTabMaintenance
        };
        mSectionPanels = new View[] {
                mBinding.settingsPanelNetwork,
                mBinding.settingsPanelDisplay,
                mBinding.settingsPanelSystem,
                mBinding.settingsPanelAbout,
                mBinding.settingsPanelMaintenance
        };

        SettingsPresenter presenter = getPresenter();
        if (presenter == null) {
            return;
        }
        for (int i = 0; i < mSectionTabs.length; i++) {
            final int section = i;
            mSectionTabs[i].setOnClickListener(v -> presenter.onSectionSelected(section));
        }
        setupWifiSettings();
        setupDisplaySettings();
        presenter.init();
    }

    @Override
    public void onDestroyView() {
        mBrightnessSettingsHandler.removeCallbacks(mPendingBrightnessSettingsWriteRunnable);
        flushPendingScreenBrightnessWrite();
        dismissDisplayOptionsPopup();
        dismissWifiPasswordDialog();
        setLanguageSwitchLoading(false);
        if (mLanguageSwitchExecutor != null) {
            mLanguageSwitchExecutor.shutdownNow();
            mLanguageSwitchExecutor = null;
        }
        mLanguageSwitchInProgress = false;
        if (mWifiRefreshAnimator != null) {
            mWifiRefreshAnimator.cancel();
            mWifiRefreshAnimator = null;
        }
        if (mWifiPresenter != null) {
            mWifiPresenter.destroy();
            mWifiPresenter = null;
        }
        mWifiNetworkAdapter = null;
        mSectionTabs = null;
        mSectionPanels = null;
        mBinding = null;
        super.onDestroyView();
    }

    @Override
    public void renderSettingsSection(int section) {
        if (mSectionTabs == null || mSectionPanels == null) {
            return;
        }
        for (int i = 0; i < mSectionTabs.length; i++) {
            boolean selected = i == section;
            mSectionTabs[i].setSelected(selected);
            mSectionPanels[i].setVisibility(selected ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void renderWifiNetworks(List<WifiNetwork> networks, String connectedSsid) {
        if (mBinding == null || mWifiNetworkAdapter == null) {
            return;
        }
        mWifiNetworkAdapter.submitNetworks(networks);
        boolean hasNetworks = networks != null && !networks.isEmpty();
        mBinding.settingsWifiList.setVisibility(hasNetworks ? View.VISIBLE : View.GONE);
        if (hasNetworks) {
            mBinding.settingsWifiEmptyState.setVisibility(View.GONE);
        }
        if (TextUtils.isEmpty(connectedSsid)) {
            mBinding.settingsNetworkSummary.setText(R.string.main_disconnected);
        } else {
            mBinding.settingsNetworkSummary.setText(connectedSsid);
        }
        updateTopWifiStatus(connectedSsid);
    }

    @Override
    public void setWifiRefreshing(boolean refreshing) {
        if (mBinding == null) {
            return;
        }
        mWifiRefreshing = refreshing;
        mBinding.settingsWifiRefresh.setEnabled(!refreshing);
        if (refreshing) {
            if (mWifiRefreshAnimator == null) {
                mWifiRefreshAnimator = ObjectAnimator.ofFloat(mBinding.settingsWifiRefresh,
                        View.ROTATION, 0f, 360f);
                mWifiRefreshAnimator.setDuration(900L);
                mWifiRefreshAnimator.setRepeatCount(ObjectAnimator.INFINITE);
            }
            mWifiRefreshAnimator.start();
        } else {
            if (mWifiRefreshAnimator != null) {
                mWifiRefreshAnimator.cancel();
            }
            mBinding.settingsWifiRefresh.setRotation(0f);
            mBinding.settingsWifiLoading.setVisibility(View.GONE);
        }
    }

    @Override
    public void showWifiEmptyState(String message) {
        if (mBinding == null) {
            return;
        }
        mBinding.settingsWifiList.setVisibility(View.GONE);
        mBinding.settingsWifiEmptyText.setText(message);
        mBinding.settingsWifiEmptyState.setVisibility(mWifiRefreshing ? View.GONE : View.VISIBLE);
        mBinding.settingsWifiLoading.setVisibility(mWifiRefreshing ? View.VISIBLE : View.GONE);
        mBinding.settingsNetworkSummary.setText(R.string.main_disconnected);
    }

    @Override
    public void showWifiPasswordDialog(WifiNetwork network) {
        if (!isAdded() || network == null) {
            return;
        }
        dismissWifiPasswordDialog();
        Dialog dialog = new Dialog(getHostActivity());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        DialogWifiPasswordBinding dialogBinding = DialogWifiPasswordBinding.inflate(
                getLayoutInflater());
        dialog.setContentView(dialogBinding.getRoot());
        dialog.setCanceledOnTouchOutside(true);
        dialogBinding.wifiPasswordTitle.setText(network.getSsid());
        dialogBinding.wifiPasswordInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        dialogBinding.wifiPasswordShow.setOnCheckedChangeListener((buttonView, isChecked) -> {
            int selection = dialogBinding.wifiPasswordInput.getSelectionStart();
            dialogBinding.wifiPasswordInput.setInputType(isChecked
                    ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            dialogBinding.wifiPasswordInput.setSelection(Math.max(0, selection));
        });
        dialogBinding.wifiPasswordCancel.setOnClickListener(view -> dialog.dismiss());
        dialogBinding.wifiPasswordConnect.setOnClickListener(view -> {
            String password = dialogBinding.wifiPasswordInput.getText() == null ? ""
                    : dialogBinding.wifiPasswordInput.getText().toString();
            if (TextUtils.isEmpty(password.trim())) {
                showToast(getString(R.string.settings_wifi_password_required));
                return;
            }
            if (mWifiPresenter != null) {
                mWifiPresenter.connectWithPassword(network, password);
            }
            dialog.dismiss();
        });
        dialog.setOnDismissListener(ignored -> {
            if (mWifiPasswordDialog == dialog) {
                mWifiPasswordDialog = null;
            }
        });
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.dimAmount = 0.68f;
            window.setAttributes(attributes);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        mWifiPasswordDialog = dialog;
        dialog.show();
        if (dialog.getWindow() != null) {
            Window dialogWindow = dialog.getWindow();
            dialogWindow.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            dialogWindow.setLayout(dp(690), WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    @Override
    public void renderDisplaySettings(int language, boolean languageOptionsExpanded,
            boolean screenSaverEnabled, int screenSaverTimeout,
            boolean screenSaverTimeoutOptionsExpanded) {
        if (mBinding == null) {
            return;
        }
        int languageText = language == SettingsModel.LANGUAGE_ENGLISH
                ? R.string.settings_language_english
                : R.string.settings_language_chinese;
        mBinding.settingsLanguageValue.setText(languageText);
        mBinding.settingsDisplaySummary.setText(languageText);

        mBinding.settingsScreenSaverToggle.setImageResource(screenSaverEnabled
                ? R.drawable.ic_screen_saver_on
                : R.drawable.ic_screen_saver_off);
        mBinding.settingsTimeScreenSaver.setEnabled(screenSaverEnabled);
        mBinding.settingsTimeScreenSaver.setAlpha(screenSaverEnabled ? 1f : 0.45f);
        mBinding.settingsTimeScreenSaverValue.setText(
                getScreenSaverTimeoutText(screenSaverTimeout));

        if (languageOptionsExpanded) {
            showLanguageOptionsPopup(language);
        } else if (screenSaverEnabled && screenSaverTimeoutOptionsExpanded) {
            showScreenSaverTimeoutOptionsPopup(screenSaverTimeout);
        } else {
            dismissDisplayOptionsPopup();
        }
    }

    private void updateTopWifiStatus(String ssid) {
        Activity activity = getActivity();
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).updateWifiConnectionStatus(ssid);
        }
    }

    private void setupWifiSettings() {
        mBinding.settingsNetworkSummary.setText(R.string.main_disconnected);
        mWifiNetworkAdapter = new WifiNetworkAdapter(getHostActivity(),
                network -> {
                    if (mWifiPresenter != null) {
                        mWifiPresenter.onWifiNetworkSelected(network);
                    }
                });
        mBinding.settingsWifiList.setLayoutManager(new LinearLayoutManager(getHostActivity()));
        mBinding.settingsWifiList.setAdapter(mWifiNetworkAdapter);
        mBinding.settingsWifiRefresh.setOnClickListener(view -> {
            if (mWifiPresenter != null) {
                mWifiPresenter.refresh();
            }
        });
        mWifiPresenter = new WifiPresenter(this);
        mWifiPresenter.init(getHostActivity());
    }

    private void setupDisplaySettings() {
        setupBrightnessSettings();
        mBinding.settingsLanguage.setOnClickListener(view -> {
            SettingsPresenter presenter = getPresenter();
            if (presenter != null) {
                presenter.onLanguageSelected();
            }
        });
        mBinding.settingsScreenSaverToggle.setOnClickListener(view -> {
            SettingsPresenter presenter = getPresenter();
            if (presenter != null) {
                presenter.onScreenSaverToggled();
            }
        });
        mBinding.settingsTimeScreenSaver.setOnClickListener(view -> {
            SettingsPresenter presenter = getPresenter();
            if (presenter != null) {
                presenter.onScreenSaverTimeoutSelected();
            }
        });
    }

    private void setupBrightnessSettings() {
        mBinding.settingsBrightness.setProgress(getCurrentScreenBrightness());
        mBinding.settingsBrightness.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress,
                            boolean fromUser) {
                        if (!fromUser) {
                            return;
                        }
                        int brightness = clampScreenBrightness(progress);
                        applyScreenBrightnessToWindow(brightness);
                        scheduleScreenBrightnessWrite(brightness);
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                        // Brightness is applied continuously while the user drags the slider.
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        flushPendingScreenBrightnessWrite();
                    }
                });
    }

    private int getCurrentScreenBrightness() {
        Activity activity = getActivity();
        if (activity == null) {
            return DEFAULT_SCREEN_BRIGHTNESS;
        }
        try {
            return clampScreenBrightness(Settings.System.getInt(activity.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS));
        } catch (Exception exception) {
            Window window = activity.getWindow();
            if (window != null) {
                float brightness = window.getAttributes().screenBrightness;
                if (brightness >= 0f) {
                    return clampScreenBrightness(Math.round(
                            brightness * SCREEN_BRIGHTNESS_MAX));
                }
            }
            Log.w(TAG, "Unable to read screen brightness.", exception);
            return DEFAULT_SCREEN_BRIGHTNESS;
        }
    }

    private void scheduleScreenBrightnessWrite(int brightness) {
        mPendingScreenBrightness = brightness;
        mBrightnessSettingsHandler.removeCallbacks(mPendingBrightnessSettingsWriteRunnable);
        mBrightnessSettingsHandler.postDelayed(mPendingBrightnessSettingsWriteRunnable,
                BRIGHTNESS_SETTINGS_WRITE_THROTTLE_MS);
    }

    private void flushPendingScreenBrightnessWrite() {
        int brightness = mPendingScreenBrightness;
        mPendingScreenBrightness = NO_PENDING_SCREEN_BRIGHTNESS;
        if (brightness == NO_PENDING_SCREEN_BRIGHTNESS) {
            return;
        }
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }
        try {
            Settings.System.putInt(activity.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            Settings.System.putInt(activity.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS, brightness);
        } catch (SecurityException exception) {
            Log.w(TAG, "Unable to persist screen brightness.", exception);
        }
    }

    private void applyScreenBrightnessToWindow(int brightness) {
        Activity activity = getActivity();
        if (activity == null || activity.getWindow() == null) {
            return;
        }
        WindowManager.LayoutParams attributes = activity.getWindow().getAttributes();
        attributes.screenBrightness = brightness / (float) SCREEN_BRIGHTNESS_MAX;
        activity.getWindow().setAttributes(attributes);
    }

    private int clampScreenBrightness(int brightness) {
        return Math.max(SCREEN_BRIGHTNESS_MIN,
                Math.min(SCREEN_BRIGHTNESS_MAX, brightness));
    }

    private void showLanguageOptionsPopup(int language) {
        if (isDisplayOptionsPopupShowing(DISPLAY_POPUP_LANGUAGE)) {
            return;
        }
        dismissDisplayOptionsPopup();
        PopupSettingsLanguageBinding popupBinding = PopupSettingsLanguageBinding.inflate(
                getLayoutInflater());
        popupBinding.settingsLanguageChineseCheck.setVisibility(
                language == SettingsModel.LANGUAGE_CHINESE ? View.VISIBLE : View.GONE);
        popupBinding.settingsLanguageEnglishCheck.setVisibility(
                language == SettingsModel.LANGUAGE_ENGLISH ? View.VISIBLE : View.GONE);
        popupBinding.settingsLanguageOptionChinese.setOnClickListener(view ->
                selectLanguage(SettingsModel.LANGUAGE_CHINESE));
        popupBinding.settingsLanguageOptionEnglish.setOnClickListener(view ->
                selectLanguage(SettingsModel.LANGUAGE_ENGLISH));
        showDisplayOptionsPopup(createDisplayOptionsPopup(popupBinding.getRoot(),
                        LANGUAGE_POPUP_WIDTH_DP, LANGUAGE_POPUP_HEIGHT_DP),
                mBinding.settingsLanguage, DISPLAY_POPUP_LANGUAGE,
                LANGUAGE_POPUP_WIDTH_DP, 9);
    }

    private void showScreenSaverTimeoutOptionsPopup(int timeout) {
        if (isDisplayOptionsPopupShowing(DISPLAY_POPUP_SCREEN_SAVER_TIMEOUT)) {
            return;
        }
        dismissDisplayOptionsPopup();
        PopupSettingsScreenSaverBinding popupBinding = PopupSettingsScreenSaverBinding.inflate(
                getLayoutInflater());
        popupBinding.settingsScreenSaverTimeoutOneMinuteCheck.setVisibility(
                timeout == SettingsModel.SCREEN_SAVER_TIMEOUT_ONE_MINUTE
                        ? View.VISIBLE : View.GONE);
        popupBinding.settingsScreenSaverTimeoutFiveMinutesCheck.setVisibility(
                timeout == SettingsModel.SCREEN_SAVER_TIMEOUT_FIVE_MINUTES
                        ? View.VISIBLE : View.GONE);
        popupBinding.settingsScreenSaverTimeoutTenMinutesCheck.setVisibility(
                timeout == SettingsModel.SCREEN_SAVER_TIMEOUT_TEN_MINUTES
                        ? View.VISIBLE : View.GONE);
        popupBinding.settingsScreenSaverTimeoutThirtyMinutesCheck.setVisibility(
                timeout == SettingsModel.SCREEN_SAVER_TIMEOUT_THIRTY_MINUTES
                        ? View.VISIBLE : View.GONE);
        popupBinding.settingsScreenSaverTimeoutNeverCheck.setVisibility(
                timeout == SettingsModel.SCREEN_SAVER_TIMEOUT_NEVER
                        ? View.VISIBLE : View.GONE);
        popupBinding.settingsScreenSaverTimeoutOneMinute.setOnClickListener(view ->
                selectScreenSaverTimeout(SettingsModel.SCREEN_SAVER_TIMEOUT_ONE_MINUTE));
        popupBinding.settingsScreenSaverTimeoutFiveMinutes.setOnClickListener(view ->
                selectScreenSaverTimeout(SettingsModel.SCREEN_SAVER_TIMEOUT_FIVE_MINUTES));
        popupBinding.settingsScreenSaverTimeoutTenMinutes.setOnClickListener(view ->
                selectScreenSaverTimeout(SettingsModel.SCREEN_SAVER_TIMEOUT_TEN_MINUTES));
        popupBinding.settingsScreenSaverTimeoutThirtyMinutes.setOnClickListener(view ->
                selectScreenSaverTimeout(SettingsModel.SCREEN_SAVER_TIMEOUT_THIRTY_MINUTES));
        popupBinding.settingsScreenSaverTimeoutNever.setOnClickListener(view ->
                selectScreenSaverTimeout(SettingsModel.SCREEN_SAVER_TIMEOUT_NEVER));
        showDisplayOptionsPopup(createDisplayOptionsPopup(popupBinding.getRoot(),
                        SCREEN_SAVER_POPUP_WIDTH_DP, SCREEN_SAVER_POPUP_HEIGHT_DP),
                mBinding.settingsTimeScreenSaver, DISPLAY_POPUP_SCREEN_SAVER_TIMEOUT,
                SCREEN_SAVER_POPUP_WIDTH_DP, 22);
    }

    private PopupWindow createDisplayOptionsPopup(View content, int widthDp, int heightDp) {
        PopupWindow popupWindow = new PopupWindow(content, dp(widthDp), dp(heightDp), true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(true);
        popupWindow.setTouchable(true);
        popupWindow.setElevation(dp(8));
        popupWindow.setTouchInterceptor((view, event) -> false);
        content.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        return popupWindow;
    }

    private void showDisplayOptionsPopup(PopupWindow popupWindow, View anchor, int popupType,
            int popupWidthDp, int verticalOffsetDp) {
        mDisplayOptionsPopupWindow = popupWindow;
        mDisplayPopupType = popupType;
        popupWindow.setOnDismissListener(() -> {
            if (mDisplayOptionsPopupWindow != popupWindow) {
                return;
            }
            mDisplayOptionsPopupWindow = null;
            mDisplayPopupType = DISPLAY_POPUP_NONE;
            SettingsPresenter presenter = getPresenter();
            if (presenter != null) {
                presenter.onDisplayDropdownDismissed();
            }
        });
        int horizontalOffset = Math.max(0, anchor.getWidth() - dp(popupWidthDp));
        popupWindow.showAsDropDown(anchor, horizontalOffset, dp(verticalOffsetDp));
    }

    private boolean isDisplayOptionsPopupShowing(int popupType) {
        return mDisplayOptionsPopupWindow != null
                && mDisplayPopupType == popupType
                && mDisplayOptionsPopupWindow.isShowing();
    }

    private void dismissDisplayOptionsPopup() {
        if (mDisplayOptionsPopupWindow != null) {
            mDisplayOptionsPopupWindow.dismiss();
        }
    }

    private void selectLanguage(int language) {
        if (mLanguageSwitchInProgress) {
            return;
        }
        Activity activity = getHostActivity();
        Context applicationContext = activity.getApplicationContext();
        Application application = activity.getApplication();
        String selectedLanguage = language == SettingsModel.LANGUAGE_ENGLISH
                ? LocaleHelper.LANGUAGE_EN : LocaleHelper.LANGUAGE_ZH;
        boolean languageChanged = !TextUtils.equals(LocaleHelper.getLanguage(applicationContext),
                selectedLanguage);

        SettingsPresenter presenter = getPresenter();
        if (presenter != null) {
            presenter.onLanguageOptionSelected(language);
        }
        if (!languageChanged) {
            return;
        }

        mLanguageSwitchInProgress = true;
        setLanguageSwitchLoading(true);
        ExecutorService languageSwitchExecutor = mLanguageSwitchExecutor;
        if (languageSwitchExecutor == null) {
            mLanguageSwitchInProgress = false;
            setLanguageSwitchLoading(false);
            return;
        }
        languageSwitchExecutor.execute(() -> {
            LocaleHelper.setLanguage(applicationContext, selectedLanguage);
            LocaleHelper.applySystemLocale(applicationContext, selectedLanguage);
            activity.runOnUiThread(() -> completeLanguageChange(activity, application));
        });
    }

    private void selectScreenSaverTimeout(int timeout) {
        SettingsPresenter presenter = getPresenter();
        if (presenter != null) {
            presenter.onScreenSaverTimeoutOptionSelected(timeout);
        }
    }

    private int getScreenSaverTimeoutText(int timeout) {
        switch (timeout) {
            case SettingsModel.SCREEN_SAVER_TIMEOUT_FIVE_MINUTES:
                return R.string.settings_screensaver_timeout_five_minutes;
            case SettingsModel.SCREEN_SAVER_TIMEOUT_TEN_MINUTES:
                return R.string.settings_screensaver_timeout_ten_minutes;
            case SettingsModel.SCREEN_SAVER_TIMEOUT_THIRTY_MINUTES:
                return R.string.settings_screensaver_timeout_thirty_minutes;
            case SettingsModel.SCREEN_SAVER_TIMEOUT_NEVER:
                return R.string.settings_screensaver_timeout_never;
            case SettingsModel.SCREEN_SAVER_TIMEOUT_ONE_MINUTE:
            default:
                return R.string.settings_screensaver_timeout_value;
        }
    }

    private int getSavedLanguage() {
        return LocaleHelper.LANGUAGE_EN.equals(
                LocaleHelper.getLanguage(getHostActivity().getApplicationContext()))
                ? SettingsModel.LANGUAGE_ENGLISH : SettingsModel.LANGUAGE_CHINESE;
    }

    private void setLanguageSwitchLoading(boolean loading) {
        if (mBinding != null) {
            mBinding.settingsLanguage.setEnabled(!loading);
        }
        Activity activity = getActivity();
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).setLanguageSwitchLoading(loading);
        }
    }

    private void completeLanguageChange(Activity activity, Application application) {
        if (!isAdded() || getActivity() != activity) {
            return;
        }
        mLanguageSwitchInProgress = false;
        LocaleHelper.applyLocale(application);
        setLanguageSwitchLoading(false);
        if (!activity.isFinishing() && !activity.isDestroyed()) {
            restartForLanguageChange(activity);
        }
    }

    private void restartForLanguageChange(Activity activity) {
        Intent launchIntent = activity.getPackageManager().getLaunchIntentForPackage(
                activity.getPackageName());
        if (launchIntent == null) {
            activity.recreate();
            return;
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        activity.startActivity(launchIntent);
        activity.finish();
    }

    private Activity getHostActivity() {
        Activity activity = getActivity();
        if (activity == null) {
            throw new IllegalStateException("SettingsFragment is not attached to an activity.");
        }
        return activity;
    }

    private void dismissWifiPasswordDialog() {
        if (mWifiPasswordDialog != null) {
            mWifiPasswordDialog.dismiss();
            mWifiPasswordDialog = null;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
