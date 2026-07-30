package com.hivi.launcher.main.ui;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.hivi.launcher.R;
import com.hivi.launcher.account.model.AuthorizedUserInfo;
import com.hivi.launcher.account.ui.AuthorizationDialog;
import com.hivi.launcher.base.BaseActivity;
import com.hivi.launcher.databinding.ActivityMainBinding;
import com.hivi.launcher.main.model.MainPage;
import com.hivi.launcher.main.presenter.MainPresenter;
import com.hivi.launcher.music.model.BluetoothMediaController;
import com.hivi.launcher.bluetooth.ui.BluetoothFragment;
import com.hivi.launcher.coax.ui.CoaxFragment;
import com.hivi.launcher.hdmi.ui.HdmiFragment;
import com.hivi.launcher.line.ui.LineFragment;
import com.hivi.launcher.microphone.ui.MicrophoneFragment;
import com.hivi.launcher.optical.ui.OpticalFragment;
import com.hivi.launcher.settings.ui.SettingsFragment;
import com.hivi.launcher.utils.network.AuthorizationStore;
import com.hivi.launcher.wifi.ui.WifiFragment;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends BaseActivity<ActivityMainBinding, MainPresenter>
        implements MainView {
    private static final int AVATAR_CONNECT_TIMEOUT_MS = 8_000;
    private static final int AVATAR_READ_TIMEOUT_MS = 10_000;
    private static final int AVATAR_MAX_SIZE_PX = 512;
    private static final long PAGE_TRANSITION_DURATION_MS = 240L;
    private static final float PAGE_TRANSITION_OFFSET_DP = 28f;
    private static final float HOME_PAGE_DIMMED_ALPHA = 0.65f;
    private AuthorizationDialog mAuthorizationDialog;
    private VolumeDialog mVolumeDialog;
    private InputModeDialog mInputModeDialog;
    private InputModeAdapter mInputModeAdapter;
    private final ExecutorService mAccountAvatarExecutor = Executors.newSingleThreadExecutor();
    private final DecelerateInterpolator mPageTransitionInterpolator =
            new DecelerateInterpolator();
    private String mAccountAvatarUrl = "";
    private int mPageTransitionGeneration;
    private boolean mBluetoothConnected;
    private boolean mWifiConnected;

    private final BroadcastReceiver mSystemReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (presenter == null || intent == null) {
                return;
            }
            if (isBluetoothStateIntent(intent.getAction())) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                int connectionState = intent.getIntExtra(BluetoothProfile.EXTRA_STATE,
                        intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1));
                presenter.onBluetoothStateChanged(device, intent.getAction(), connectionState);
            } else {
                presenter.onSystemStateChanged();
            }
        }
    };

    @Override
    protected ActivityMainBinding createBinding() {
        return ActivityMainBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MainPresenter createPresenter() {
        return new MainPresenter(this, this);
    }

    @Override
    protected void initView(@Nullable Bundle savedInstanceState) {
        binding.accountImg.setClipToOutline(true);
        setupInputModeCarousel();
        bindMainClickListeners();
    }

    @Override
    protected void initData() {
        presenter.init();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyLocalizedTexts();
        registerSystemReceiver();
        presenter.onSystemStateChanged();
        presenter.startTicker();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiverQuietly();
        presenter.stopTicker();
    }

    @Override
    public void updateClock(String time, String date) {
        // The information cards were replaced by the input-mode carousel.
    }

    @Override
    public void updateConnectivity(String wifiLabel, boolean bluetoothConnected,
            String bluetoothDeviceName) {
        if (binding == null) {
            return;
        }
        mBluetoothConnected = bluetoothConnected;
        mWifiConnected = !TextUtils.isEmpty(wifiLabel)
                && !TextUtils.equals(wifiLabel, getString(R.string.main_disconnected));
        binding.wifiText.setText(formatWifiStatus(wifiLabel));
        binding.bluetoothText.setText(bluetoothConnected
                ? (android.text.TextUtils.isEmpty(bluetoothDeviceName)
                        ? getString(R.string.main_bluetooth_default) : bluetoothDeviceName)
                : getString(R.string.main_disconnected));
        if (mInputModeAdapter != null
                && mInputModeAdapter.updateConnectivityState(bluetoothConnected, wifiLabel)) {
            scrollToSelectedMode();
        }
        if (mInputModeDialog != null) {
            mInputModeDialog.updateState(mInputModeAdapter == null
                            ? null : mInputModeAdapter.getSelectedPage(),
                    mBluetoothConnected, mWifiConnected);
        }
    }

    public void updateWifiConnectionStatus(String ssid) {
        if (presenter != null) {
            presenter.updateConnectivity(ssid);
        }
    }

    @Override
    public void updateVolume(int volumePercent) {
        if (binding == null) {
            return;
        }
        binding.bottomNavigationVolume.setText(getString(
                R.string.main_bottom_navigation_volume_format, volumePercent));
        if (mVolumeDialog != null) {
            mVolumeDialog.updateVolume(volumePercent);
        }
    }

    @Override
    public void updateVolumeMuted(boolean muted) {
        if (mVolumeDialog != null) {
            mVolumeDialog.updateMuted(muted);
        }
    }

    @Override
    public void showAuthorization() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        if (mAuthorizationDialog == null) {
            mAuthorizationDialog = new AuthorizationDialog(this,
                    new AuthorizationDialog.OnAuthorizationChangedListener() {
                        @Override
                        public void onAuthorizationChanged() {
                            if (!isFinishing() && !isDestroyed()) {
                                updateAccountText();
                            }
                        }
                    });
        }
        mAuthorizationDialog.show();
    }

    @Override
    public void showVolumeDialog(int volumePercent, boolean muted) {
        if (isFinishing() || isDestroyed() || binding == null) {
            return;
        }
        if (mVolumeDialog == null) {
            mVolumeDialog = new VolumeDialog(this, new VolumeDialog.Listener() {
                @Override
                public void onVolumeAdjusted(int direction) {
                    presenter.adjustVolume(direction);
                }

                @Override
                public void onVolumeChanged(int volumePercent) {
                    presenter.setVolumePercent(volumePercent);
                }

                @Override
                public void onMuteToggleRequested() {
                    presenter.toggleVolumeMute();
                }

                @Override
                public void onDialogDismissed() {
                    if (binding != null) {
                        binding.bottomNavigationVolume.setSelected(false);
                    }
                }
            });
        }
        binding.bottomNavigationVolume.setSelected(true);
        mVolumeDialog.show(volumePercent, muted);
    }

    @Override
    public void showPage(MainPage page) {
        if (binding == null) {
            return;
        }
        View fragmentContainer = binding.fragmentContainer;
        boolean homePageVisible = fragmentContainer.getVisibility() != View.VISIBLE;
        int transitionGeneration = ++mPageTransitionGeneration;
        cancelPageAnimations();

        if (homePageVisible) {
            int transitionOffset = dp(PAGE_TRANSITION_OFFSET_DP);
            binding.pageBody.setTranslationX(0f);
            binding.pageBody.setAlpha(1f);
            binding.pageBody.animate()
                    .translationX(-transitionOffset)
                    .alpha(HOME_PAGE_DIMMED_ALPHA)
                    .setDuration(PAGE_TRANSITION_DURATION_MS)
                    .setInterpolator(mPageTransitionInterpolator)
                    .start();

            fragmentContainer.setVisibility(View.VISIBLE);
            fragmentContainer.setTranslationX(transitionOffset);
            fragmentContainer.setAlpha(0f);
        }
        getFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, createPageFragment(page))
                .commit();

        if (homePageVisible) {
            fragmentContainer.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(PAGE_TRANSITION_DURATION_MS)
                    .setInterpolator(mPageTransitionInterpolator)
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            if (transitionGeneration != mPageTransitionGeneration) {
                                return;
                            }
                            fragmentContainer.setTranslationX(0f);
                            fragmentContainer.setAlpha(1f);
                        }
                    })
                    .start();
        }
    }

    @Override
    public void showHomePage() {
        if (binding == null) {
            return;
        }
        updateModeTextFromSelectedInputMode();
        final int transitionGeneration = ++mPageTransitionGeneration;
        final View fragmentContainer = binding.fragmentContainer;
        Fragment fragment = getFragmentManager().findFragmentById(R.id.fragment_container);
        cancelPageAnimations();
        if (fragment == null || fragmentContainer.getVisibility() != View.VISIBLE) {
            if (fragment != null) {
                getFragmentManager().beginTransaction().remove(fragment).commit();
            }
            fragmentContainer.setVisibility(View.GONE);
            fragmentContainer.setTranslationX(0f);
            fragmentContainer.setAlpha(1f);
            binding.pageBody.setTranslationX(0f);
            binding.pageBody.setAlpha(1f);
            return;
        }

        int transitionOffset = dp(PAGE_TRANSITION_OFFSET_DP);
        binding.pageBody.setTranslationX(-transitionOffset);
        binding.pageBody.setAlpha(HOME_PAGE_DIMMED_ALPHA);
        binding.pageBody.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(PAGE_TRANSITION_DURATION_MS)
                .setInterpolator(mPageTransitionInterpolator)
                .start();

        fragmentContainer.animate()
                .translationX(transitionOffset)
                .alpha(0f)
                .setDuration(PAGE_TRANSITION_DURATION_MS)
                .setInterpolator(mPageTransitionInterpolator)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        if (transitionGeneration != mPageTransitionGeneration || binding == null) {
                            return;
                        }
                        Fragment currentFragment = getFragmentManager()
                                .findFragmentById(R.id.fragment_container);
                        if (currentFragment != null) {
                            getFragmentManager().beginTransaction().remove(currentFragment).commit();
                        }
                        fragmentContainer.setVisibility(View.GONE);
                        fragmentContainer.setTranslationX(0f);
                        fragmentContainer.setAlpha(1f);
                    }
                })
                .start();
    }

    @Override
    protected void onDestroy() {
        if (mVolumeDialog != null) {
            mVolumeDialog.dismiss();
            mVolumeDialog = null;
        }
        if (mAuthorizationDialog != null) {
            mAuthorizationDialog.release();
            mAuthorizationDialog = null;
        }
        if (mInputModeDialog != null) {
            mInputModeDialog.dismiss();
            mInputModeDialog = null;
        }
        mAccountAvatarExecutor.shutdownNow();
        super.onDestroy();
    }

    private void applyLocalizedTexts() {
        binding.wifiText.setText(R.string.main_disconnected);
        binding.bluetoothText.setText(R.string.main_disconnected);
        updateModeTextFromSelectedInputMode();
        updateAccountText();
    }

    private void bindMainClickListeners() {
        binding.authAccount.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                presenter.showAuthorizationDialog();
            }
        });
        binding.modeText.setOnClickListener(view -> showInputModeDialog());
        binding.bottomNavigationBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                presenter.onBottomNavigationBackClicked();
            }
        });
        binding.bottomNavigationHome.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                presenter.onBottomNavigationHomeClicked();
            }
        });
        binding.bottomNavigationBackground.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                presenter.onBottomNavigationRecentsClicked();
            }
        });
        binding.bottomNavigationVolume.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                presenter.onBottomNavigationVolumeClicked();
            }
        });
        binding.bottomNavigationApps.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                presenter.onBottomNavigationAppsClicked();
            }
        });
        binding.bottomNavigationSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                presenter.onBottomNavigationSettingsClicked();
            }
        });
    }

    private void updateAccountText() {
        if (binding == null) {
            return;
        }
        if (!AuthorizationStore.hasToken(this)) {
            binding.accountText.setText(R.string.auth_please_authorize);
            binding.accountText.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    0, 0, R.drawable.main_account_indicator, 0);
            binding.accountImg.setVisibility(View.GONE);
            loadAccountAvatar("");
            return;
        }
        binding.accountImg.setVisibility(View.VISIBLE);
        binding.accountText.setCompoundDrawablesRelativeWithIntrinsicBounds(
                0, 0, R.drawable.main_account_indicator_connected, 0);
        String accountName = AuthorizationStore.getAccountName(this);
        if (TextUtils.isEmpty(accountName)) {
            binding.accountText.setText(R.string.auth_authorized_fallback);
        } else {
            binding.accountText.setText(accountName);
        }
        AuthorizedUserInfo userInfo = AuthorizationStore.getUserInfo(this);
        loadAccountAvatar(userInfo == null ? "" : userInfo.getAvatarUrl());
    }

    private void loadAccountAvatar(String avatarUrl) {
        String normalizedAvatarUrl = avatarUrl == null ? "" : avatarUrl.trim();
        if (TextUtils.equals(mAccountAvatarUrl, normalizedAvatarUrl)) {
            return;
        }
        mAccountAvatarUrl = normalizedAvatarUrl;
        binding.accountImg.setImageBitmap(null);
        if (TextUtils.isEmpty(normalizedAvatarUrl)) {
            return;
        }

        final String requestedAvatarUrl = normalizedAvatarUrl;
        mAccountAvatarExecutor.execute(() -> {
            Bitmap avatarBitmap = downloadAccountAvatar(requestedAvatarUrl);
            runOnUiThread(() -> {
                if (binding == null || isFinishing() || isDestroyed()
                        || !TextUtils.equals(mAccountAvatarUrl, requestedAvatarUrl)
                        || avatarBitmap == null) {
                    return;
                }
                binding.accountImg.setImageBitmap(avatarBitmap);
            });
        });
    }

    private Bitmap downloadAccountAvatar(String avatarUrl) {
        Uri uri = Uri.parse(avatarUrl);
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            return null;
        }
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            decodeAccountAvatar(avatarUrl, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null;
            }

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = calculateAvatarInSampleSize(bounds);
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return decodeAccountAvatar(avatarUrl, options);
        } catch (IOException ignored) {
            return null;
        }
    }

    private Bitmap decodeAccountAvatar(String avatarUrl, BitmapFactory.Options options)
            throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(avatarUrl).openConnection();
        connection.setConnectTimeout(AVATAR_CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(AVATAR_READ_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(true);
        try {
            int responseCode = connection.getResponseCode();
            if (responseCode < HttpURLConnection.HTTP_OK
                    || responseCode >= HttpURLConnection.HTTP_MULT_CHOICE) {
                throw new IOException("avatar response code=" + responseCode);
            }
            try (InputStream inputStream = connection.getInputStream()) {
                return BitmapFactory.decodeStream(inputStream, null, options);
            }
        } finally {
            connection.disconnect();
        }
    }

    private int calculateAvatarInSampleSize(BitmapFactory.Options options) {
        int sampleSize = 1;
        while (options.outWidth / sampleSize > AVATAR_MAX_SIZE_PX
                || options.outHeight / sampleSize > AVATAR_MAX_SIZE_PX) {
            sampleSize *= 2;
        }
        return sampleSize;
    }

    private void setupInputModeCarousel() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(this,
                RecyclerView.HORIZONTAL, false);
        binding.cardsRow.setLayoutManager(layoutManager);
        binding.cardsRow.setHasFixedSize(true);
        binding.cardsRow.setItemAnimator(null);
        mInputModeAdapter = new InputModeAdapter(this,
                new InputModeAdapter.OnModeSelectedListener() {
                    @Override
                    public void onModeSelected(int topLabelResId) {
                        updateModeText(topLabelResId);
                    }
                }, new InputModeAdapter.OnModeClickListener() {
                    @Override
                    public void onModeClicked(MainPage page) {
                        presenter.onInputModeClicked(page);
                    }
                });
        binding.cardsRow.setAdapter(mInputModeAdapter);
        new LinearSnapHelper().attachToRecyclerView(binding.cardsRow);
    }

    private void updateModeText(int topLabelResId) {
        binding.modeText.setText(topLabelResId == 0 ? R.string.select_mode : topLabelResId);
    }

    private void updateModeTextFromSelectedInputMode() {
        updateModeText(mInputModeAdapter == null
                ? 0 : mInputModeAdapter.getSelectedModeTopLabelResId());
    }

    private void showInputModeDialog() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        if (mInputModeDialog == null) {
            mInputModeDialog = new InputModeDialog(this, new InputModeDialog.Listener() {
                @Override
                public void onModeSelected(MainPage page) {
                    if (presenter != null) {
                        presenter.onInputModeClicked(page);
                    }
                }
            });
        }
        mInputModeDialog.show(mInputModeAdapter == null
                        ? null : mInputModeAdapter.getSelectedPage(),
                mBluetoothConnected, mWifiConnected);
    }

    private void cancelPageAnimations() {
        binding.pageBody.animate().cancel();
        binding.fragmentContainer.animate().cancel();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void scrollToSelectedMode() {
        final int selectedPosition = mInputModeAdapter.getSelectedPosition();
        if (selectedPosition == RecyclerView.NO_POSITION) {
            return;
        }
        binding.cardsRow.post(new Runnable() {
            @Override
            public void run() {
                RecyclerView.LayoutManager layoutManager = binding.cardsRow.getLayoutManager();
                if (layoutManager instanceof LinearLayoutManager) {
                    ((LinearLayoutManager) layoutManager).scrollToPositionWithOffset(
                            selectedPosition, binding.cardsRow.getPaddingLeft());
                }
            }
        });
    }

    private String formatWifiStatus(String wifiLabel) {
        if (android.text.TextUtils.isEmpty(wifiLabel)
                || android.text.TextUtils.equals(wifiLabel,
                getString(R.string.main_disconnected))) {
            return getString(R.string.main_disconnected);
        }
        return getString(R.string.main_wifi_connected_format, wifiLabel);
    }

    private Fragment createPageFragment(MainPage page) {
        switch (page) {
            case LINE:
                return new LineFragment();
            case MICROPHONE:
                return new MicrophoneFragment();
            case OPTICAL:
                return new OpticalFragment();
            case COAX:
                return new CoaxFragment();
            case HDMI:
                return new HdmiFragment();
            case BLUETOOTH:
                return new BluetoothFragment();
            case WIFI:
                return new WifiFragment();
            case SETTINGS:
                return new SettingsFragment();
            default:
                throw new IllegalArgumentException("Unsupported page: " + page);
        }
    }

    private void registerSystemReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(android.net.wifi.WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(android.net.wifi.WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothMediaController.ACTION_A2DP_SINK_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction(MainPresenter.ACTION_VOLUME_CHANGED);
        registerReceiver(mSystemReceiver, filter);
    }

    private void unregisterReceiverQuietly() {
        try {
            unregisterReceiver(mSystemReceiver);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private boolean isBluetoothStateIntent(String action) {
        return BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)
                || BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED.equals(action)
                || BluetoothMediaController.ACTION_A2DP_SINK_CONNECTION_STATE_CHANGED.equals(action)
                || BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)
                || BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action);
    }

}
