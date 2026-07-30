package com.hivi.launcher.main.ui;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.hivi.launcher.R;
import com.hivi.launcher.audio.AudioRouteController;
import com.hivi.launcher.account.model.AuthorizedUserInfo;
import com.hivi.launcher.account.ui.AuthorizationDialog;
import com.hivi.launcher.ai.ui.AiConversationFragment;
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
    private static final String TAG = "MainActivity";
    private static final int AVATAR_CONNECT_TIMEOUT_MS = 8_000;
    private static final int AVATAR_READ_TIMEOUT_MS = 10_000;
    private static final int AVATAR_MAX_SIZE_PX = 512;
    private static final long PAGE_TRANSITION_DURATION_MS = 240L;
    private static final float PAGE_TRANSITION_OFFSET_DP = 28f;
    private static final float HOME_PAGE_DIMMED_ALPHA = 0.65f;
    private static final long SYSTEM_MUSIC_VOLUME_CHECK_INTERVAL_MS = 10_000L;
    private AuthorizationDialog mAuthorizationDialog;
    private VolumeDialog mVolumeDialog;
    private InputModeDialog mInputModeDialog;
    private InputModeAdapter mInputModeAdapter;
    private final ExecutorService mAccountAvatarExecutor = Executors.newSingleThreadExecutor();
    private final Handler mSystemVolumeHandler = new Handler(Looper.getMainLooper());
    private final DecelerateInterpolator mPageTransitionInterpolator =
            new DecelerateInterpolator();
    private String mAccountAvatarUrl = "";
    private int mPageTransitionGeneration;
    private int mAiChatEntryTransitionGeneration;
    private boolean mBluetoothConnected;
    private boolean mWifiConnected;
    private boolean mAiConversationBackgroundVisible;
    private boolean mHomeNavigationPending;
    private boolean mSuppressBackStackUiSync;
    private final FragmentManager.OnBackStackChangedListener mBackStackChangedListener =
            new FragmentManager.OnBackStackChangedListener() {
                @Override
                public void onBackStackChanged() {
                    if (!mSuppressBackStackUiSync) {
                        syncPageUiWithCurrentFragment();
                    }
                }
            };
    private final Runnable mSystemMusicVolumeCheck = new Runnable() {
        @Override
        public void run() {
            if (!ensureSystemMusicVolumeAtMaximum()) {
                mSystemVolumeHandler.postDelayed(this, SYSTEM_MUSIC_VOLUME_CHECK_INTERVAL_MS);
            }
        }
    };

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
        restoreSelectedInputMode();
        bindMainClickListeners();
        getFragmentManager().addOnBackStackChangedListener(mBackStackChangedListener);
        syncPageUiWithCurrentFragment();
    }

    @Override
    protected void initData() {
        startSystemMusicVolumeCheck();
        AudioRouteController.getInstance().initialize(this);
        presenter.init();
    }

    @Override
    public void onBackPressed() {
        // The launcher root remains active; content pages use the native Fragment back stack.
        navigateBack();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyLocalizedTexts();
        registerSystemReceiver();
        presenter.onSystemStateChanged();
        presenter.startTicker();
        syncPageUiWithCurrentFragment();
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
                    syncPageUiWithCurrentFragment();
                }
            });
        }
        selectBottomNavigationItem(BottomNavigationItem.VOLUME);
        mVolumeDialog.show(volumePercent, muted);
    }

    @Override
    public void showPage(MainPage page) {
        if (binding == null) {
            return;
        }
        if (mInputModeAdapter != null && mInputModeAdapter.selectMode(page)) {
            scrollToSelectedMode();
        }
        Fragment currentFragment = getFragmentManager()
                .findFragmentById(R.id.fragment_container);
        if (currentFragment instanceof AiConversationFragment && page != MainPage.AI) {
            removeAiPageBeforeNavigating();
        }
        if (isInputModePage(page)) {
            showInputModePage(page);
            return;
        }
        showStackedPage(page);
    }

    private void showInputModePage(MainPage page) {
        FragmentManager fragmentManager = getFragmentManager();
        Fragment currentFragment = fragmentManager.findFragmentById(R.id.fragment_container);
        if (isFragmentForPage(currentFragment, page)
                && fragmentManager.getBackStackEntryCount() == 1) {
            syncPageUiWithCurrentFragment();
            return;
        }
        mHomeNavigationPending = false;
        ++mPageTransitionGeneration;
        cancelPageAnimations();
        if (fragmentManager.getBackStackEntryCount() > 0) {
            popBackStackImmediately(true);
        }
        showStackedPage(page);
    }

    private void showStackedPage(MainPage page) {
        Fragment currentFragment = getFragmentManager()
                .findFragmentById(R.id.fragment_container);
        if (isFragmentForPage(currentFragment, page)) {
            syncPageUiWithCurrentFragment();
            return;
        }
        mHomeNavigationPending = false;
        selectBottomNavigationItem(page == MainPage.SETTINGS
                ? BottomNavigationItem.SETTINGS : BottomNavigationItem.BACK);
        updateLauncherBackground(page == MainPage.AI);
        View fragmentContainer = binding.fragmentContainer;
        boolean homePageVisible = fragmentContainer.getVisibility() != View.VISIBLE;
        int transitionGeneration = ++mPageTransitionGeneration;
        cancelPageAnimations();
        hideAiChatEntry();

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
                .addToBackStack(page.name())
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
    public void navigateBack() {
        if (binding == null) {
            return;
        }
        FragmentManager fragmentManager = getFragmentManager();
        int backStackEntryCount = fragmentManager.getBackStackEntryCount();
        if (backStackEntryCount == 0 || mHomeNavigationPending) {
            return;
        }
        selectBottomNavigationItem(BottomNavigationItem.BACK);
        if (backStackEntryCount == 1) {
            navigateToHome(false);
            return;
        }
        ++mPageTransitionGeneration;
        cancelPageAnimations();
        mHomeNavigationPending = false;
        fragmentManager.popBackStack();
    }

    @Override
    public void showHomePage() {
        if (binding == null) {
            return;
        }
        if (getFragmentManager().getBackStackEntryCount() == 0) {
            syncPageUiWithCurrentFragment();
            return;
        }
        navigateToHome(true);
    }

    private void navigateToHome(final boolean clearBackStack) {
        if (binding == null) {
            return;
        }
        final int transitionGeneration = ++mPageTransitionGeneration;
        final View fragmentContainer = binding.fragmentContainer;
        Fragment fragment = getFragmentManager().findFragmentById(R.id.fragment_container);
        cancelPageAnimations();
        updateLauncherBackground(false);
        showAiChatEntry();
        if (fragment == null || fragmentContainer.getVisibility() != View.VISIBLE) {
            applyHomePageUi(true);
            popBackStack(clearBackStack);
            return;
        }

        mHomeNavigationPending = true;
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
                        popBackStack(clearBackStack);
                    }
                })
                .start();
    }

    public void onAiChatBackRequested() {
        if (presenter != null) {
            presenter.onBottomNavigationBackClicked();
        }
    }

    @Override
    protected void onDestroy() {
        getFragmentManager().removeOnBackStackChangedListener(mBackStackChangedListener);
        mSystemVolumeHandler.removeCallbacks(mSystemMusicVolumeCheck);
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
        binding.aiChatEntry.setOnClickListener(view -> presenter.onAiChatEntryClicked());
        binding.bottomNavigationBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getFragmentManager().getBackStackEntryCount() > 0) {
                    selectBottomNavigationItem(BottomNavigationItem.BACK);
                }
                presenter.onBottomNavigationBackClicked();
            }
        });
        binding.bottomNavigationHome.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectBottomNavigationItem(BottomNavigationItem.HOME);
                presenter.onBottomNavigationHomeClicked();
            }
        });
        binding.bottomNavigationBackground.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectBottomNavigationItem(BottomNavigationItem.BACKGROUND);
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
                selectBottomNavigationItem(BottomNavigationItem.APPS);
                presenter.onBottomNavigationAppsClicked();
            }
        });
        binding.bottomNavigationSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectBottomNavigationItem(BottomNavigationItem.SETTINGS);
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

    private void restoreSelectedInputMode() {
        if (mInputModeAdapter != null
                && mInputModeAdapter.selectMode(
                AudioRouteController.getInstance().getSelectedMode(this))) {
            scrollToSelectedMode();
        }
    }

    private void updateModeText(int topLabelResId) {
        binding.modeText.setText(topLabelResId == 0 ? R.string.select_mode : topLabelResId);
    }

    private void updateLauncherBackground(boolean aiConversationVisible) {
        if (binding == null || mAiConversationBackgroundVisible == aiConversationVisible) {
            return;
        }
        Drawable normalBackground = new ColorDrawable(Color.rgb(54, 54, 54));
        Drawable aiConversationBackground = getDrawable(R.drawable.ai_conversation_background);
        if (aiConversationBackground == null) {
            return;
        }
        TransitionDrawable backgroundTransition = new TransitionDrawable(aiConversationVisible
                ? new Drawable[] {normalBackground, aiConversationBackground}
                : new Drawable[] {aiConversationBackground, normalBackground});
        backgroundTransition.setCrossFadeEnabled(true);
        binding.launcherRoot.setBackground(backgroundTransition);
        backgroundTransition.startTransition((int) PAGE_TRANSITION_DURATION_MS);
        mAiConversationBackgroundVisible = aiConversationVisible;
    }

    private void startSystemMusicVolumeCheck() {
        mSystemVolumeHandler.removeCallbacks(mSystemMusicVolumeCheck);
        mSystemMusicVolumeCheck.run();
    }

    private boolean ensureSystemMusicVolumeAtMaximum() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) {
            Log.w(TAG, "Unable to verify Android music volume");
            return false;
        }
        int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        if (currentVolume < maxVolume) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0);
            int updatedVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            boolean isAtMaximum = updatedVolume >= maxVolume;
            Log.i(TAG, "Android music volume raised from " + currentVolume + " to "
                    + updatedVolume + ", maximum=" + maxVolume + ", success=" + isAtMaximum);
            return isAtMaximum;
        }
        Log.i(TAG, "Android music volume already at maximum: " + maxVolume);
        return true;
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
        ++mAiChatEntryTransitionGeneration;
        binding.aiChatEntry.animate().cancel();
    }

    private void popBackStack(boolean clearBackStack) {
        FragmentManager fragmentManager = getFragmentManager();
        if (clearBackStack) {
            fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        } else {
            fragmentManager.popBackStack();
        }
    }

    private void removeAiPageBeforeNavigating() {
        if (getFragmentManager().getBackStackEntryCount() == 0) {
            return;
        }
        mHomeNavigationPending = false;
        ++mPageTransitionGeneration;
        cancelPageAnimations();
        popBackStackImmediately(false);
    }

    private void popBackStackImmediately(boolean clearBackStack) {
        FragmentManager fragmentManager = getFragmentManager();
        mSuppressBackStackUiSync = true;
        try {
            if (clearBackStack) {
                fragmentManager.popBackStackImmediate(null,
                        FragmentManager.POP_BACK_STACK_INCLUSIVE);
            } else {
                fragmentManager.popBackStackImmediate();
            }
        } finally {
            mSuppressBackStackUiSync = false;
        }
    }

    private void syncPageUiWithCurrentFragment() {
        if (binding == null) {
            return;
        }
        if (isVolumeDialogShowing()) {
            selectBottomNavigationItem(BottomNavigationItem.VOLUME);
            return;
        }
        Fragment fragment = getFragmentManager().findFragmentById(R.id.fragment_container);
        if (fragment == null) {
            boolean keepHomeTransition = mHomeNavigationPending;
            applyHomePageUi(!keepHomeTransition);
            mHomeNavigationPending = false;
            return;
        }
        mHomeNavigationPending = false;
        binding.fragmentContainer.setVisibility(View.VISIBLE);
        boolean aiConversationVisible = fragment instanceof AiConversationFragment;
        updateLauncherBackground(aiConversationVisible);
        hideAiChatEntry();
        selectBottomNavigationItem(fragment instanceof SettingsFragment
                ? BottomNavigationItem.SETTINGS : BottomNavigationItem.BACK);
    }

    private void applyHomePageUi(boolean resetPageBody) {
        updateModeTextFromSelectedInputMode();
        updateLauncherBackground(false);
        binding.fragmentContainer.setVisibility(View.GONE);
        binding.fragmentContainer.setTranslationX(0f);
        binding.fragmentContainer.setAlpha(1f);
        if (resetPageBody) {
            binding.pageBody.setTranslationX(0f);
            binding.pageBody.setAlpha(1f);
        }
        showAiChatEntry();
        selectBottomNavigationItem(BottomNavigationItem.HOME);
    }

    private void hideAiChatEntry() {
        final int transitionGeneration = ++mAiChatEntryTransitionGeneration;
        final View aiChatEntry = binding.aiChatEntry;
        aiChatEntry.animate().cancel();
        if (aiChatEntry.getVisibility() != View.VISIBLE) {
            aiChatEntry.setVisibility(View.GONE);
            aiChatEntry.setAlpha(1f);
            aiChatEntry.setTranslationX(0f);
            return;
        }
        aiChatEntry.setEnabled(false);
        aiChatEntry.animate()
                .translationX(-dp(PAGE_TRANSITION_OFFSET_DP))
                .alpha(0f)
                .setDuration(PAGE_TRANSITION_DURATION_MS)
                .setInterpolator(mPageTransitionInterpolator)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        if (binding == null
                                || transitionGeneration != mAiChatEntryTransitionGeneration) {
                            return;
                        }
                        aiChatEntry.setVisibility(View.GONE);
                        aiChatEntry.setAlpha(1f);
                        aiChatEntry.setTranslationX(0f);
                    }
                })
                .start();
    }

    private void showAiChatEntry() {
        final int transitionGeneration = ++mAiChatEntryTransitionGeneration;
        final View aiChatEntry = binding.aiChatEntry;
        aiChatEntry.animate().cancel();
        if (aiChatEntry.getVisibility() != View.VISIBLE) {
            aiChatEntry.setVisibility(View.VISIBLE);
            aiChatEntry.setTranslationX(-dp(PAGE_TRANSITION_OFFSET_DP));
            aiChatEntry.setAlpha(0f);
        }
        if (aiChatEntry.getAlpha() == 1f && aiChatEntry.getTranslationX() == 0f) {
            aiChatEntry.setEnabled(true);
            return;
        }
        aiChatEntry.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(PAGE_TRANSITION_DURATION_MS)
                .setInterpolator(mPageTransitionInterpolator)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        if (binding == null
                                || transitionGeneration != mAiChatEntryTransitionGeneration) {
                            return;
                        }
                        aiChatEntry.setEnabled(true);
                    }
                })
                .start();
    }

    private boolean isVolumeDialogShowing() {
        return mVolumeDialog != null && mVolumeDialog.isShowing();
    }

    private void selectBottomNavigationItem(BottomNavigationItem selectedItem) {
        if (binding == null) {
            return;
        }
        switch (selectedItem) {
            case BACK:
                binding.bottomNavigation.setBackgroundResource(R.drawable.back_selected);
                break;
            case HOME:
                binding.bottomNavigation.setBackgroundResource(R.drawable.home_selected);
                break;
            case BACKGROUND:
                binding.bottomNavigation.setBackgroundResource(R.drawable.background_selected);
                break;
            case VOLUME:
                binding.bottomNavigation.setBackgroundResource(R.drawable.volume_selected);
                break;
            case APPS:
                binding.bottomNavigation.setBackgroundResource(R.drawable.apps_selected);
                break;
            case SETTINGS:
                binding.bottomNavigation.setBackgroundResource(R.drawable.settings_selected);
                break;
            default:
                throw new IllegalArgumentException("Unsupported bottom navigation item: "
                        + selectedItem);
        }
    }

    private enum BottomNavigationItem {
        BACK,
        HOME,
        BACKGROUND,
        VOLUME,
        APPS,
        SETTINGS
    }

    private boolean isInputModePage(MainPage page) {
        if (page == null) {
            return false;
        }
        switch (page) {
            case LINE:
            case MICROPHONE:
            case OPTICAL:
            case COAX:
            case HDMI:
            case BLUETOOTH:
            case WIFI:
                return true;
            case SETTINGS:
            case AI:
            default:
                return false;
        }
    }

    private boolean isFragmentForPage(Fragment fragment, MainPage page) {
        if (fragment == null || page == null) {
            return false;
        }
        switch (page) {
            case LINE:
                return fragment instanceof LineFragment;
            case MICROPHONE:
                return fragment instanceof MicrophoneFragment;
            case OPTICAL:
                return fragment instanceof OpticalFragment;
            case COAX:
                return fragment instanceof CoaxFragment;
            case HDMI:
                return fragment instanceof HdmiFragment;
            case BLUETOOTH:
                return fragment instanceof BluetoothFragment;
            case WIFI:
                return fragment instanceof WifiFragment;
            case SETTINGS:
                return fragment instanceof SettingsFragment;
            case AI:
                return fragment instanceof AiConversationFragment;
            default:
                return false;
        }
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
            case AI:
                return new AiConversationFragment();
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
