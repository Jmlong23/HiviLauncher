package com.hivi.launcher.main.model;

import android.content.Context;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.text.TextUtils;

import com.hivi.launcher.R;
import com.hivi.launcher.audio.AudioRouteController;
import com.hivi.launcher.bluetooth.model.McuBluetoothController;
import com.hivi.launcher.music.model.UpnpPlaybackManager;
import com.hivi.launcher.music.model.UpnpPlaybackState;
import com.hivi.launcher.wifi.model.WifiConnectionStatus;

import java.util.Collections;
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

    /**
     * 信息条跟随实际在播音源：投歌/AI歌曲在播优先，其次在播的本机音乐App；
     * 都暂停时沿用原偏好（投歌有曲目显示投歌，否则显示音乐App会话）。
     */
    public MusicInfo getMusicInfo() {
        UpnpPlaybackState upnpState = UpnpPlaybackManager.getInstance().getCurrentState();
        if (upnpState != null && upnpState.hasRealSong()
                && (upnpState.isPlaying() || upnpState.isPreparing())) {
            return new MusicInfo(upnpState.getTitle(), upnpState.getArtist());
        }
        MusicInfo anySessionInfo = null;
        for (MediaController controller : getActiveControllers()) {
            if (controller.getMetadata() == null) {
                continue;
            }
            CharSequence title = controller.getMetadata().getText(
                    android.media.MediaMetadata.METADATA_KEY_TITLE);
            if (TextUtils.isEmpty(title)) {
                continue;
            }
            CharSequence artist = controller.getMetadata().getText(
                    android.media.MediaMetadata.METADATA_KEY_ARTIST);
            MusicInfo info = new MusicInfo(title,
                    TextUtils.isEmpty(artist) ? controller.getPackageName() : artist);
            if (isControllerPlaying(controller)) {
                return info;
            }
            if (anySessionInfo == null) {
                anySessionInfo = info;
            }
        }
        if (upnpState != null && upnpState.hasRealSong()) {
            return new MusicInfo(upnpState.getTitle(), upnpState.getArtist());
        }
        return anySessionInfo;
    }

    private List<MediaController> getActiveControllers() {
        if (mMediaSessionManager == null) {
            return Collections.emptyList();
        }
        try {
            return mMediaSessionManager.getActiveSessions(null);
        } catch (SecurityException e) {
            // Media-session access is only available on some system builds.
            return Collections.emptyList();
        }
    }

    private static boolean isControllerPlaying(MediaController controller) {
        PlaybackState playback = controller.getPlaybackState();
        return playback != null && playback.getState() == PlaybackState.STATE_PLAYING;
    }
}
