package com.hivi.launcher.main.data;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
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
import com.hivi.launcher.main.model.MainStatus;
import com.hivi.launcher.main.model.MusicInfo;
import com.hivi.launcher.music.model.UpnpPlaybackManager;
import com.hivi.launcher.music.model.UpnpPlaybackState;

import java.util.List;

/**
 * Reads the device state rendered by the home information panel.
 *
 * <p>This is intentionally the only home-layer class that talks to Android system services.
 * Presenters consume the resulting models instead of assembling Wi-Fi, Bluetooth, volume, and
 * playback state themselves.</p>
 */
public final class MainStatusRepository {
    private final Context mContext;
    private final AudioManager mAudioManager;
    private final WifiManager mWifiManager;
    private final ConnectivityManager mConnectivityManager;
    private final MediaSessionManager mMediaSessionManager;

    public MainStatusRepository(Context context) {
        mContext = context.getApplicationContext();
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        mConnectivityManager = (ConnectivityManager) mContext.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        mMediaSessionManager = (MediaSessionManager) mContext.getSystemService(
                Context.MEDIA_SESSION_SERVICE);
    }

    public MainStatus loadStatus() {
        return new MainStatus(getWifiLabel(), isBluetoothConnected(), getVolumePercent(),
                getMusicInfo());
    }

    public String getWifiLabel() {
        if (mConnectivityManager != null) {
            Network network = mConnectivityManager.getActiveNetwork();
            NetworkCapabilities capabilities = mConnectivityManager.getNetworkCapabilities(network);
            if (capabilities == null
                    || !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
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

    public boolean isBluetoothConnected() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        return adapter != null && adapter.isEnabled()
                && adapter.getProfileConnectionState(android.bluetooth.BluetoothProfile.A2DP)
                == android.bluetooth.BluetoothProfile.STATE_CONNECTED;
    }

    public int getVolumePercent() {
        if (mAudioManager == null) {
            return 0;
        }
        int current = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int max = Math.max(1, mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        return Math.round(current * 100f / max);
    }

    public void adjustVolume(int direction) {
        if (mAudioManager == null) {
            return;
        }
        mAudioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction,
                AudioManager.FLAG_PLAY_SOUND);
    }

    public MusicInfo getMusicInfo() {
        UpnpPlaybackState upnpState = UpnpPlaybackManager.getInstance().getCurrentState();
        if (upnpState != null && upnpState.hasRealSong()) {
            return new MusicInfo(upnpState.getTitle(), upnpState.getArtist());
        }
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
            // Media-session access is only available on some system builds.
        }
        return null;
    }
}
