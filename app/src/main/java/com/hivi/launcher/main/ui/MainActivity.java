package com.hivi.launcher.main.ui;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.app.Dialog;
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
import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import com.hivi.launcher.utils.log.AppLog;
import android.view.MotionEvent;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.hivi.launcher.R;
import com.hivi.launcher.audio.AudioRouteController;
import com.hivi.launcher.account.model.AuthorizedUserInfo;
import com.hivi.launcher.account.ui.AuthorizationDialog;
import com.hivi.launcher.ai.presenter.AiPresenter;
import com.hivi.launcher.ai.ui.AiFragment;
import com.hivi.launcher.ai.ui.AiHeadlessConversationView;
import com.hivi.launcher.ai.ui.AiListeningOverlay;
import com.hivi.launcher.ai.wakeup.AiWakeupController;
import com.hivi.launcher.base.BaseActivity;
import com.hivi.launcher.customview.FrostedTextView;
import com.hivi.launcher.customview.FlipLayout;
import com.hivi.launcher.databinding.ActivityMainBinding;
import com.hivi.launcher.main.model.MainPage;
import com.hivi.launcher.main.presenter.MainPresenter;
import com.hivi.launcher.music.model.BluetoothMediaController;
import com.hivi.launcher.onboarding.model.FirstUseGuideStore;
import com.hivi.launcher.onboarding.ui.FirstUseGuideActivity;
import com.hivi.launcher.bluetooth.ui.BluetoothFragment;
import com.hivi.launcher.coax.ui.CoaxFragment;
import com.hivi.launcher.hdmi.ui.HdmiFragment;
import com.hivi.launcher.line.ui.LineFragment;
import com.hivi.launcher.microphone.ui.MicrophoneFragment;
import com.hivi.launcher.optical.ui.OpticalFragment;
import com.hivi.launcher.settings.ui.SettingsFragment;
import com.hivi.launcher.settings.model.ScreenSaverSettings;
import com.hivi.launcher.settings.model.SettingsModel;
import com.hivi.launcher.settings.ui.SystemUpdateSuccessDialog;
import com.hivi.launcher.systemapps.ui.SystemAppsFragment;
import com.hivi.launcher.update.SystemUpdateInstallReceiver;
import com.hivi.launcher.utils.LocaleHelper;
import com.hivi.launcher.utils.network.AuthorizationStore;
import com.hivi.launcher.wifi.ui.WifiFragment;
import com.nlf.calendar.Lunar;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import org.json.JSONObject;

public class MainActivity extends BaseActivity<ActivityMainBinding, MainPresenter>
        implements MainView, AiWakeupController.Navigator {
    private static final String TAG = "MainActivity";
    private static final String WEATHER_LOCATION_TAG = "WeatherLocation";
    public static final String EXTRA_INITIAL_MODE =
            "com.hivi.launcher.main.ui.MainActivity.initial_mode";
    private static final int AVATAR_CONNECT_TIMEOUT_MS = 8_000;
    private static final int AVATAR_READ_TIMEOUT_MS = 10_000;
    private static final int AVATAR_MAX_SIZE_PX = 512;
    private static final long PAGE_TRANSITION_DURATION_MS = 240L;
    private static final float PAGE_TRANSITION_OFFSET_DP = 28f;
    private static final float HOME_PAGE_DIMMED_ALPHA = 0.65f;
    private static final long SYSTEM_MUSIC_VOLUME_CHECK_INTERVAL_MS = 10_000L;
    private static final long SCREEN_SAVER_MINUTE_MS = 60_000L;
    private static final long WEATHER_LOCATION_TIMEOUT_MS = 10_000L;
    private static final int REQUEST_WEATHER_LOCATION = 101;
    private static final boolean USE_TEST_WEATHER_LOCATION = true;
    private static final double TEST_WEATHER_LATITUDE = 43.8256;
    private static final double TEST_WEATHER_LONGITUDE = 87.6168;
    private AuthorizationDialog mAuthorizationDialog;
    private VolumeDialog mVolumeDialog;
    private InputModeDialog mInputModeDialog;
    private InputModeAdapter mInputModeAdapter;
    private final ExecutorService mAccountAvatarExecutor = Executors.newSingleThreadExecutor();
    private final Handler mSystemVolumeHandler = new Handler(Looper.getMainLooper());
    private final DecelerateInterpolator mPageTransitionInterpolator =
            new DecelerateInterpolator();
    private String mAccountAvatarUrl = "";
    private int mAmplifierVolumePercent;
    private int mPageTransitionGeneration;
    private int mAiChatEntryTransitionGeneration;
    private boolean mBluetoothConnected;
    private boolean mWifiConnected;
    private boolean mAmplifierMuted;
    private boolean mAiConversationBackgroundVisible;
    private AiWakeupController mAiWakeupController;
    private AiHeadlessConversationView mAiHeadlessView;
    private boolean mHomeNavigationPending;
    private boolean mSuppressBackStackUiSync;
    private boolean mActivityResumed;
    private boolean mPendingSystemUpdateSuccess;
    private MainPage mPendingInitialMode;
    /** 后台时请求打开的页面（如 QQ 音乐前台期间收到 AI 应答），onResume 时消费。 */
    private MainPage mPendingPageToShow;
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
    private final Runnable mScreenSaverTimeoutRunnable = this::showScreenSaver;
    private final Runnable mScreenSaverClockRunnable = new Runnable() {
        @Override
        public void run() {
            updateSimpleScreenSaverClock();
            updateFlipScreenSaverClock();
            updateWeatherScreenSaverClock();
            mSystemVolumeHandler.postDelayed(this, 1_000L);
        }
    };
    private View mScreenSaverOverlay;
    private Dialog mScreenSaverDialog;
    private TextView mScreenSaverTime;
    private TextView mScreenSaverDate;
    private FlipLayout mFlipHour;
    private FlipLayout mFlipMinute;
    private FlipLayout mFlipSecond;
    private TextView mFlipAmPm;
    private TextView mFlipDate;
    private TextView mFlipWeekday;
    private TextView mWeatherTime;
    private TextView mWeatherDate;
    private TextView mWeatherLocation;
    private TextView mWeatherTemperature;
    private TextView mWeatherRange;
    private TextView mWeatherDescription;
    private View mWeatherScreenSaverRoot;
    private View mWeatherScreenSaverCard;
    private ImageView mWeatherScreenSaverIcon;
    private boolean mWeatherScreenSaverLoading;
    private final ExecutorService mWeatherExecutor = Executors.newSingleThreadExecutor();
    private LocationManager mWeatherLocationManager;
    private LocationListener mWeatherLocationListener;
    private Location mWeatherLastKnownLocation;
    private int mWeatherDataGeneration;
    private final Runnable mWeatherLocationTimeoutRunnable = () -> {
        Location fallbackLocation = mWeatherLastKnownLocation;
        AppLog.w(WEATHER_LOCATION_TAG, "Current location timed out; using last known location: "
                + describeLocation(fallbackLocation));
        stopWeatherLocationUpdates();
        fetchWeatherData(fallbackLocation);
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
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!FirstUseGuideStore.isCompleted(this)) {
            startActivity(new Intent(this, FirstUseGuideActivity.class));
            finish();
        } else {
            mPendingInitialMode = consumeInitialModeIntent(getIntent());
        }
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
        handleSystemUpdateInstallResult(getIntent());
    }

    @Override
    protected void initData() {
        startSystemMusicVolumeCheck();
        AudioRouteController.getInstance().initialize(this);
        presenter.init();
        startVoiceWakeupIfGuideCompleted();
    }

    /**
     * 启动语音唤醒（ALSA 多麦采集 + VTN 前端处理），Launcher 常驻，唤醒引擎随之常驻。
     * 同时创建共享 AI 会话 presenter（headless 视图驱动左上角悬浮条）。
     */
    private void startVoiceWakeupIfGuideCompleted() {
        if (!FirstUseGuideStore.isCompleted(this) || mAiWakeupController != null) {
            return;
        }
        mAiWakeupController = AiWakeupController.getInstance(this);
        mAiWakeupController.setNavigator(this);
        mAiWakeupController.start();
        AiListeningOverlay.getInstance().attach(this, binding.launcherRoot);
        mAiHeadlessView = new AiHeadlessConversationView(this);
        AiPresenter.obtainShared(this, mAiHeadlessView);
    }

    @Override
    public boolean onWakeupBeginListening() {
        if (binding == null) {
            return false;
        }
        Fragment currentFragment = getFragmentManager().findFragmentById(R.id.fragment_container);
        if (currentFragment instanceof AiFragment) {
            // AI 页已可见：对话直接渲染在页面内，不再弹悬浮条。
            return true;
        }
        // 唤醒先进入悬浮条聆听模式；AI 页等首个对话正文到达后再打开。
        AiPresenter presenter = AiPresenter.peekShared();
        if (presenter != null && mAiHeadlessView != null) {
            presenter.attachConversationView(mAiHeadlessView);
        }
        return true;
    }

    @Override
    public void onWakeupRejected(AiWakeupController.Reason reason) {
        if (reason == AiWakeupController.Reason.NOT_AUTHORIZED) {
            showAuthorization();
        }
        int messageResId = reason == AiWakeupController.Reason.NO_NETWORK
                ? R.string.ai_conversation_connection_error
                : R.string.ai_conversation_authorize_required;
        showToast(getString(messageResId));
    }

    @Override
    protected void onResume() {
        super.onResume();
        mActivityResumed = true;
        resetScreenSaverTimer();
        applyLocalizedTexts();
        registerSystemReceiver();
        presenter.onSystemStateChanged();
        presenter.startTicker();
        syncPageUiWithCurrentFragment();
        applyPendingInitialMode();
        applyPendingPageToShow();
        handleSystemUpdateInstallResult(getIntent());
    }

    @Override
    protected void onPause() {
        mActivityResumed = false;
        hideScreenSaver();
        super.onPause();
        unregisterReceiverQuietly();
        presenter.stopTicker();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event != null) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN && mScreenSaverOverlay != null) {
                hideScreenSaver();
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN
                    || event.getActionMasked() == MotionEvent.ACTION_UP) {
                resetScreenSaverTimer();
            }
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (mScreenSaverOverlay != null) {
            hideScreenSaver();
            return;
        }
        // The launcher root remains active; content pages use the native Fragment back stack.
        navigateBack();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        startVoiceWakeupIfGuideCompleted();
        handleSystemUpdateInstallResult(intent);
        MainPage initialMode = consumeInitialModeIntent(intent);
        if (initialMode != null) {
            mPendingInitialMode = initialMode;
            if (mActivityResumed) {
                applyPendingInitialMode();
            }
        }
    }

    @Override
    public void updateClock(String time, String date) {
        // The information cards were replaced by the input-mode carousel.
    }

    private void resetScreenSaverTimer() {
        mSystemVolumeHandler.removeCallbacks(mScreenSaverTimeoutRunnable);
        int style = ScreenSaverSettings.getStyle(this);
        if (!mActivityResumed || mScreenSaverOverlay != null
                || (style != SettingsModel.SCREEN_SAVER_STYLE_SIMPLE
                && style != SettingsModel.SCREEN_SAVER_STYLE_FLIP
                && style != SettingsModel.SCREEN_SAVER_STYLE_WEATHER
                && style != SettingsModel.SCREEN_SAVER_STYLE_BLACK)) {
            return;
        }
        int timeout = ScreenSaverSettings.getTimeout(this);
        if (timeout == SettingsModel.SCREEN_SAVER_TIMEOUT_NEVER) {
            return;
        }
        long delay = (timeout == SettingsModel.SCREEN_SAVER_TIMEOUT_ONE_MINUTE
                ? 1L : timeout == SettingsModel.SCREEN_SAVER_TIMEOUT_FIVE_MINUTES
                ? 5L : timeout == SettingsModel.SCREEN_SAVER_TIMEOUT_TEN_MINUTES
                ? 10L : 30L) * SCREEN_SAVER_MINUTE_MS;
        mSystemVolumeHandler.postDelayed(mScreenSaverTimeoutRunnable, delay);
    }

    private void showScreenSaver() {
        if (!mActivityResumed || mScreenSaverOverlay != null) {
            return;
        }
        int style = ScreenSaverSettings.getStyle(this);
        if (style == SettingsModel.SCREEN_SAVER_STYLE_BLACK) {
            showBlackScreenSaver();
            return;
        }
        if (style == SettingsModel.SCREEN_SAVER_STYLE_FLIP) {
            showFlipScreenSaver();
            return;
        }
        if (style == SettingsModel.SCREEN_SAVER_STYLE_WEATHER) {
            showWeatherScreenSaver();
            return;
        }
        if (style != SettingsModel.SCREEN_SAVER_STYLE_SIMPLE) {
            return;
        }
        mScreenSaverOverlay = LayoutInflater.from(this)
                .inflate(R.layout.layout_screen_saver_simple, binding.launcherRoot, false);
        FrostedTextView screenSaverTime = mScreenSaverOverlay.findViewById(
                R.id.simple_screen_saver_time);
        mScreenSaverTime = screenSaverTime;
        mScreenSaverDate = mScreenSaverOverlay.findViewById(R.id.simple_screen_saver_date);
        ImageView wallpaper = mScreenSaverOverlay.findViewById(
                R.id.simple_screen_saver_wallpaper);
        int wallpaperResource = ScreenSaverSettings.getSimpleWallpaperResource(
                ScreenSaverSettings.getSimpleWallpaper(this));
        wallpaper.setImageResource(wallpaperResource);
        screenSaverTime.setBackdropResource(wallpaperResource);
        showScreenSaverOverlay();
        updateSimpleScreenSaverClock();
        mSystemVolumeHandler.removeCallbacks(mScreenSaverClockRunnable);
        mSystemVolumeHandler.post(mScreenSaverClockRunnable);
    }

    private void showFlipScreenSaver() {
        mScreenSaverOverlay = LayoutInflater.from(this)
                .inflate(R.layout.layout_screen_saver_flip, binding.launcherRoot, false);
        mFlipHour = mScreenSaverOverlay.findViewById(R.id.flip_screen_saver_hour);
        mFlipMinute = mScreenSaverOverlay.findViewById(R.id.flip_screen_saver_minute);
        mFlipSecond = mScreenSaverOverlay.findViewById(R.id.flip_screen_saver_second);
        mFlipAmPm = mScreenSaverOverlay.findViewById(R.id.flip_screen_saver_ampm);
        mFlipDate = mScreenSaverOverlay.findViewById(R.id.flip_screen_saver_date);
        mFlipWeekday = mScreenSaverOverlay.findViewById(R.id.flip_screen_saver_weekday);
        showScreenSaverOverlay();
        updateFlipScreenSaverClock();
        mSystemVolumeHandler.removeCallbacks(mScreenSaverClockRunnable);
        mSystemVolumeHandler.post(mScreenSaverClockRunnable);
    }

    private void showWeatherScreenSaver() {
        mScreenSaverOverlay = LayoutInflater.from(this)
                .inflate(R.layout.layout_screen_saver_weather, binding.launcherRoot, false);
        mWeatherScreenSaverRoot = mScreenSaverOverlay.findViewById(
                R.id.weather_screen_saver_root);
        mWeatherScreenSaverCard = mScreenSaverOverlay.findViewById(
                R.id.weather_screen_saver_weather_card);
        mWeatherScreenSaverIcon = mScreenSaverOverlay.findViewById(
                R.id.weather_screen_saver_icon);
        mWeatherTime = mScreenSaverOverlay.findViewById(R.id.weather_screen_saver_time);
        mWeatherDate = mScreenSaverOverlay.findViewById(R.id.weather_screen_saver_date);
        mWeatherLocation = mScreenSaverOverlay.findViewById(R.id.weather_screen_saver_location);
        mWeatherTemperature = mScreenSaverOverlay.findViewById(R.id.weather_screen_saver_temperature);
        mWeatherRange = mScreenSaverOverlay.findViewById(R.id.weather_screen_saver_range);
        mWeatherDescription = mScreenSaverOverlay.findViewById(R.id.weather_screen_saver_description);
        mWeatherScreenSaverLoading = true;
        AppLog.i(WEATHER_LOCATION_TAG, "Weather screen saver waiting for weather data");
        loadWeatherData();
    }

    private void showWeatherScreenSaverContent() {
        if (mScreenSaverOverlay == null) {
            return;
        }
        mScreenSaverOverlay.setOnClickListener(view -> hideScreenSaver());
        if (mScreenSaverDialog == null) {
            showScreenSaverOverlay();
        } else {
            mScreenSaverDialog.setContentView(mScreenSaverOverlay);
        }
        mWeatherScreenSaverLoading = false;
        updateWeatherScreenSaverClock();
        mSystemVolumeHandler.removeCallbacks(mScreenSaverClockRunnable);
        mSystemVolumeHandler.post(mScreenSaverClockRunnable);
    }

    private void updateWeatherScreenSaverClock() {
        if (mWeatherTime == null || mWeatherDate == null) {
            return;
        }
        Date now = new Date();
        mWeatherTime.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(now));
        if (LocaleHelper.LANGUAGE_EN.equals(LocaleHelper.getLanguage(this))) {
            mWeatherDate.setText(new SimpleDateFormat("EEEE, MMMM d", Locale.US).format(now));
        } else {
            mWeatherDate.setText(new SimpleDateFormat("M月d日 E", Locale.CHINA).format(now));
        }
    }

    private void loadWeatherData() {
        if (USE_TEST_WEATHER_LOCATION) {
            AppLog.i(WEATHER_LOCATION_TAG, "Using test weather location: "
                    + getString(R.string.weather_test_location));
            fetchWeatherData(null);
            return;
        }
        AppLog.i(WEATHER_LOCATION_TAG, "Location permission: fine="
                + (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) + ", coarse="
                + (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED));
        if (!hasLocationPermission()) {
            AppLog.i(WEATHER_LOCATION_TAG, "Requesting location permission");
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_WEATHER_LOCATION);
            fetchWeatherData(null);
            return;
        }

        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        Location lastKnownLocation = getBestLastKnownLocation(locationManager);
        AppLog.i(WEATHER_LOCATION_TAG, "Last known location: "
                + describeLocation(lastKnownLocation));
        if (locationManager == null || !isLocationEnabled(locationManager)) {
            AppLog.w(WEATHER_LOCATION_TAG, "No enabled location provider; using last known location");
            fetchWeatherData(lastKnownLocation);
            return;
        }

        requestCurrentWeatherLocation(locationManager, lastKnownLocation);
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private Location getBestLastKnownLocation(LocationManager locationManager) {
        if (locationManager == null) {
            return null;
        }
        Location bestLocation = null;
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            bestLocation = getLastKnownLocation(locationManager, LocationManager.GPS_PROVIDER);
        }
        Location networkLocation = getLastKnownLocation(locationManager, LocationManager.NETWORK_PROVIDER);
        if (networkLocation != null && (bestLocation == null
                || networkLocation.getTime() > bestLocation.getTime())) {
            bestLocation = networkLocation;
        }
        return bestLocation;
    }

    private Location getLastKnownLocation(LocationManager locationManager, String provider) {
        try {
            return locationManager.getLastKnownLocation(provider);
        } catch (SecurityException | IllegalArgumentException ignored) {
            return null;
        }
    }

    private boolean isLocationEnabled(LocationManager locationManager) {
        boolean gpsEnabled = isProviderEnabled(locationManager, LocationManager.GPS_PROVIDER);
        boolean networkEnabled = isProviderEnabled(locationManager, LocationManager.NETWORK_PROVIDER);
        AppLog.i(WEATHER_LOCATION_TAG, "Location providers: gps=" + gpsEnabled
                + ", network=" + networkEnabled);
        return gpsEnabled || networkEnabled;
    }

    private boolean isProviderEnabled(LocationManager locationManager, String provider) {
        try {
            return locationManager.isProviderEnabled(provider);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private void requestCurrentWeatherLocation(LocationManager locationManager,
            Location lastKnownLocation) {
        String provider = null;
        if (isProviderEnabled(locationManager, LocationManager.NETWORK_PROVIDER)) {
            provider = LocationManager.NETWORK_PROVIDER;
        } else if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                && isProviderEnabled(locationManager, LocationManager.GPS_PROVIDER)) {
            provider = LocationManager.GPS_PROVIDER;
        }
        if (provider == null) {
            AppLog.w(WEATHER_LOCATION_TAG, "No provider available for current location request");
            fetchWeatherData(lastKnownLocation);
            return;
        }

        stopWeatherLocationUpdates();
        mWeatherLocationManager = locationManager;
        mWeatherLastKnownLocation = lastKnownLocation;
        mWeatherLocationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                AppLog.i(WEATHER_LOCATION_TAG, "Current location received: "
                        + describeLocation(location));
                stopWeatherLocationUpdates();
                fetchWeatherData(location);
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            @Override
            public void onProviderEnabled(String provider) {
            }

            @Override
            public void onProviderDisabled(String provider) {
            }
        };
        try {
            AppLog.i(WEATHER_LOCATION_TAG, "Requesting single update from " + provider);
            locationManager.requestSingleUpdate(provider, mWeatherLocationListener,
                    Looper.getMainLooper());
            mSystemVolumeHandler.postDelayed(mWeatherLocationTimeoutRunnable,
                    WEATHER_LOCATION_TIMEOUT_MS);
        } catch (SecurityException | IllegalArgumentException ignored) {
            Location fallbackLocation = mWeatherLastKnownLocation;
            AppLog.w(WEATHER_LOCATION_TAG, "Unable to request current location; using last known",
                    ignored);
            stopWeatherLocationUpdates();
            fetchWeatherData(fallbackLocation);
        }
    }

    private void stopWeatherLocationUpdates() {
        mSystemVolumeHandler.removeCallbacks(mWeatherLocationTimeoutRunnable);
        if (mWeatherLocationManager != null && mWeatherLocationListener != null) {
            try {
                mWeatherLocationManager.removeUpdates(mWeatherLocationListener);
            } catch (SecurityException ignored) {
                AppLog.w(WEATHER_LOCATION_TAG, "Unable to remove location listener", ignored);
            }
        }
        mWeatherLocationManager = null;
        mWeatherLocationListener = null;
        mWeatherLastKnownLocation = null;
    }

    private void fetchWeatherData(@Nullable Location currentLocation) {
        final int requestGeneration = ++mWeatherDataGeneration;
        AppLog.i(WEATHER_LOCATION_TAG, "Loading weather with location: "
                + describeLocation(currentLocation));
        mWeatherExecutor.execute(() -> {
            double latitude = USE_TEST_WEATHER_LOCATION ? TEST_WEATHER_LATITUDE : 39.9042;
            double longitude = USE_TEST_WEATHER_LOCATION ? TEST_WEATHER_LONGITUDE : 116.4074;
            String location = USE_TEST_WEATHER_LOCATION ? getString(R.string.weather_test_location)
                    : getString(R.string.weather_default_location);
            try {
                if (!USE_TEST_WEATHER_LOCATION && currentLocation != null) {
                    latitude = currentLocation.getLatitude();
                    longitude = currentLocation.getLongitude();
                    Geocoder geocoder = new Geocoder(this, getWeatherLocale());
                    List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
                    AppLog.i(WEATHER_LOCATION_TAG, "Reverse geocode returned "
                            + (addresses == null ? 0 : addresses.size()) + " address(es)");
                    if (addresses != null && !addresses.isEmpty()) {
                        Address address = addresses.get(0);
                        String city = address.getLocality();
                        if (TextUtils.isEmpty(city)) city = address.getAdminArea();
                        if (!TextUtils.isEmpty(city)) {
                            location = city;
                            AppLog.i(WEATHER_LOCATION_TAG, "Resolved city: " + city);
                        } else {
                            AppLog.w(WEATHER_LOCATION_TAG,
                                    "Reverse geocode result has no locality or admin area");
                        }
                    }
                }
                URL url = new URL("https://api.open-meteo.com/v1/forecast?latitude="
                        + latitude + "&longitude=" + longitude
                        + "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min&timezone=auto");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                InputStream input = connection.getInputStream();
                byte[] data = new byte[8192];
                StringBuilder body = new StringBuilder();
                int count;
                while ((count = input.read(data)) != -1) body.append(new String(data, 0, count));
                input.close();
                JSONObject json = new JSONObject(body.toString());
                JSONObject current = json.getJSONObject("current");
                double temperature = current.getDouble("temperature_2m");
                int code = current.getInt("weather_code");
                JSONObject daily = json.getJSONObject("daily");
                double max = daily.getJSONArray("temperature_2m_max").getDouble(0);
                double min = daily.getJSONArray("temperature_2m_min").getDouble(0);
                String description = weatherDescription(code);
                final String finalLocation = location;
                final String temperatureText = String.format(Locale.getDefault(), "%.0f°C",
                        temperature);
                final String rangeText = String.format(Locale.getDefault(), "%.0f°/%.0f°", min,
                        max);
                runOnUiThread(() -> {
                    if (requestGeneration != mWeatherDataGeneration || mWeatherLocation == null) return;
                    AppLog.i(WEATHER_LOCATION_TAG, "Weather loaded; displaying city: "
                            + finalLocation);
                    mWeatherLocation.setText(finalLocation);
                    mWeatherTemperature.setText(temperatureText);
                    mWeatherRange.setText(rangeText);
                    mWeatherDescription.setText(description);
                    updateWeatherScreenSaverAppearance(code);
                    if (mWeatherScreenSaverLoading) {
                        showWeatherScreenSaverContent();
                    }
                });
            } catch (Exception exception) {
                AppLog.w(WEATHER_LOCATION_TAG, "Weather or reverse-geocoding request failed",
                        exception);
                runOnUiThread(() -> {
                    if (requestGeneration == mWeatherDataGeneration && mWeatherLocation != null) {
                        if (mWeatherScreenSaverLoading) {
                            AppLog.i(WEATHER_LOCATION_TAG,
                                    "Weather unavailable; displaying default sunny screen saver");
                            showWeatherScreenSaverContent();
                        }
                    }
                });
            }
        });
    }

    private String describeLocation(@Nullable Location location) {
        if (location == null) {
            return "none";
        }
        return location.getProvider() + "(" + location.getLatitude() + ","
                + location.getLongitude() + "), accuracy=" + location.getAccuracy()
                + "m, age=" + (System.currentTimeMillis() - location.getTime()) + "ms";
    }

    private Locale getWeatherLocale() {
        return LocaleHelper.LANGUAGE_EN.equals(LocaleHelper.getLanguage(this))
                ? Locale.US : Locale.SIMPLIFIED_CHINESE;
    }

    private String weatherDescription(int code) {
        if (code == 0) return getString(R.string.weather_sunny);
        if (code <= 3) return getString(R.string.weather_partly_cloudy);
        if (code <= 48) return getString(R.string.weather_foggy);
        if (code <= 67 || code >= 80 && code <= 82) return getString(R.string.weather_rainy);
        if (code >= 71 && code <= 77) return getString(R.string.weather_snowy);
        return getString(R.string.weather_stormy);
    }

    private void updateWeatherScreenSaverAppearance(int weatherCode) {
        boolean isRainy = (weatherCode >= 51 && weatherCode <= 67)
                || (weatherCode >= 80 && weatherCode <= 82);
        boolean isStormy = weatherCode == 95 || weatherCode == 96 || weatherCode == 99;
        boolean isCloudy = weatherCode >= 1 && weatherCode <= 3;
        mWeatherScreenSaverRoot.setBackgroundResource(isStormy
                ? R.drawable.img_weather_clock_storm
                : isRainy ? R.drawable.img_weather_clock_rain
                : isCloudy ? R.drawable.img_weather_clock_cloudy
                : R.drawable.img_weather_clock_sun);
        mWeatherScreenSaverCard.setBackgroundResource(isStormy
                ? R.drawable.bg_weather_clock_storm
                : isRainy ? R.drawable.bg_weather_clock_rain
                : isCloudy ? R.drawable.bg_weather_clock_cloudy
                : R.drawable.bg_weather_clock_sun);
        mWeatherScreenSaverIcon.setBackgroundResource(isStormy
                ? R.drawable.ic_storm : isRainy ? R.drawable.ic_rain
                : isCloudy ? R.drawable.ic_cloudy : R.drawable.ic_sun);
        mWeatherScreenSaverIcon.getLayoutParams().width = dp(isStormy ? 167
                : isCloudy ? 196 : 170);
        mWeatherScreenSaverIcon.getLayoutParams().height = dp(isStormy ? 137
                : isRainy ? 190 : isCloudy ? 153 : 170);
        mWeatherScreenSaverIcon.requestLayout();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_WEATHER_LOCATION && mScreenSaverOverlay != null) {
            AppLog.i(WEATHER_LOCATION_TAG, "Location permission result: granted="
                    + hasLocationPermission());
            loadWeatherData();
        }
    }

    private void updateFlipScreenSaverClock() {
        if (mFlipHour == null || mFlipMinute == null || mFlipSecond == null) {
            return;
        }
        Date now = new Date();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(now);
        int hour = calendar.get(Calendar.HOUR);
        mFlipHour.flipTo(hour == 0 ? 12 : hour);
        mFlipMinute.flipTo(calendar.get(Calendar.MINUTE));
        mFlipSecond.flipTo(calendar.get(Calendar.SECOND));
        mFlipAmPm.setText(calendar.get(Calendar.AM_PM) == Calendar.AM ? "AM" : "PM");
        mFlipDate.setText(new SimpleDateFormat("yyyy.MM.dd", Locale.US).format(now));
        mFlipWeekday.setText(new SimpleDateFormat("EEE.", Locale.US).format(now));
    }

    private void showBlackScreenSaver() {
        View blackOverlay = new View(this);
        blackOverlay.setBackgroundColor(Color.BLACK);
        blackOverlay.setClickable(true);
        blackOverlay.setFocusable(true);
        mScreenSaverOverlay = blackOverlay;
        showScreenSaverOverlay();
    }

    private void showScreenSaverOverlay() {
        if (mScreenSaverOverlay == null || isFinishing() || isDestroyed()) {
            return;
        }
        mScreenSaverOverlay.setOnClickListener(view -> hideScreenSaver());
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(mScreenSaverOverlay);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnKeyListener((ignored, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                hideScreenSaver();
                return true;
            }
            return keyCode == KeyEvent.KEYCODE_BACK;
        });
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        dialog.show();
        mScreenSaverDialog = dialog;
        window = dialog.getWindow();
        if (window != null) {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
        }
    }

    private void updateSimpleScreenSaverClock() {
        if (mScreenSaverTime == null || mScreenSaverDate == null) {
            return;
        }
        Locale locale = getResources().getConfiguration().locale;
        Date now = new Date();
        mScreenSaverTime.setText(new SimpleDateFormat("HH:mm", locale).format(now));
        if (LocaleHelper.LANGUAGE_EN.equals(LocaleHelper.getLanguage(this))) {
            mScreenSaverDate.setText(new SimpleDateFormat("EEEE, MMMM d", Locale.US)
                    .format(now));
            return;
        }
        Lunar lunar = Lunar.fromDate(now);
        String solarDate = new SimpleDateFormat("M月d日E", locale).format(now);
        String lunarDate = lunar.getYearInGanZhi() + "年" + lunar.getMonthInChinese()
                + "月" + lunar.getDayInChinese();
        mScreenSaverDate.setText(solarDate + " · " + lunarDate);
    }

    private void hideScreenSaver() {
        mSystemVolumeHandler.removeCallbacks(mScreenSaverClockRunnable);
        mWeatherScreenSaverLoading = false;
        AppLog.i(WEATHER_LOCATION_TAG, "Weather screen saver hidden; stopping location update");
        stopWeatherLocationUpdates();
        mWeatherDataGeneration++;
        if (mScreenSaverDialog != null) {
            mScreenSaverDialog.dismiss();
            mScreenSaverDialog = null;
        }
        mScreenSaverOverlay = null;
        mScreenSaverTime = null;
        mScreenSaverDate = null;
        mFlipHour = null;
        mFlipMinute = null;
        mFlipSecond = null;
        mFlipAmPm = null;
        mFlipDate = null;
        mFlipWeekday = null;
        mWeatherTime = null;
        mWeatherDate = null;
        mWeatherLocation = null;
        mWeatherTemperature = null;
        mWeatherRange = null;
        mWeatherDescription = null;
        mWeatherScreenSaverRoot = null;
        mWeatherScreenSaverCard = null;
        mWeatherScreenSaverIcon = null;
        resetScreenSaverTimer();
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

    public void setLanguageSwitchLoading(boolean loading) {
        if (binding != null) {
            binding.settingsLanguageLoadingOverlay.setVisibility(
                    loading ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void updateVolume(int volumePercent) {
        mAmplifierVolumePercent = volumePercent;
        renderAmplifierVolume();
    }

    @Override
    public void updateVolumeMuted(boolean muted) {
        mAmplifierMuted = muted;
        renderAmplifierVolume();
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
        mAmplifierVolumePercent = volumePercent;
        mAmplifierMuted = muted;
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
        resetBottomNavigationBackground();
        mVolumeDialog.show(volumePercent, muted);
    }

    private void renderAmplifierVolume() {
        if (binding == null) {
            return;
        }
        int displayedVolume = mAmplifierMuted ? 0 : mAmplifierVolumePercent;
        binding.bottomNavigationVolume.setText(getString(
                R.string.main_bottom_navigation_volume_format, displayedVolume));
        if (mVolumeDialog != null) {
            mVolumeDialog.updateVolume(mAmplifierVolumePercent, mAmplifierMuted);
        }
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
        if (currentFragment instanceof AiFragment && page != MainPage.AI) {
            removeAiPageBeforeNavigating();
        }
        if (isInputModePage(page)) {
            showInputModePage(page);
            return;
        }
        showStackedPage(page);
    }

    /**
     * 其他应用（如 QQ 音乐）在前台时收到页面请求：先把 launcher 任务拉回前台，
     * 页面切换推迟到 onResume 执行——避免 stopped 状态下 FragmentTransaction
     * 抛 "Can not perform this action after onSaveInstanceState" 崩溃。
     */
    public void bringToFrontAndShowPage(MainPage page) {
        if (binding == null || isFinishing() || isDestroyed()) {
            return;
        }
        if (!mActivityResumed) {
            mPendingPageToShow = page;
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            try {
                startActivity(intent);
            } catch (Exception e) {
                AppLog.w("MainActivity", "bring launcher to front failed", e);
            }
            return;
        }
        showPage(page);
    }

    private void applyPendingPageToShow() {
        MainPage page = mPendingPageToShow;
        if (page == null) {
            return;
        }
        mPendingPageToShow = null;
        showPage(page);
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
        resetBottomNavigationBackground();
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
                .commitAllowingStateLoss();

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
        releaseAiConversationIfVisible();
        resetBottomNavigationBackground();
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
        releaseAiConversationIfVisible();
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
        if (mAiWakeupController != null) {
            mAiWakeupController.clearNavigator(this);
            mAiWakeupController = null;
        }
        AiListeningOverlay.getInstance().detach();
        mAiHeadlessView = null;
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
        mWeatherExecutor.shutdownNow();
        super.onDestroy();
    }

    private void handleSystemUpdateInstallResult(Intent intent) {
        boolean hasIntentResult = intent != null
                && intent.hasExtra(SystemUpdateInstallReceiver.EXTRA_UPDATE_SUCCEEDED);
        if (intent != null) {
            boolean succeeded = hasIntentResult && intent.getBooleanExtra(
                    SystemUpdateInstallReceiver.EXTRA_UPDATE_SUCCEEDED, false);
            intent.removeExtra(SystemUpdateInstallReceiver.EXTRA_UPDATE_SUCCEEDED);
            intent.removeExtra(SystemUpdateInstallReceiver.EXTRA_UPDATE_ERROR);
            if (succeeded) {
                mPendingSystemUpdateSuccess = true;
            } else if (hasIntentResult && mActivityResumed) {
                showToast(getString(R.string.system_update_failed));
            }
        }
        if (mActivityResumed && SystemUpdateInstallReceiver.consumeUpdateSucceeded(this)) {
            mPendingSystemUpdateSuccess = true;
        }
        if (mActivityResumed && mPendingSystemUpdateSuccess) {
            mPendingSystemUpdateSuccess = false;
            refreshSystemUpdateVersion();
            dismissSystemUpdateProgress();
            SystemUpdateSuccessDialog.show(this);
        }
    }

    private void refreshSystemUpdateVersion() {
        Fragment fragment = getFragmentManager().findFragmentById(R.id.fragment_container);
        if (fragment instanceof SettingsFragment) {
            ((SettingsFragment) fragment).onSystemUpdatePackageReplaced();
        }
    }

    private void dismissSystemUpdateProgress() {
        Fragment fragment = getFragmentManager().findFragmentById(R.id.fragment_container);
        if (fragment instanceof SettingsFragment) {
            ((SettingsFragment) fragment).dismissSystemUpdateProgress();
        }
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
        bindBottomNavigationTouchFeedback(binding.bottomNavigationBack, BottomNavigationItem.BACK);
        bindBottomNavigationTouchFeedback(binding.bottomNavigationHome, BottomNavigationItem.HOME);
        bindBottomNavigationTouchFeedback(binding.bottomNavigationBackground,
                BottomNavigationItem.BACKGROUND);
        bindBottomNavigationTouchFeedback(binding.bottomNavigationVolume,
                BottomNavigationItem.VOLUME);
        bindBottomNavigationTouchFeedback(binding.bottomNavigationApps, BottomNavigationItem.APPS);
        bindBottomNavigationTouchFeedback(binding.bottomNavigationSettings,
                BottomNavigationItem.SETTINGS);
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

    private void bindBottomNavigationTouchFeedback(View navigationItem,
            final BottomNavigationItem item) {
        navigationItem.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        selectBottomNavigationItem(item);
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        resetBottomNavigationBackground();
                        break;
                    default:
                        break;
                }
                return false;
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

    private void applyPendingInitialMode() {
        if (binding == null || mInputModeAdapter == null || mPendingInitialMode == null) {
            return;
        }
        MainPage initialMode = mPendingInitialMode;
        mPendingInitialMode = null;
        mInputModeAdapter.selectMode(initialMode);
        updateModeTextFromSelectedInputMode();
        scrollToSelectedMode();
        if (mInputModeDialog != null) {
            mInputModeDialog.updateState(mInputModeAdapter.getSelectedPage(),
                    mBluetoothConnected, mWifiConnected);
        }
    }

    @Nullable
    private MainPage consumeInitialModeIntent(Intent intent) {
        if (intent == null) {
            return null;
        }
        String modeName = intent.getStringExtra(EXTRA_INITIAL_MODE);
        intent.removeExtra(EXTRA_INITIAL_MODE);
        if (TextUtils.isEmpty(modeName)) {
            return null;
        }
        try {
            MainPage mode = MainPage.valueOf(modeName);
            return isInputModePage(mode) ? mode : null;
        } catch (IllegalArgumentException ignored) {
            return null;
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
            AppLog.w(TAG, "Unable to verify Android music volume");
            return false;
        }
        int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        if (currentVolume < maxVolume) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0);
            int updatedVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            boolean isAtMaximum = updatedVolume >= maxVolume;
            AppLog.i(TAG, "Android music volume raised from " + currentVolume + " to "
                    + updatedVolume + ", maximum=" + maxVolume + ", success=" + isAtMaximum);
            return isAtMaximum;
        }
        AppLog.i(TAG, "Android music volume already at maximum: " + maxVolume);
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
        releaseAiConversationIfVisible();
        mHomeNavigationPending = false;
        ++mPageTransitionGeneration;
        cancelPageAnimations();
        popBackStackImmediately(false);
    }

    private void releaseAiConversationIfVisible() {
        Fragment fragment = getFragmentManager().findFragmentById(R.id.fragment_container);
        if (fragment instanceof AiFragment) {
            ((AiFragment) fragment).releaseForNavigation();
        }
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
        resetBottomNavigationBackground();
        if (isVolumeDialogShowing()) {
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
        boolean aiConversationVisible = fragment instanceof AiFragment;
        updateLauncherBackground(aiConversationVisible);
        hideAiChatEntry();
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
        resetBottomNavigationBackground();
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

    private void resetBottomNavigationBackground() {
        if (binding != null) {
            binding.bottomNavigation.setBackgroundResource(R.drawable.bg_navigation);
        }
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
            case SYSTEM_APPS:
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
                return fragment instanceof AiFragment;
            case SYSTEM_APPS:
                return fragment instanceof SystemAppsFragment;
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
                return new AiFragment();
            case SYSTEM_APPS:
                return new SystemAppsFragment();
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
