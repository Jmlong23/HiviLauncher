package com.hivi.launcher.settings.ui;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.ColorDrawable;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import com.hivi.launcher.utils.log.AppLog;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.ViewOutlineProvider;
import android.view.animation.LinearInterpolator;
import android.widget.PopupWindow;
import android.widget.SeekBar;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hivi.launcher.R;
import com.hivi.launcher.base.BaseFragment;
import com.hivi.launcher.databinding.DialogFactoryResetBinding;
import com.hivi.launcher.databinding.DialogLogUploadBinding;
import com.hivi.launcher.databinding.DialogProductInformationBinding;
import com.hivi.launcher.databinding.DialogSystemUpdateConfirmationBinding;
import com.hivi.launcher.databinding.DialogSystemUpdateProgressBinding;
import com.hivi.launcher.databinding.DialogWifiPasswordBinding;
import com.hivi.launcher.databinding.LayoutSettingsContentBinding;
import com.hivi.launcher.databinding.PopupSettingsLanguageBinding;
import com.hivi.launcher.databinding.PopupSettingsScreenSaverBinding;
import com.hivi.launcher.main.ui.MainActivity;
import com.hivi.launcher.onboarding.ui.FirstUseGuideActivity;
import com.hivi.launcher.settings.model.SettingsModel;
import com.hivi.launcher.settings.model.ScreenSaverSettings;
import com.hivi.launcher.settings.presenter.SettingsPresenter;
import com.hivi.launcher.update.SystemUpdateInfo;
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
    private static final long INITIAL_WIFI_SETUP_DELAY_MS = 500L;
    private static final long LOG_UPLOAD_SUCCESS_DISMISS_DELAY_MS = 1_500L;
    private static final long FACTORY_RESET_SUCCESS_TRANSITION_DELAY_MS = 1_200L;

    private LayoutSettingsContentBinding mBinding;
    private View[] mSectionTabs;
    private View[] mSectionPanels;
    private WifiPresenter mWifiPresenter;
    private WifiNetworkAdapter mWifiNetworkAdapter;
    private ObjectAnimator mWifiRefreshAnimator;
    private Dialog mWifiPasswordDialog;
    private Dialog mSystemUpdateConfirmationDialog;
    private Dialog mSystemUpdateProgressDialog;
    private DialogSystemUpdateProgressBinding mSystemUpdateProgressBinding;
    private Dialog mProductInformationDialog;
    private Dialog mSimpleClockWallpaperDialog;
    private Dialog mLogUploadDialog;
    private DialogLogUploadBinding mLogUploadBinding;
    private Dialog mFactoryResetDialog;
    private DialogFactoryResetBinding mFactoryResetBinding;
    private ObjectAnimator mFactoryResetSuccessLoadingAnimator;
    private PopupWindow mDisplayOptionsPopupWindow;
    private ExecutorService mLanguageSwitchExecutor;
    private final Handler mBrightnessSettingsHandler = new Handler(Looper.getMainLooper());
    private final Runnable mPendingBrightnessSettingsWriteRunnable =
            this::flushPendingScreenBrightnessWrite;
    private final Runnable mDeferredWifiSettingsInitialization =
            this::initializeWifiSettingsIfNeeded;
    private final Runnable mLogUploadSuccessDismissRunnable = this::dismissLogUploadDialog;
    private final Runnable mFactoryResetSuccessTransitionRunnable =
            this::openFirstUseGuideAfterFactoryReset;
    private int mDisplayPopupType = DISPLAY_POPUP_NONE;
    private int mPendingScreenBrightness = NO_PENDING_SCREEN_BRIGHTNESS;
    private int mSelectedSettingsSection = SettingsPresenter.SECTION_NETWORK;
    private boolean mWifiRefreshing;
    private boolean mWifiSettingsInitialized;
    private boolean mInitialSectionRendered;
    private boolean mLanguageSwitchInProgress;

    @Override
    protected SettingsPresenter createPresenter() {
        return new SettingsPresenter(getHostActivity(), this, getSavedLanguage());
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
        setupWifiRefreshAction();
        showWifiInitializationPending();
        scheduleWifiSettingsInitialization();
        setupDisplaySettings();
        setupSystemUpdateSettings();
        setupAboutSettings();
        presenter.init();
    }

    @Override
    public void onDestroyView() {
        mBrightnessSettingsHandler.removeCallbacks(mPendingBrightnessSettingsWriteRunnable);
        mBrightnessSettingsHandler.removeCallbacks(mDeferredWifiSettingsInitialization);
        mBrightnessSettingsHandler.removeCallbacks(mLogUploadSuccessDismissRunnable);
        mBrightnessSettingsHandler.removeCallbacks(mFactoryResetSuccessTransitionRunnable);
        flushPendingScreenBrightnessWrite();
        dismissDisplayOptionsPopup();
        dismissWifiPasswordDialog();
        dismissSystemUpdateConfirmationDialog();
        dismissSystemUpdateProgress();
        dismissProductInformationDialog();
        dismissSimpleClockWallpaperDialog();
        dismissLogUploadDialog();
        dismissFactoryResetDialog();
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
        mWifiSettingsInitialized = false;
        mInitialSectionRendered = false;
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
        mSelectedSettingsSection = section;
        for (int i = 0; i < mSectionTabs.length; i++) {
            boolean selected = i == section;
            mSectionTabs[i].setSelected(selected);
            mSectionPanels[i].setVisibility(selected ? View.VISIBLE : View.GONE);
        }
        if (section == SettingsPresenter.SECTION_NETWORK && !mWifiSettingsInitialized) {
            showWifiInitializationPending();
        }
        if (section == SettingsPresenter.SECTION_NETWORK && mInitialSectionRendered) {
            mBrightnessSettingsHandler.removeCallbacks(mDeferredWifiSettingsInitialization);
            initializeWifiSettingsIfNeeded();
        }
        mInitialSectionRendered = true;
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
            mBinding.settingsWifiEmptyState.setVisibility(View.GONE);
            mBinding.settingsWifiLoading.setVisibility(View.GONE);
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
        mBinding.settingsWifiLoading.setVisibility(View.GONE);
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
            int screenSaverTimeout,
            boolean screenSaverTimeoutOptionsExpanded, int screenSaverStyle) {
        if (mBinding == null) {
            return;
        }
        int languageText = language == SettingsModel.LANGUAGE_ENGLISH
                ? R.string.settings_language_english
                : R.string.settings_language_chinese;
        mBinding.settingsLanguageValue.setText(languageText);
        mBinding.settingsDisplaySummary.setText(languageText);

        updateScreenSaverStyles(screenSaverStyle);
        mBinding.settingsTimeScreenSaverValue.setText(
                getScreenSaverTimeoutText(screenSaverTimeout));

        if (languageOptionsExpanded) {
            showLanguageOptionsPopup(language);
        } else if (screenSaverTimeoutOptionsExpanded) {
            showScreenSaverTimeoutOptionsPopup(screenSaverTimeout);
        } else {
            dismissDisplayOptionsPopup();
        }
    }

    @Override
    public void renderSystemUpdate(SystemUpdateInfo updateInfo, boolean updateInProgress) {
        if (mBinding == null || updateInfo == null) {
            return;
        }
        String currentVersion = formatSystemVersion(updateInfo.getCurrentVersionName());
        mBinding.settingsSystemVersionValue.setText(currentVersion);
        mBinding.settingsSystemSummaryValue.setText(currentVersion);
        boolean updateAvailable = updateInfo.isUpdateAvailable();
        mBinding.settingsSystemUpdateAction.setVisibility(
                updateAvailable && !updateInProgress ? View.VISIBLE : View.GONE);
        mBinding.settingsSystemUpdateAction.setEnabled(updateAvailable && !updateInProgress);
    }

    @Override
    public void showSystemUpdateConfirmation(SystemUpdateInfo updateInfo) {
        if (!isAdded() || updateInfo == null) {
            return;
        }
        dismissSystemUpdateConfirmationDialog();
        Dialog dialog = new Dialog(getHostActivity());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        DialogSystemUpdateConfirmationBinding dialogBinding =
                DialogSystemUpdateConfirmationBinding.inflate(getLayoutInflater());
        dialog.setContentView(dialogBinding.getRoot());
        dialog.setCanceledOnTouchOutside(true);
        dialog.setCancelable(true);
        dialogBinding.systemUpdateConfirmationMessage.setText(getString(
                R.string.system_update_confirm_message,
                formatSystemVersion(updateInfo.getLatestVersionName())));
        dialogBinding.systemUpdateConfirmationCancel.setOnClickListener(view -> dialog.dismiss());
        dialogBinding.systemUpdateConfirmationConfirm.setOnClickListener(view -> {
            dialog.dismiss();
            SettingsPresenter presenter = getPresenter();
            if (presenter != null) {
                presenter.onSystemUpdateConfirmed();
            }
        });
        dialog.setOnDismissListener(ignored -> {
            if (mSystemUpdateConfirmationDialog == dialog) {
                mSystemUpdateConfirmationDialog = null;
            }
        });
        prepareSystemUpdateDialogWindow(dialog);
        mSystemUpdateConfirmationDialog = dialog;
        showSystemUpdateDialog(dialog);
    }

    @Override
    public void showSystemUpdateProgress(int progress, String status) {
        if (!isAdded()) {
            return;
        }
        if (mSystemUpdateProgressDialog == null) {
            Dialog dialog = new Dialog(getHostActivity());
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            mSystemUpdateProgressBinding = DialogSystemUpdateProgressBinding.inflate(
                    getLayoutInflater());
            dialog.setContentView(mSystemUpdateProgressBinding.getRoot());
            dialog.setCanceledOnTouchOutside(false);
            dialog.setCancelable(false);
            dialog.setOnDismissListener(ignored -> {
                if (mSystemUpdateProgressDialog == dialog) {
                    mSystemUpdateProgressDialog = null;
                    mSystemUpdateProgressBinding = null;
                }
            });
            prepareSystemUpdateDialogWindow(dialog);
            mSystemUpdateProgressDialog = dialog;
        }
        if (mSystemUpdateProgressBinding == null) {
            return;
        }
        int safeProgress = Math.max(0, Math.min(100, progress));
        mSystemUpdateProgressBinding.systemUpdateProgressBar.setProgress(safeProgress);
        mSystemUpdateProgressBinding.systemUpdateProgressPercent.setText(
                getString(R.string.system_update_progress_percent_format, safeProgress));
        mSystemUpdateProgressBinding.systemUpdateProgressStatus.setText(status);
        showSystemUpdateDialog(mSystemUpdateProgressDialog);
    }

    @Override
    public void dismissSystemUpdateProgress() {
        if (mSystemUpdateProgressDialog != null) {
            mSystemUpdateProgressDialog.dismiss();
            mSystemUpdateProgressDialog = null;
        }
        mSystemUpdateProgressBinding = null;
    }

    public void onSystemUpdatePackageReplaced() {
        SettingsPresenter presenter = getPresenter();
        if (presenter != null) {
            presenter.onSystemUpdatePackageReplaced();
        }
    }

    @Override
    public void showLogUploadConfirmation() {
        if (!ensureLogUploadDialog()) {
            return;
        }
        mBrightnessSettingsHandler.removeCallbacks(mLogUploadSuccessDismissRunnable);
        mLogUploadDialog.setCanceledOnTouchOutside(true);
        mLogUploadDialog.setCancelable(true);
        mLogUploadBinding.logUploadConfirmationContent.setVisibility(View.VISIBLE);
        mLogUploadBinding.logUploadProgressContent.setVisibility(View.GONE);
        mLogUploadBinding.logUploadSuccessContent.setVisibility(View.GONE);
        showSystemUpdateDialog(mLogUploadDialog);
    }

    @Override
    public void showLogUploadProgress(int progress, String status) {
        if (!ensureLogUploadDialog()) {
            return;
        }
        mBrightnessSettingsHandler.removeCallbacks(mLogUploadSuccessDismissRunnable);
        mLogUploadDialog.setCanceledOnTouchOutside(false);
        mLogUploadDialog.setCancelable(false);
        mLogUploadBinding.logUploadConfirmationContent.setVisibility(View.GONE);
        mLogUploadBinding.logUploadProgressContent.setVisibility(View.VISIBLE);
        mLogUploadBinding.logUploadSuccessContent.setVisibility(View.GONE);
        int safeProgress = Math.max(0, Math.min(100, progress));
        mLogUploadBinding.logUploadProgressBar.setProgress(safeProgress);
        mLogUploadBinding.logUploadProgressPercent.setText(
                getString(R.string.log_upload_progress_percent_format, safeProgress));
        mLogUploadBinding.logUploadProgressStatus.setText(status);
        showSystemUpdateDialog(mLogUploadDialog);
    }

    @Override
    public void showLogUploadSuccess() {
        if (!ensureLogUploadDialog()) {
            return;
        }
        mLogUploadBinding.logUploadConfirmationContent.setVisibility(View.GONE);
        mLogUploadBinding.logUploadProgressContent.setVisibility(View.GONE);
        mLogUploadBinding.logUploadSuccessContent.setVisibility(View.VISIBLE);
        showSystemUpdateDialog(mLogUploadDialog);
        mBrightnessSettingsHandler.removeCallbacks(mLogUploadSuccessDismissRunnable);
        mBrightnessSettingsHandler.postDelayed(mLogUploadSuccessDismissRunnable,
                LOG_UPLOAD_SUCCESS_DISMISS_DELAY_MS);
    }

    @Override
    public void showLogUploadFailure() {
        dismissLogUploadDialog();
        showToast(getString(R.string.log_upload_failed));
    }

    @Override
    public void showFactoryResetConfirmation() {
        showFactoryResetFinalConfirmation();
    }

    @Override
    public void showFactoryResetFinalConfirmation() {
        if (!ensureFactoryResetDialog()) {
            return;
        }
        mBrightnessSettingsHandler.removeCallbacks(mFactoryResetSuccessTransitionRunnable);
        stopFactoryResetSuccessLoadingAnimation();
        mFactoryResetDialog.setCanceledOnTouchOutside(true);
        mFactoryResetDialog.setCancelable(true);
        mFactoryResetBinding.factoryResetConfirmationContent.setVisibility(View.GONE);
        mFactoryResetBinding.factoryResetFinalConfirmationContent.setVisibility(View.VISIBLE);
        mFactoryResetBinding.factoryResetProgressContent.setVisibility(View.GONE);
        mFactoryResetBinding.factoryResetSuccessContent.setVisibility(View.GONE);
        showSystemUpdateDialog(mFactoryResetDialog);
    }

    @Override
    public void showFactoryResetProgress(int progress, String status) {
        if (!ensureFactoryResetDialog()) {
            return;
        }
        mBrightnessSettingsHandler.removeCallbacks(mFactoryResetSuccessTransitionRunnable);
        stopFactoryResetSuccessLoadingAnimation();
        mFactoryResetDialog.setCanceledOnTouchOutside(false);
        mFactoryResetDialog.setCancelable(false);
        mFactoryResetBinding.factoryResetConfirmationContent.setVisibility(View.GONE);
        mFactoryResetBinding.factoryResetFinalConfirmationContent.setVisibility(View.GONE);
        mFactoryResetBinding.factoryResetProgressContent.setVisibility(View.VISIBLE);
        mFactoryResetBinding.factoryResetSuccessContent.setVisibility(View.GONE);
        int safeProgress = Math.max(0, Math.min(100, progress));
        mFactoryResetBinding.factoryResetProgressBar.setProgress(safeProgress);
        mFactoryResetBinding.factoryResetProgressPercent.setText(
                getString(R.string.factory_reset_progress_percent_format, safeProgress));
        mFactoryResetBinding.factoryResetProgressStatus.setText(status);
        showSystemUpdateDialog(mFactoryResetDialog);
    }

    @Override
    public void showFactoryResetSuccess() {
        if (!ensureFactoryResetDialog()) {
            return;
        }
        mFactoryResetDialog.setCanceledOnTouchOutside(false);
        mFactoryResetDialog.setCancelable(false);
        mFactoryResetBinding.factoryResetConfirmationContent.setVisibility(View.GONE);
        mFactoryResetBinding.factoryResetFinalConfirmationContent.setVisibility(View.GONE);
        mFactoryResetBinding.factoryResetProgressContent.setVisibility(View.GONE);
        mFactoryResetBinding.factoryResetSuccessContent.setVisibility(View.VISIBLE);
        showSystemUpdateDialog(mFactoryResetDialog);
        startFactoryResetSuccessLoadingAnimation();
        mBrightnessSettingsHandler.removeCallbacks(mFactoryResetSuccessTransitionRunnable);
        mBrightnessSettingsHandler.postDelayed(mFactoryResetSuccessTransitionRunnable,
                FACTORY_RESET_SUCCESS_TRANSITION_DELAY_MS);
    }

    @Override
    public void showFactoryResetFailure() {
        dismissFactoryResetDialog();
        showToast(getString(R.string.factory_reset_failed));
    }

    private void updateTopWifiStatus(String ssid) {
        Activity activity = getActivity();
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).updateWifiConnectionStatus(ssid);
        }
    }

    private void setupWifiRefreshAction() {
        mBinding.settingsWifiRefresh.setOnClickListener(view -> {
            boolean initialized = initializeWifiSettingsIfNeeded();
            if (!initialized && mWifiPresenter != null) {
                mWifiPresenter.refresh();
            }
        });
    }

    private void scheduleWifiSettingsInitialization() {
        mBrightnessSettingsHandler.removeCallbacks(mDeferredWifiSettingsInitialization);
        mBrightnessSettingsHandler.postDelayed(mDeferredWifiSettingsInitialization,
                INITIAL_WIFI_SETUP_DELAY_MS);
    }

    private void showWifiInitializationPending() {
        if (mBinding == null) {
            return;
        }
        mBinding.settingsWifiList.setVisibility(View.GONE);
        mBinding.settingsWifiEmptyState.setVisibility(View.GONE);
        mBinding.settingsWifiLoading.setVisibility(View.GONE);
    }

    private boolean initializeWifiSettingsIfNeeded() {
        if (mWifiSettingsInitialized || mBinding == null || !isAdded()) {
            return false;
        }
        if (mSelectedSettingsSection != SettingsPresenter.SECTION_NETWORK) {
            return false;
        }
        mWifiSettingsInitialized = true;
        mBinding.settingsNetworkSummary.setText(R.string.main_disconnected);
        mWifiNetworkAdapter = new WifiNetworkAdapter(getHostActivity(),
                network -> {
                    if (mWifiPresenter != null) {
                        mWifiPresenter.onWifiNetworkSelected(network);
                    }
                });
        mBinding.settingsWifiList.setLayoutManager(new LinearLayoutManager(getHostActivity()));
        mBinding.settingsWifiList.setAdapter(mWifiNetworkAdapter);
        mWifiPresenter = new WifiPresenter(this);
        mWifiPresenter.init(getHostActivity());
        return true;
    }

    private void setupDisplaySettings() {
        setupBrightnessSettings();
        setupRoundedScreenSaverThumbnails();
        mBinding.settingsLanguage.setOnClickListener(view -> {
            SettingsPresenter presenter = getPresenter();
            if (presenter != null) {
                presenter.onLanguageSelected();
            }
        });
        mBinding.settingsTimeScreenSaver.setOnClickListener(view -> {
            SettingsPresenter presenter = getPresenter();
            if (presenter != null) {
                presenter.onScreenSaverTimeoutSelected();
            }
        });
        mBinding.screensaverItemSimple.setOnClickListener(view -> {
            selectScreenSaverStyle(SettingsModel.SCREEN_SAVER_STYLE_SIMPLE);
            showSimpleClockWallpaperDialog();
        });
        mBinding.screensaverItemWeather.setOnClickListener(view -> selectScreenSaverStyle(
                SettingsModel.SCREEN_SAVER_STYLE_WEATHER));
        mBinding.screensaverItemFlip.setOnClickListener(view -> selectScreenSaverStyle(
                SettingsModel.SCREEN_SAVER_STYLE_FLIP));
        mBinding.screensaverItemBlack.setOnClickListener(view -> selectScreenSaverStyle(
                SettingsModel.SCREEN_SAVER_STYLE_BLACK));
    }

    private void setupRoundedScreenSaverThumbnails() {
        ImageView[] thumbnails = {mBinding.screensaverThumbnailSimple,
                mBinding.screensaverThumbnailSimpleClock,
                mBinding.screensaverThumbnailWeather, mBinding.screensaverThumbnailFlip};
        for (ImageView thumbnail : thumbnails) {
            thumbnail.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(7));
                }
            });
            thumbnail.setClipToOutline(true);
        }
        updateSimpleScreenSaverThumbnail();
    }

    private void updateSimpleScreenSaverThumbnail() {
        if (mBinding == null || !isAdded()) {
            return;
        }
        int wallpaper = ScreenSaverSettings.getSimpleWallpaper(getHostActivity());
        mBinding.screensaverThumbnailSimple.setImageResource(
                ScreenSaverSettings.getSimpleWallpaperResource(wallpaper));
        boolean english = LocaleHelper.LANGUAGE_EN.equals(
                LocaleHelper.getLanguage(getHostActivity()));
        mBinding.screensaverThumbnailSimpleClock.setImageResource(english
                ? R.drawable.clock_simple_en : R.drawable.clock_simple_cn);
    }

    private void selectScreenSaverStyle(int style) {
        SettingsPresenter presenter = getPresenter();
        if (presenter != null) {
            presenter.onScreenSaverStyleSelected(style);
        }
    }

    private void showSimpleClockWallpaperDialog() {
        if (!isAdded()) {
            return;
        }
        dismissSimpleClockWallpaperDialog();
        Dialog dialog = new Dialog(getHostActivity());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        View content = getLayoutInflater().inflate(R.layout.dialog_simple_clock_wallpaper, null);
        dialog.setContentView(content);
        dialog.setCanceledOnTouchOutside(true);
        dialog.setCancelable(true);
        RecyclerView wallpaperList = content.findViewById(R.id.simple_clock_wallpaper_list);
        int selectedWallpaper = ScreenSaverSettings.getSimpleWallpaper(getHostActivity());
        wallpaperList.setLayoutManager(new LinearLayoutManager(getHostActivity(),
                RecyclerView.HORIZONTAL, false));
        wallpaperList.setAdapter(new SimpleClockWallpaperAdapter(selectedWallpaper, wallpaper -> {
            ScreenSaverSettings.setSimpleWallpaper(getHostActivity(), wallpaper);
            updateSimpleScreenSaverThumbnail();
            dialog.dismiss();
        }));
        wallpaperList.scrollToPosition(selectedWallpaper);
        content.findViewById(R.id.simple_clock_wallpaper_back).setOnClickListener(
                view -> dialog.dismiss());
        dialog.setOnDismissListener(ignored -> {
            if (mSimpleClockWallpaperDialog == dialog) {
                mSimpleClockWallpaperDialog = null;
            }
        });
        prepareSystemUpdateDialogWindow(dialog);
        mSimpleClockWallpaperDialog = dialog;
        showSystemUpdateDialog(dialog);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(dp(681), dp(349));
        }
    }

    private void updateScreenSaverStyles(int selectedStyle) {
        View[] items = {mBinding.screensaverItemSimple, mBinding.screensaverItemWeather,
                mBinding.screensaverItemFlip, mBinding.screensaverItemBlack};
        View[] checks = {mBinding.screensaverCheckSimple, mBinding.screensaverCheckWeather,
                mBinding.screensaverCheckFlip, mBinding.screensaverCheckBlack};
        for (int index = 0; index < items.length; index++) {
            items[index].setEnabled(true);
            items[index].setAlpha(1f);
            items[index].setSelected(index == selectedStyle);
            checks[index].setVisibility(index == selectedStyle
                    ? View.VISIBLE : View.GONE);
        }
    }

    private void setupSystemUpdateSettings() {
        mBinding.settingsSystemUpdateAction.setOnClickListener(view -> {
            SettingsPresenter presenter = getPresenter();
            if (presenter != null) {
                presenter.onSystemUpdateSelected();
            }
        });
        mBinding.settingsLogUpload.setOnClickListener(view -> {
            SettingsPresenter presenter = getPresenter();
            if (presenter != null) {
                presenter.onLogUploadSelected();
            }
        });
        mBinding.settingsRestoreFactory.setOnClickListener(view -> {
            SettingsPresenter presenter = getPresenter();
            if (presenter != null) {
                presenter.onFactoryResetSelected();
            }
        });
    }

    private void setupAboutSettings() {
        mBinding.settingsProductInformation.setOnClickListener(
                view -> showProductInformationDialog());
    }

    private void showProductInformationDialog() {
        if (!isAdded()) {
            return;
        }
        dismissProductInformationDialog();
        Dialog dialog = new Dialog(getHostActivity());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        DialogProductInformationBinding dialogBinding = DialogProductInformationBinding.inflate(
                getLayoutInflater());
        dialog.setContentView(dialogBinding.getRoot());
        dialog.setCanceledOnTouchOutside(true);
        dialog.setCancelable(true);
        dialogBinding.productInformationIpAddress.setText(getCurrentIpAddress());
        dialogBinding.productInformationClose.setOnClickListener(view -> dialog.dismiss());
        dialog.setOnDismissListener(ignored -> {
            if (mProductInformationDialog == dialog) {
                mProductInformationDialog = null;
            }
        });
        prepareSystemUpdateDialogWindow(dialog);
        mProductInformationDialog = dialog;
        showSystemUpdateDialog(dialog);
    }

    private String getCurrentIpAddress() {
        WifiManager wifiManager = (WifiManager) getHostActivity().getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) {
            return getString(R.string.product_information_ip_unavailable);
        }
        try {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            int ipAddress = wifiInfo == null ? 0 : wifiInfo.getIpAddress();
            if (ipAddress == 0) {
                return getString(R.string.product_information_ip_unavailable);
            }
            return (ipAddress & 0xFF) + "." + (ipAddress >> 8 & 0xFF) + "."
                    + (ipAddress >> 16 & 0xFF) + "." + (ipAddress >> 24 & 0xFF);
        } catch (SecurityException exception) {
            AppLog.w(TAG, "Unable to read Wi-Fi IP address.", exception);
            return getString(R.string.product_information_ip_unavailable);
        }
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
            AppLog.w(TAG, "Unable to read screen brightness.", exception);
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
            AppLog.w(TAG, "Unable to persist screen brightness.", exception);
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

    private void dismissSystemUpdateConfirmationDialog() {
        if (mSystemUpdateConfirmationDialog != null) {
            mSystemUpdateConfirmationDialog.dismiss();
            mSystemUpdateConfirmationDialog = null;
        }
    }

    private void dismissProductInformationDialog() {
        if (mProductInformationDialog != null) {
            mProductInformationDialog.dismiss();
            mProductInformationDialog = null;
        }
    }

    private void dismissSimpleClockWallpaperDialog() {
        if (mSimpleClockWallpaperDialog != null) {
            mSimpleClockWallpaperDialog.dismiss();
            mSimpleClockWallpaperDialog = null;
        }
    }

    private void startFactoryResetSuccessLoadingAnimation() {
        if (mFactoryResetBinding == null) {
            return;
        }
        stopFactoryResetSuccessLoadingAnimation();
        View loadingView = mFactoryResetBinding.factoryResetSuccessLoading;
        ObjectAnimator animator = ObjectAnimator.ofFloat(loadingView, View.ROTATION, 0f, 360f);
        animator.setDuration(900L);
        animator.setInterpolator(new LinearInterpolator());
        animator.setRepeatCount(ObjectAnimator.INFINITE);
        animator.start();
        mFactoryResetSuccessLoadingAnimator = animator;
    }

    private void stopFactoryResetSuccessLoadingAnimation() {
        if (mFactoryResetSuccessLoadingAnimator != null) {
            mFactoryResetSuccessLoadingAnimator.cancel();
            mFactoryResetSuccessLoadingAnimator = null;
        }
        if (mFactoryResetBinding != null) {
            mFactoryResetBinding.factoryResetSuccessLoading.setRotation(0f);
        }
    }

    private boolean ensureFactoryResetDialog() {
        if (!isAdded()) {
            return false;
        }
        if (mFactoryResetDialog != null && mFactoryResetBinding != null) {
            return true;
        }
        Dialog dialog = new Dialog(getHostActivity());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        DialogFactoryResetBinding dialogBinding =
                DialogFactoryResetBinding.inflate(getLayoutInflater());
        dialog.setContentView(dialogBinding.getRoot());
        dialogBinding.factoryResetConfirmationCancel.setOnClickListener(view -> dialog.dismiss());
        dialogBinding.factoryResetConfirmationContinue.setOnClickListener(view -> {
            dismissFactoryResetDialog();
            SettingsPresenter presenter = getPresenter();
            if (presenter != null) {
                presenter.onFactoryResetFirstConfirmed();
            }
        });
        dialogBinding.factoryResetFinalConfirmationCancel.setOnClickListener(view ->
                dialog.dismiss());
        dialogBinding.factoryResetFinalConfirmationConfirm.setOnClickListener(view -> {
            SettingsPresenter presenter = getPresenter();
            if (presenter != null) {
                presenter.onFactoryResetConfirmed();
            }
        });
        dialog.setOnDismissListener(ignored -> {
            if (mFactoryResetDialog == dialog) {
                mFactoryResetDialog = null;
                mFactoryResetBinding = null;
            }
        });
        prepareSystemUpdateDialogWindow(dialog);
        mFactoryResetDialog = dialog;
        mFactoryResetBinding = dialogBinding;
        return true;
    }

    private void dismissFactoryResetDialog() {
        mBrightnessSettingsHandler.removeCallbacks(mFactoryResetSuccessTransitionRunnable);
        stopFactoryResetSuccessLoadingAnimation();
        if (mFactoryResetDialog != null) {
            mFactoryResetDialog.dismiss();
            mFactoryResetDialog = null;
        }
        mFactoryResetBinding = null;
    }

    private void openFirstUseGuideAfterFactoryReset() {
        if (!isAdded()) {
            return;
        }
        Activity activity = getActivity();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        dismissFactoryResetDialog();
        Intent intent = new Intent(activity, FirstUseGuideActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        activity.startActivity(intent);
        activity.finish();
    }

    private boolean ensureLogUploadDialog() {
        if (!isAdded()) {
            return false;
        }
        if (mLogUploadDialog != null && mLogUploadBinding != null) {
            return true;
        }
        Dialog dialog = new Dialog(getHostActivity());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        DialogLogUploadBinding dialogBinding = DialogLogUploadBinding.inflate(getLayoutInflater());
        dialog.setContentView(dialogBinding.getRoot());
        dialogBinding.logUploadCancel.setOnClickListener(view -> dialog.dismiss());
        dialogBinding.logUploadConfirm.setOnClickListener(view -> {
            SettingsPresenter presenter = getPresenter();
            if (presenter != null) {
                presenter.onLogUploadConfirmed();
            }
        });
        dialog.setOnDismissListener(ignored -> {
            if (mLogUploadDialog == dialog) {
                mLogUploadDialog = null;
                mLogUploadBinding = null;
            }
        });
        prepareSystemUpdateDialogWindow(dialog);
        mLogUploadDialog = dialog;
        mLogUploadBinding = dialogBinding;
        return true;
    }

    private void dismissLogUploadDialog() {
        mBrightnessSettingsHandler.removeCallbacks(mLogUploadSuccessDismissRunnable);
        if (mLogUploadDialog != null) {
            mLogUploadDialog.dismiss();
            mLogUploadDialog = null;
        }
        mLogUploadBinding = null;
    }

    private void prepareSystemUpdateDialogWindow(Dialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) {
            return;
        }
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.dimAmount = 0.72f;
        window.setAttributes(attributes);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
    }

    private void showSystemUpdateDialog(Dialog dialog) {
        if (dialog == null || dialog.isShowing()) {
            return;
        }
        dialog.show();
        Window dialogWindow = dialog.getWindow();
        if (dialogWindow == null) {
            return;
        }
        dialogWindow.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        dialogWindow.setLayout(WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT);
    }

    private String formatSystemVersion(String versionName) {
        if (TextUtils.isEmpty(versionName)) {
            return "";
        }
        return versionName.startsWith("v") || versionName.startsWith("V")
                ? versionName : "v " + versionName;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
