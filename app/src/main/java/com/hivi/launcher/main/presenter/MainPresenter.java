package com.hivi.launcher.main.presenter;

import android.bluetooth.BluetoothAdapter;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.text.TextUtils;

import com.hivi.launcher.R;
import com.hivi.launcher.base.BasePresenter;
import com.hivi.launcher.main.model.MainStatus;
import com.hivi.launcher.main.model.MusicInfo;
import com.hivi.launcher.main.ui.MainView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainPresenter extends BasePresenter<MainView> {
    public static final String ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION";

    private static final String[] MUSIC_PACKAGES = {
            "com.tencent.qqmusic",
            "com.netease.cloudmusic",
            "com.kugou.android"
    };

    private final Context mContext;
    private final AudioManager mAudioManager;
    private final WifiManager mWifiManager;
    private final ConnectivityManager mConnectivityManager;
    private final MediaSessionManager mMediaSessionManager;

    private final Runnable mTicker = new Runnable() {
        @Override
        public void run() {
            updateClock();
            updateMedia();
            runOnUiThreadDelayed(this, 1000L);
        }
    };

    public MainPresenter(Context context, MainView view) {
        super(view);
        mContext = context.getApplicationContext();
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mWifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        mConnectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        mMediaSessionManager = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
    }

    public void init() {
        updateClock();
        updateConnectivity();
        updateVolume();
        updateMedia();
    }

    public void startTicker() {
        removeUiThreadRunnable(mTicker);
        mTicker.run();
    }

    public void stopTicker() {
        removeUiThreadRunnable(mTicker);
    }

    public void onSystemStateChanged() {
        updateConnectivity();
        updateVolume();
    }

    public void updateClock() {
        MainView view = getView();
        if (view == null) {
            return;
        }
        Locale locale = mContext.getResources().getConfiguration().locale;
        Date now = new Date();
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", locale);
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd", locale);
        SimpleDateFormat weekFormat = new SimpleDateFormat("E", locale);
        view.updateClock(timeFormat.format(now),
                dateFormat.format(now) + "   " + weekFormat.format(now));
    }

    public void updateConnectivity() {
        MainView view = getView();
        if (view != null) {
            view.updateConnectivity(getWifiLabel(), isBluetoothConnected());
        }
    }

    public void updateVolume() {
        MainView view = getView();
        if (view != null) {
            view.updateVolume(getVolumePercent());
        }
    }

    public MainStatus getCurrentStatus() {
        return new MainStatus(getWifiLabel(), isBluetoothConnected(), getVolumePercent(), getMusicInfo());
    }

    public void adjustVolume(int direction) {
        if (mAudioManager == null) {
            return;
        }
        mAudioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction,
                AudioManager.FLAG_PLAY_SOUND);
        updateVolume();
    }

    public void updateMedia() {
        MainView view = getView();
        if (view == null) {
            return;
        }
        MusicInfo musicInfo = getMusicInfo();
        if (musicInfo != null) {
            view.updateMusic(musicInfo.getTitle(), musicInfo.getArtist());
        }
    }

    public void launchFirstAvailableMusicApp() {
        for (String pkg : MUSIC_PACKAGES) {
            if (launchPackage(pkg)) {
                return;
            }
        }
        MainView view = getView();
        if (view != null) {
            view.showToast(mContext.getString(R.string.main_music_apps_not_installed));
        }
    }

    public void openSystemApps() {
        MainView view = getView();
        if (view != null) {
            view.openSystemApps();
        }
    }

    private String getWifiLabel() {
        if (mConnectivityManager != null) {
            Network network = mConnectivityManager.getActiveNetwork();
            NetworkCapabilities capabilities = mConnectivityManager.getNetworkCapabilities(network);
            if (capabilities == null || !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return mContext.getString(R.string.main_disconnected);
            }
        }

        if (mWifiManager == null) {
            return "WiFi";
        }
        WifiInfo wifiInfo = mWifiManager.getConnectionInfo();
        if (wifiInfo == null) {
            return mContext.getString(R.string.main_connected);
        }
        String ssid = wifiInfo.getSSID();
        if (TextUtils.isEmpty(ssid) || "<unknown ssid>".equals(ssid)) {
            return mContext.getString(R.string.main_connected);
        }
        return ssid.replace("\"", "");
    }

    private boolean isBluetoothConnected() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        return adapter != null && adapter.isEnabled()
                && adapter.getProfileConnectionState(android.bluetooth.BluetoothProfile.A2DP)
                == android.bluetooth.BluetoothProfile.STATE_CONNECTED;
    }

    private int getVolumePercent() {
        if (mAudioManager == null) {
            return 0;
        }
        int current = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int max = Math.max(1, mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        return Math.round(current * 100f / max);
    }

    private MusicInfo getMusicInfo() {
        if (mMediaSessionManager == null) {
            return null;
        }
        try {
            List<MediaController> controllers = mMediaSessionManager.getActiveSessions(null);
            for (MediaController controller : controllers) {
                if (controller.getMetadata() == null) {
                    continue;
                }
                CharSequence title = controller.getMetadata().getText(
                        android.media.MediaMetadata.METADATA_KEY_TITLE);
                CharSequence artist = controller.getMetadata().getText(
                        android.media.MediaMetadata.METADATA_KEY_ARTIST);
                if (!TextUtils.isEmpty(title)) {
                    return new MusicInfo(title,
                            TextUtils.isEmpty(artist) ? controller.getPackageName() : artist);
                }
            }
        } catch (SecurityException ignored) {
        }
        return null;
    }

    private boolean launchPackage(String packageName) {
        Intent launchIntent = mContext.getPackageManager().getLaunchIntentForPackage(packageName);
        if (launchIntent == null) {
            return false;
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            mContext.startActivity(launchIntent);
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        }
    }
}
