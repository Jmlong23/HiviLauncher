package com.hivi.launcher.onboarding.ui;

import android.animation.ObjectAnimator;
import android.app.Application;
import android.app.Dialog;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.SeekBar;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.hivi.launcher.R;
import com.hivi.launcher.base.BaseActivity;
import com.hivi.launcher.databinding.ActivityFirstUseGuideBinding;
import com.hivi.launcher.databinding.DialogWifiPasswordBinding;
import com.hivi.launcher.main.model.MainPage;
import com.hivi.launcher.main.ui.MainActivity;
import com.hivi.launcher.music.model.BluetoothMediaController;
import com.hivi.launcher.onboarding.model.FirstUseGuideStore;
import com.hivi.launcher.onboarding.presenter.OnboardingPresenter;
import com.hivi.launcher.utils.LocaleHelper;
import com.hivi.launcher.wifi.model.WifiConnectionStatus;
import com.hivi.launcher.wifi.model.WifiNetwork;
import com.hivi.launcher.wifi.ui.WifiNetworkAdapter;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Guided setup shown before the launcher home screen is made available on a new device.
 *
 * <p>The screen intentionally reuses the regular Wi-Fi model and amplifier controller, so a
 * connection or volume selected here has exactly the same behavior as when it is changed later
 * from the launcher.</p>
 */
public final class FirstUseGuideActivity
        extends BaseActivity<ActivityFirstUseGuideBinding, OnboardingPresenter>
        implements OnboardingView {
    private static final String STATE_PAGE = "page";
    private static final String STATE_LANGUAGE = "language";
    private static final String STATE_SELECTED_INPUT = "selected_input";
    private static final String STATE_REMOTE_CONFIRMED = "remote_confirmed";

    private enum Page {
        LANGUAGE,
        WIFI,
        WIFI_CONNECTING,
        WIFI_FAILED,
        ACCESSORIES,
        VOLUME,
        INPUT_MODE
    }

    private final AudioDeviceCallback mAudioDeviceCallback = new AudioDeviceCallback() {
        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            refreshMicrophoneStatus();
        }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
            refreshMicrophoneStatus();
        }
    };
    private final BroadcastReceiver mConnectivityReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (mCurrentPage == Page.INPUT_MODE) {
                refreshInputModeConnectionStatus();
            }
        }
    };

    private WifiNetworkAdapter mWifiNetworkAdapter;
    private ObjectAnimator mWifiRefreshAnimator;
    private Dialog mWifiPasswordDialog;
    private ExecutorService mLanguageSwitchExecutor;
    private AudioManager mAudioManager;

    private Page mCurrentPage = Page.LANGUAGE;
    private String mSelectedLanguage;
    private MainPage mSelectedInputMode = MainPage.LINE;
    private boolean mInitialized;
    private boolean mLanguageSwitchInProgress;
    private boolean mWifiRefreshing;
    private boolean mHasWifiNetworks;
    private boolean mRemoteConfirmed;
    private boolean mMicrophoneConnected;
    private boolean mAudioDeviceCallbackRegistered;
    private boolean mConnectivityReceiverRegistered;

    @Override
    protected ActivityFirstUseGuideBinding createBinding() {
        return ActivityFirstUseGuideBinding.inflate(getLayoutInflater());
    }

    @Override
    protected OnboardingPresenter createPresenter() {
        return new OnboardingPresenter(this);
    }

    @Override
    protected void initView(@Nullable Bundle savedInstanceState) {
        restoreState(savedInstanceState);
        mLanguageSwitchExecutor = Executors.newSingleThreadExecutor();
        mWifiNetworkAdapter = new WifiNetworkAdapter(this, this::onWifiNetworkSelected);
        binding.firstUseWifiList.setLayoutManager(new LinearLayoutManager(this));
        binding.firstUseWifiList.setAdapter(mWifiNetworkAdapter);
        bindClickListeners();
        renderLanguageSelection();
        renderInputModeSelection();
        renderAccessories();
        showPage(mCurrentPage);
    }

    @Override
    protected void initData() {
        presenter.init(this);
        mInitialized = true;
        startAudioDeviceMonitoring();
        onCurrentPageActivated();
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerConnectivityReceiver();
        refreshInputModeConnectionStatus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterConnectivityReceiver();
    }

    @Override
    public void onBackPressed() {
        switch (mCurrentPage) {
            case LANGUAGE:
                // The guide must not be bypassed by pressing Back before it is completed.
                moveTaskToBack(true);
                break;
            case WIFI:
                showPage(Page.LANGUAGE);
                break;
            case WIFI_CONNECTING:
                presenter.cancelWifiConnection();
                showPage(Page.WIFI);
                break;
            case WIFI_FAILED:
                showPage(Page.WIFI);
                break;
            case ACCESSORIES:
                showPage(Page.WIFI);
                break;
            case VOLUME:
                showPage(Page.ACCESSORIES);
                break;
            case INPUT_MODE:
                showPage(Page.VOLUME);
                break;
            default:
                super.onBackPressed();
                break;
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (mCurrentPage == Page.ACCESSORIES && event != null
                && event.getAction() == KeyEvent.ACTION_DOWN
                && event.getKeyCode() != KeyEvent.KEYCODE_BACK) {
            if (!mRemoteConfirmed) {
                mRemoteConfirmed = true;
                renderAccessories();
                showToast(getString(R.string.onboarding_remote_connected));
            }
            // A remote key used for this test should not trigger a second launcher action.
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void renderWifiNetworks(List<WifiNetwork> networks) {
        if (binding == null || mWifiNetworkAdapter == null) {
            return;
        }
        mWifiNetworkAdapter.submitNetworks(networks);
        mHasWifiNetworks = networks != null && !networks.isEmpty();
        if (mHasWifiNetworks) {
            binding.firstUseWifiList.setVisibility(View.VISIBLE);
            binding.firstUseWifiEmpty.setVisibility(View.GONE);
            binding.firstUseWifiLoading.setVisibility(View.GONE);
        } else if (!mWifiRefreshing) {
            showWifiEmptyState(getString(R.string.settings_no_network_message));
        }
    }

    @Override
    public void setWifiRefreshing(boolean refreshing) {
        if (binding == null) {
            return;
        }
        mWifiRefreshing = refreshing;
        binding.firstUseWifiRefresh.setEnabled(!refreshing);
        if (refreshing) {
            binding.firstUseWifiEmpty.setVisibility(View.GONE);
            binding.firstUseWifiList.setVisibility(mHasWifiNetworks ? View.VISIBLE : View.GONE);
            binding.firstUseWifiLoading.setVisibility(mHasWifiNetworks ? View.GONE : View.VISIBLE);
            startWifiRefreshAnimation();
        } else {
            stopWifiRefreshAnimation();
            binding.firstUseWifiLoading.setVisibility(View.GONE);
            if (!mHasWifiNetworks) {
                showWifiEmptyState(getString(R.string.settings_no_network_message));
            }
        }
    }

    @Override
    public void showWifiUnavailable(String message) {
        mWifiRefreshing = false;
        stopWifiRefreshAnimation();
        if (binding != null) {
            binding.firstUseWifiRefresh.setEnabled(true);
        }
        showWifiEmptyState(message);
    }

    @Override
    public void showWifiConnecting(String ssid) {
        if (binding == null) {
            return;
        }
        binding.firstUseWifiConnectingNetwork.setText(getString(
                R.string.onboarding_wifi_connecting_network, displaySsid(ssid)));
        showPage(Page.WIFI_CONNECTING);
    }

    @Override
    public void showWifiConnected(String ssid) {
        showToast(getString(R.string.settings_wifi_connected_toast, displaySsid(ssid)));
        showPage(Page.ACCESSORIES);
    }

    @Override
    public void showWifiConnectionFailed(WifiNetwork network, boolean authenticationFailure) {
        if (binding == null) {
            return;
        }
        String ssid = displaySsid(network == null ? null : network.getSsid());
        binding.firstUseWifiFailedTitle.setText(getString(R.string.onboarding_wifi_failed_title,
                ssid));
        binding.firstUseWifiFailedDescription.setText(authenticationFailure
                ? R.string.onboarding_wifi_failed_authentication
                : R.string.onboarding_wifi_failed_general);
        binding.firstUseWifiFailedRetry.setText(presenter.selectedWifiNetworkNeedsPassword()
                ? R.string.onboarding_wifi_reenter_password
                : R.string.settings_wifi_connect);
        showPage(Page.WIFI_FAILED);
    }

    @Override
    public void renderAmplifierVolume(int volumePercent, boolean muted) {
        if (binding == null) {
            return;
        }
        int safeVolume = Math.max(0, Math.min(100, volumePercent));
        int displayedVolume = muted ? 0 : safeVolume;
        binding.firstUseVolumeSeek.setProgress(displayedVolume);
        binding.firstUseVolumeValue.setText(String.valueOf(displayedVolume));
        binding.firstUseVolumeDown.setEnabled(displayedVolume > 0);
        binding.firstUseVolumeUp.setEnabled(displayedVolume < 100);
        binding.firstUseVolumeMute.setContentDescription(getString(muted
                ? R.string.main_volume_unmute : R.string.main_volume_mute));
        binding.firstUseVolumeMute.setSelected(muted);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_PAGE, mCurrentPage.name());
        outState.putString(STATE_LANGUAGE, mSelectedLanguage);
        outState.putString(STATE_SELECTED_INPUT, mSelectedInputMode.name());
        outState.putBoolean(STATE_REMOTE_CONFIRMED, mRemoteConfirmed);
    }

    @Override
    protected void onDestroy() {
        mInitialized = false;
        unregisterConnectivityReceiver();
        dismissWifiPasswordDialog();
        stopWifiRefreshAnimation();
        stopAudioDeviceMonitoring();
        if (mLanguageSwitchExecutor != null) {
            mLanguageSwitchExecutor.shutdownNow();
            mLanguageSwitchExecutor = null;
        }
        if (presenter != null) {
            presenter.destroy();
        }
        mWifiNetworkAdapter = null;
        super.onDestroy();
    }

    private void bindClickListeners() {
        binding.firstUseLanguageChinese.setOnClickListener(view -> {
            mSelectedLanguage = LocaleHelper.LANGUAGE_ZH;
            renderLanguageSelection();
        });
        binding.firstUseLanguageEnglish.setOnClickListener(view -> {
            mSelectedLanguage = LocaleHelper.LANGUAGE_EN;
            renderLanguageSelection();
        });
        binding.firstUseLanguageContinue.setOnClickListener(view -> continueFromLanguage());

        binding.firstUseWifiBack.setOnClickListener(view -> showPage(Page.LANGUAGE));
        binding.firstUseWifiRefresh.setOnClickListener(view -> presenter.refreshWifiNetworks());
        binding.firstUseWifiSkip.setOnClickListener(view -> {
            presenter.cancelWifiConnection();
            showPage(Page.ACCESSORIES);
        });

        binding.firstUseWifiConnectingCancel.setOnClickListener(view -> {
            presenter.cancelWifiConnection();
            showPage(Page.WIFI);
        });

        binding.firstUseWifiFailedRetry.setOnClickListener(view -> retryWifiConnection());
        binding.firstUseWifiFailedOtherNetwork.setOnClickListener(view -> showPage(Page.WIFI));
        binding.firstUseWifiFailedSkip.setOnClickListener(view -> showPage(Page.ACCESSORIES));

        binding.firstUseAccessoriesBack.setOnClickListener(view -> showPage(Page.WIFI));
        binding.firstUseAccessoryRemote.setOnClickListener(view ->
                showToast(getString(R.string.onboarding_remote_waiting)));
        binding.firstUseAccessoryMicrophone.setOnClickListener(view -> refreshMicrophoneStatus());
        binding.firstUseAccessoriesContinue.setOnClickListener(view -> showPage(Page.VOLUME));

        binding.firstUseVolumeBack.setOnClickListener(view -> showPage(Page.ACCESSORIES));
        binding.firstUseVolumeMute.setOnClickListener(view -> presenter.toggleAmplifierMute());
        binding.firstUseVolumeDown.setOnClickListener(view -> presenter.adjustAmplifierVolume(-1));
        binding.firstUseVolumeUp.setOnClickListener(view -> presenter.adjustAmplifierVolume(1));
        binding.firstUseVolumeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    presenter.setAmplifierVolume(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        binding.firstUseVolumeContinue.setOnClickListener(view -> showPage(Page.INPUT_MODE));

        binding.firstUseInputModeBack.setOnClickListener(view -> showPage(Page.VOLUME));
        binding.firstUseInputModeLine.setOnClickListener(view -> selectInputMode(MainPage.LINE));
        binding.firstUseInputModeMicrophone.setOnClickListener(view ->
                selectInputMode(MainPage.MICROPHONE));
        binding.firstUseInputModeOptical.setOnClickListener(view -> selectInputMode(MainPage.OPTICAL));
        binding.firstUseInputModeCoax.setOnClickListener(view -> selectInputMode(MainPage.COAX));
        binding.firstUseInputModeHdmi.setOnClickListener(view -> selectInputMode(MainPage.HDMI));
        binding.firstUseInputModeBluetooth.setOnClickListener(view ->
                selectInputMode(MainPage.BLUETOOTH));
        binding.firstUseInputModeWifi.setOnClickListener(view -> selectInputMode(MainPage.WIFI));
        binding.firstUseInputModeContinue.setOnClickListener(view -> completeGuide());
    }

    private void continueFromLanguage() {
        if (mLanguageSwitchInProgress) {
            return;
        }
        String currentLanguage = LocaleHelper.getLanguage(this);
        if (TextUtils.equals(currentLanguage, mSelectedLanguage)) {
            showPage(Page.WIFI);
            return;
        }

        if (mLanguageSwitchExecutor == null) {
            return;
        }
        mLanguageSwitchInProgress = true;
        setLanguageSelectionEnabled(false);
        final String targetLanguage = mSelectedLanguage;
        final Application application = getApplication();
        mLanguageSwitchExecutor.execute(() -> {
            LocaleHelper.setLanguage(application, targetLanguage);
            LocaleHelper.applySystemLocale(application, targetLanguage);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                LocaleHelper.applyLocale(application);
                mCurrentPage = Page.WIFI;
                recreate();
            });
        });
    }

    private void onWifiNetworkSelected(WifiNetwork network) {
        if (network == null || network.isConnected()) {
            return;
        }
        presenter.selectWifiNetwork(network);
        if (presenter.selectedWifiNetworkNeedsPassword()) {
            showWifiPasswordDialog(network);
        }
    }

    private void retryWifiConnection() {
        WifiNetwork network = presenter.getSelectedWifiNetwork();
        if (network == null) {
            showPage(Page.WIFI);
            return;
        }
        if (presenter.selectedWifiNetworkNeedsPassword()) {
            showWifiPasswordDialog(network);
        } else {
            presenter.connectSelectedNetwork(null);
        }
    }

    private void showWifiPasswordDialog(WifiNetwork network) {
        if (network == null || isFinishing() || isDestroyed()) {
            return;
        }
        dismissWifiPasswordDialog();
        Dialog dialog = new Dialog(this);
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
            presenter.connectSelectedNetwork(password);
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

    private void dismissWifiPasswordDialog() {
        if (mWifiPasswordDialog != null) {
            mWifiPasswordDialog.dismiss();
            mWifiPasswordDialog = null;
        }
    }

    private void completeGuide() {
        presenter.selectInputMode(mSelectedInputMode);
        FirstUseGuideStore.markCompleted(this);
        Intent intent = new Intent(this, MainActivity.class)
                .putExtra(MainActivity.EXTRA_INITIAL_MODE, mSelectedInputMode.name())
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private void selectInputMode(MainPage page) {
        if (page == null) {
            return;
        }
        mSelectedInputMode = page;
        renderInputModeSelection();
    }

    private void showPage(Page page) {
        mCurrentPage = page == null ? Page.LANGUAGE : page;
        binding.firstUsePageLanguage.setVisibility(mCurrentPage == Page.LANGUAGE
                ? View.VISIBLE : View.GONE);
        binding.firstUsePageWifi.setVisibility(mCurrentPage == Page.WIFI
                ? View.VISIBLE : View.GONE);
        binding.firstUsePageWifiConnecting.setVisibility(mCurrentPage == Page.WIFI_CONNECTING
                ? View.VISIBLE : View.GONE);
        binding.firstUsePageWifiFailed.setVisibility(mCurrentPage == Page.WIFI_FAILED
                ? View.VISIBLE : View.GONE);
        binding.firstUsePageAccessories.setVisibility(mCurrentPage == Page.ACCESSORIES
                ? View.VISIBLE : View.GONE);
        binding.firstUsePageVolume.setVisibility(mCurrentPage == Page.VOLUME
                ? View.VISIBLE : View.GONE);
        binding.firstUsePageInputMode.setVisibility(mCurrentPage == Page.INPUT_MODE
                ? View.VISIBLE : View.GONE);
        if (mInitialized) {
            onCurrentPageActivated();
        }
    }

    private void onCurrentPageActivated() {
        switch (mCurrentPage) {
            case WIFI:
                presenter.startWifiSetup();
                break;
            case ACCESSORIES:
                refreshMicrophoneStatus();
                renderAccessories();
                break;
            case VOLUME:
                renderAmplifierVolume(presenter.getAmplifierVolume(), presenter.isAmplifierMuted());
                break;
            case INPUT_MODE:
                renderInputModeSelection();
                break;
            default:
                break;
        }
    }

    private void renderLanguageSelection() {
        boolean chineseSelected = LocaleHelper.LANGUAGE_ZH.equals(mSelectedLanguage);
        updateLanguageOptionSelection(binding.firstUseLanguageChinese, chineseSelected);
        updateLanguageOptionSelection(binding.firstUseLanguageEnglish, !chineseSelected);
    }

    /**
     * settings_menu_item_background renders its selected visual from state_selected, so update
     * that drawable state explicitly whenever the guide language changes.
     */
    private void updateLanguageOptionSelection(View option, boolean selected) {
        if (option.isSelected() != selected) {
            option.setSelected(selected);
            option.refreshDrawableState();
        }
    }

    private void setLanguageSelectionEnabled(boolean enabled) {
        binding.firstUseLanguageChinese.setEnabled(enabled);
        binding.firstUseLanguageEnglish.setEnabled(enabled);
        binding.firstUseLanguageContinue.setEnabled(enabled);
        float alpha = enabled ? 1f : 0.55f;
        binding.firstUseLanguageChinese.setAlpha(alpha);
        binding.firstUseLanguageEnglish.setAlpha(alpha);
        binding.firstUseLanguageContinue.setAlpha(alpha);
    }

    private void renderInputModeSelection() {
        updateInputModeCard(binding.firstUseInputModeLine, mSelectedInputMode == MainPage.LINE,
                R.drawable.card_mode_line, R.drawable.card_mode_line_selected);
        updateInputModeCard(binding.firstUseInputModeMicrophone,
                mSelectedInputMode == MainPage.MICROPHONE, R.drawable.card_mode_microphone,
                R.drawable.card_mode_microphone_selected);
        updateInputModeCard(binding.firstUseInputModeOptical,
                mSelectedInputMode == MainPage.OPTICAL, R.drawable.card_mode_optical,
                R.drawable.card_mode_optical_selected);
        updateInputModeCard(binding.firstUseInputModeCoax, mSelectedInputMode == MainPage.COAX,
                R.drawable.card_mode_coax, R.drawable.card_mode_coax_selected);
        updateInputModeCard(binding.firstUseInputModeHdmi, mSelectedInputMode == MainPage.HDMI,
                R.drawable.card_mode_hdmi, R.drawable.card_mode_hdmi_selected);
        updateInputModeCard(binding.firstUseInputModeBluetooth,
                mSelectedInputMode == MainPage.BLUETOOTH, R.drawable.card_mode_bluetooth,
                R.drawable.card_mode_bluetooth_selected);
        updateInputModeCard(binding.firstUseInputModeWifi, mSelectedInputMode == MainPage.WIFI,
                R.drawable.card_mode_wifi, R.drawable.card_mode_wifi_selected);
        refreshInputModeConnectionStatus();
    }

    private void updateInputModeCard(android.widget.ImageView view, boolean selected,
            int normalResId, int selectedResId) {
        view.setSelected(selected);
        view.setImageResource(selected ? selectedResId : normalResId);
    }

    private void refreshInputModeConnectionStatus() {
        if (binding == null) {
            return;
        }
        boolean bluetoothConnected = BluetoothMediaController.getInstance()
                .isBluetoothAudioConnected();
        boolean wifiConnected = !TextUtils.isEmpty(WifiConnectionStatus.getConnectedSsid(this));
        binding.firstUseInputModeBluetoothStatus.setText(bluetoothConnected
                ? R.string.input_mode_status_connected : R.string.input_mode_status_disconnected);
        binding.firstUseInputModeBluetoothStatus.setTextColor(getColor(bluetoothConnected
                ? R.color.status_connected : R.color.text_color2));
        binding.firstUseInputModeWifiStatus.setText(wifiConnected
                ? R.string.input_mode_status_connected : R.string.input_mode_status_disconnected);
        binding.firstUseInputModeWifiStatus.setTextColor(getColor(wifiConnected
                ? R.color.status_connected : R.color.text_color2));
    }

    private void registerConnectivityReceiver() {
        if (mConnectivityReceiverRegistered) {
            return;
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction(android.net.wifi.WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(android.net.wifi.WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothMediaController.ACTION_A2DP_SINK_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        registerReceiver(mConnectivityReceiver, filter);
        mConnectivityReceiverRegistered = true;
    }

    private void unregisterConnectivityReceiver() {
        if (!mConnectivityReceiverRegistered) {
            return;
        }
        try {
            unregisterReceiver(mConnectivityReceiver);
        } catch (IllegalArgumentException ignored) {
            // The system may already have unregistered the receiver during teardown.
        }
        mConnectivityReceiverRegistered = false;
    }

    private void startWifiRefreshAnimation() {
        if (binding == null) {
            return;
        }
        if (mWifiRefreshAnimator == null) {
            mWifiRefreshAnimator = ObjectAnimator.ofFloat(binding.firstUseWifiRefresh,
                    View.ROTATION, 0f, 360f);
            mWifiRefreshAnimator.setDuration(900L);
            mWifiRefreshAnimator.setRepeatCount(ObjectAnimator.INFINITE);
        }
        if (!mWifiRefreshAnimator.isStarted()) {
            mWifiRefreshAnimator.start();
        }
    }

    private void stopWifiRefreshAnimation() {
        if (mWifiRefreshAnimator != null) {
            mWifiRefreshAnimator.cancel();
            mWifiRefreshAnimator = null;
        }
        if (binding != null) {
            binding.firstUseWifiRefresh.setRotation(0f);
        }
    }

    private void showWifiEmptyState(String message) {
        if (binding == null) {
            return;
        }
        binding.firstUseWifiList.setVisibility(View.GONE);
        binding.firstUseWifiLoading.setVisibility(View.GONE);
        binding.firstUseWifiEmptyText.setText(message);
        binding.firstUseWifiEmpty.setVisibility(View.VISIBLE);
    }

    private void startAudioDeviceMonitoring() {
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (mAudioManager != null && !mAudioDeviceCallbackRegistered) {
            mAudioManager.registerAudioDeviceCallback(mAudioDeviceCallback, null);
            mAudioDeviceCallbackRegistered = true;
        }
        refreshMicrophoneStatus();
    }

    private void stopAudioDeviceMonitoring() {
        if (mAudioDeviceCallbackRegistered && mAudioManager != null) {
            mAudioManager.unregisterAudioDeviceCallback(mAudioDeviceCallback);
        }
        mAudioDeviceCallbackRegistered = false;
        mAudioManager = null;
    }

    private void refreshMicrophoneStatus() {
        mMicrophoneConnected = isExternalMicrophoneConnected();
        if (binding != null && mCurrentPage == Page.ACCESSORIES) {
            renderAccessories();
        }
    }

    private boolean isExternalMicrophoneConnected() {
        if (mAudioManager == null) {
            return false;
        }
        AudioDeviceInfo[] devices = mAudioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);
        for (AudioDeviceInfo device : devices) {
            switch (device.getType()) {
                case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
                case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                case AudioDeviceInfo.TYPE_USB_ACCESSORY:
                case AudioDeviceInfo.TYPE_USB_DEVICE:
                case AudioDeviceInfo.TYPE_USB_HEADSET:
                    return true;
                default:
                    break;
            }
        }
        return false;
    }

    private void renderAccessories() {
        if (binding == null) {
            return;
        }
        binding.firstUseAccessoryRemote.setSelected(mRemoteConfirmed);
        binding.firstUseAccessoryRemoteStatus.setText(mRemoteConfirmed
                ? R.string.onboarding_remote_connected : R.string.onboarding_remote_waiting);
        binding.firstUseAccessoryRemoteStatus.setBackgroundResource(mRemoteConfirmed
                ? R.drawable.onboarding_status_connected : R.drawable.onboarding_status_pending);
        binding.firstUseAccessoryMicrophone.setSelected(mMicrophoneConnected);
        binding.firstUseAccessoryMicrophoneStatus.setText(mMicrophoneConnected
                ? R.string.onboarding_microphone_connected
                : R.string.onboarding_microphone_waiting);
        binding.firstUseAccessoryMicrophoneStatus.setBackgroundResource(mMicrophoneConnected
                ? R.drawable.onboarding_status_connected : R.drawable.onboarding_status_pending);
    }

    private void restoreState(@Nullable Bundle state) {
        mSelectedLanguage = LocaleHelper.getLanguage(this);
        if (state == null) {
            return;
        }
        String pageName = state.getString(STATE_PAGE);
        if (!TextUtils.isEmpty(pageName)) {
            try {
                mCurrentPage = Page.valueOf(pageName);
            } catch (IllegalArgumentException ignored) {
                mCurrentPage = Page.LANGUAGE;
            }
        }
        String language = state.getString(STATE_LANGUAGE);
        if (LocaleHelper.LANGUAGE_EN.equals(language) || LocaleHelper.LANGUAGE_ZH.equals(language)) {
            mSelectedLanguage = language;
        }
        String selectedInput = state.getString(STATE_SELECTED_INPUT);
        if (!TextUtils.isEmpty(selectedInput)) {
            try {
                MainPage page = MainPage.valueOf(selectedInput);
                if (isSelectableInputMode(page)) {
                    mSelectedInputMode = page;
                }
            } catch (IllegalArgumentException ignored) {
                mSelectedInputMode = MainPage.LINE;
            }
        }
        mRemoteConfirmed = state.getBoolean(STATE_REMOTE_CONFIRMED, false);
    }

    private boolean isSelectableInputMode(MainPage page) {
        return page == MainPage.LINE || page == MainPage.MICROPHONE || page == MainPage.OPTICAL
                || page == MainPage.COAX || page == MainPage.HDMI || page == MainPage.BLUETOOTH
                || page == MainPage.WIFI;
    }

    private String displaySsid(String ssid) {
        return TextUtils.isEmpty(ssid) ? getString(R.string.settings_wifi_network) : ssid;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
