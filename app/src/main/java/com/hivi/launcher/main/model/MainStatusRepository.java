package com.hivi.launcher.main.model;

import android.content.Context;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.text.TextUtils;

import com.hivi.launcher.R;
import com.hivi.launcher.audio.AudioRouteController;
import com.hivi.launcher.bluetooth.model.McuBluetoothController;
import com.hivi.launcher.music.model.UpnpPlaybackManager;
import com.hivi.launcher.music.model.UpnpPlaybackState;
import com.hivi.launcher.wifi.model.WifiConnectionStatus;

import java.util.List;

/**
 * Reads the device state rendered by the home information panel.
 *
 * <p>Wi-Fi SSID state is read through {@link WifiConnectionStatus} so the home and settings
 * screens use the same connection source. Presenters consume the resulting models instead of
 * assembling Wi-Fi, Bluetooth, volume, and playback state themselves.</p>
 */
public final class MainStatusRepository {
    private final Context mContext;
    private final MediaSessionManager mMediaSessionManager;
    private final McuBluetoothController mMcuBluetoothController;
    private final AudioRouteController mAudioRouteController;

    public MainStatusRepository(Context context) {
        mContext = context.getApplicationContext();
        mMediaSessionManager = (MediaSessionManager) mContext.getSystemService(
                Context.MEDIA_SESSION_SERVICE);
        mMcuBluetoothController = McuBluetoothController.getInstance();
        mAudioRouteController = AudioRouteController.getInstance();
    }

    public MainStatus loadStatus() {
        return new MainStatus(getWifiLabel(), isBluetoothConnected(), getBluetoothDeviceName(),
                getVolumePercent(), getMusicInfo());
    }

    public String getWifiLabel() {
        String ssid = WifiConnectionStatus.getConnectedSsid(mContext);
        return TextUtils.isEmpty(ssid) ? mContext.getString(R.string.main_disconnected) : ssid;
    }

    public boolean isBluetoothConnected() {
        return mMcuBluetoothController.isConnected();
    }

    public String getBluetoothDeviceName() {
        return mMcuBluetoothController.getPhoneName();
    }

    public int getVolumePercent() {
        return mAudioRouteController.getAmplifierVolumePercent();
    }

    public void adjustVolume(int direction) {
        mAudioRouteController.adjustAmplifierVolume(direction);
    }

    public void setVolumePercent(int volumePercent) {
        mAudioRouteController.setAmplifierVolume(volumePercent);
    }

    public boolean isMusicStreamMuted() {
        return mAudioRouteController.isAmplifierMuted();
    }

    public void toggleMusicStreamMute() {
        mAudioRouteController.toggleAmplifierMute();
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
