package com.hivi.launcher.music.model;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.browse.MediaBrowser;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads and controls the media session created by Android's Bluetooth AVRCP controller service.
 *
 * <p>The target device is an A2DP Sink. Audio is decoded by the system Bluetooth stack, while
 * this class uses the accompanying AVRCP media browser service only for metadata and transport
 * controls.</p>
 */
public final class BluetoothMediaController {
    public static final String ACTION_A2DP_SINK_CONNECTION_STATE_CHANGED =
            "android.bluetooth.a2dp-sink.profile.action.CONNECTION_STATE_CHANGED";

    private static final String TAG = "BluetoothMediaController";
    private static final int PROFILE_A2DP_SINK = 11;
    private static final long PROGRESS_TICK_MS = 1_000L;

    private static final ComponentName BLUETOOTH_MEDIA_BROWSER_SERVICE = new ComponentName(
            "com.android.bluetooth",
            "com.android.bluetooth.avrcpcontroller.BluetoothMediaBrowserService");

    private static final BluetoothMediaController INSTANCE = new BluetoothMediaController();

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final List<Listener> mListeners = new ArrayList<>();
    private final Runnable mProgressTicker = new Runnable() {
        @Override
        public void run() {
            if (mListeners.isEmpty()) {
                return;
            }
            notifyPlaybackChanged();
            mMainHandler.postDelayed(this, PROGRESS_TICK_MS);
        }
    };

    private Context mAppContext;
    private MediaBrowser mMediaBrowser;
    private MediaController mMediaController;
    private BluetoothProfile mA2dpSinkProfile;
    private BluetoothDevice mConnectedDevice;
    private boolean mStarted;
    private boolean mA2dpSinkProfileRequested;

    public interface Listener {
        void onBluetoothPlaybackChanged(BluetoothPlaybackState state);
    }

    public static BluetoothMediaController getInstance() {
        return INSTANCE;
    }

    private BluetoothMediaController() {
    }

    public void start(Context context) {
        if (context == null) {
            return;
        }
        mAppContext = context.getApplicationContext();
        mStarted = true;
        runOnMainThread(() -> {
            connectIfNeeded();
            requestA2dpSinkProfileIfNeeded();
        });
    }

    public void refresh() {
        runOnMainThread(() -> {
            if (!mStarted || mAppContext == null) {
                return;
            }
            if (mMediaBrowser != null && !mMediaBrowser.isConnected()) {
                mMediaBrowser.disconnect();
                mMediaBrowser = null;
            }
            connectIfNeeded();
            updateConnectedDeviceFromProfile();
            requestA2dpSinkProfileIfNeeded();
            notifyPlaybackChanged();
        });
    }

    public void onBluetoothConnectionStateChanged(BluetoothDevice device, String action,
            int connectionState) {
        runOnMainThread(() -> {
            if (device != null) {
                if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)
                        || connectionState == BluetoothProfile.STATE_CONNECTED) {
                    mConnectedDevice = device;
                } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)
                        || connectionState == BluetoothProfile.STATE_DISCONNECTED) {
                    if (mConnectedDevice == null
                            || mConnectedDevice.getAddress().equals(device.getAddress())) {
                        mConnectedDevice = null;
                    }
                }
            } else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)
                    && connectionState == BluetoothAdapter.STATE_OFF) {
                mConnectedDevice = null;
            }
            refresh();
        });
    }

    public void addListener(Listener listener) {
        if (listener == null || mListeners.contains(listener)) {
            return;
        }
        mListeners.add(listener);
        listener.onBluetoothPlaybackChanged(getCurrentState());
        if (mListeners.size() == 1) {
            mMainHandler.removeCallbacks(mProgressTicker);
            mMainHandler.post(mProgressTicker);
        }
    }

    public void removeListener(Listener listener) {
        mListeners.remove(listener);
        if (mListeners.isEmpty()) {
            mMainHandler.removeCallbacks(mProgressTicker);
        }
    }

    public boolean isBluetoothAudioConnected() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            return false;
        }
        return isProfileConnected(adapter, PROFILE_A2DP_SINK)
                || isProfileConnected(adapter, BluetoothProfile.A2DP);
    }

    public String getConnectedDeviceName() {
        updateConnectedDeviceFromProfile();
        BluetoothDevice device = mConnectedDevice;
        if (device == null) {
            return "";
        }
        try {
            String name = device.getName();
            return TextUtils.isEmpty(name) ? "" : name;
        } catch (SecurityException e) {
            Log.w(TAG, "Unable to read connected Bluetooth device name", e);
            return "";
        }
    }

    public boolean isPlaybackControlAvailable() {
        return isBluetoothAudioConnected() && mMediaController != null;
    }

    public BluetoothPlaybackState getCurrentState() {
        MediaController controller = mMediaController;
        if (controller == null) {
            return BluetoothPlaybackState.empty();
        }

        MediaMetadata metadata = controller.getMetadata();
        PlaybackState playbackState = controller.getPlaybackState();

        CharSequence lyric = getMetadataText(metadata, MediaMetadata.METADATA_KEY_TITLE);
        CharSequence displayTitle = getMetadataText(metadata,
                MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        CharSequence artistMetadata = getMetadataText(metadata, MediaMetadata.METADATA_KEY_ARTIST);
        if (TextUtils.isEmpty(artistMetadata)) {
            artistMetadata = getMetadataText(metadata, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
        }
        BluetoothTrackMetadata trackMetadata = splitTrackMetadata(artistMetadata, displayTitle);
        CharSequence title = trackMetadata.title;
        CharSequence artist = trackMetadata.artist;
        CharSequence album = getMetadataText(metadata, MediaMetadata.METADATA_KEY_ALBUM);
        if (TextUtils.isEmpty(album)) {
            album = getMetadataText(metadata, MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION);
        }
        long duration = metadata == null ? 0L
                : Math.max(0L, metadata.getLong(MediaMetadata.METADATA_KEY_DURATION));
        long position = resolvePosition(playbackState, duration);
        boolean playing = playbackState != null
                && playbackState.getState() == PlaybackState.STATE_PLAYING;
        return new BluetoothPlaybackState(title, artist, album, lyric, position, duration, playing);
    }

    public void playOrPause() {
        MediaController controller = mMediaController;
        if (controller == null) {
            return;
        }
        try {
            if (getCurrentState().isPlaying()) {
                controller.getTransportControls().pause();
            } else {
                controller.getTransportControls().play();
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to toggle Bluetooth playback", e);
        }
    }

    public void previous() {
        sendTransportCommand(TransportCommand.PREVIOUS);
    }

    public void next() {
        sendTransportCommand(TransportCommand.NEXT);
    }

    public void seekTo(long positionMs) {
        MediaController controller = mMediaController;
        if (controller == null) {
            return;
        }
        try {
            controller.getTransportControls().seekTo(Math.max(0L, positionMs));
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to seek Bluetooth playback", e);
        }
    }

    private void connectIfNeeded() {
        if (!mStarted || mAppContext == null || mMediaBrowser != null) {
            return;
        }
        mMediaBrowser = new MediaBrowser(mAppContext, BLUETOOTH_MEDIA_BROWSER_SERVICE,
                mBrowserConnectionCallback, null);
        try {
            mMediaBrowser.connect();
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to connect Bluetooth media browser service", e);
            mMediaBrowser = null;
            notifyPlaybackChanged();
        }
    }

    private void requestA2dpSinkProfileIfNeeded() {
        if (mA2dpSinkProfile != null || mA2dpSinkProfileRequested || mAppContext == null) {
            return;
        }
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            return;
        }
        try {
            mA2dpSinkProfileRequested = adapter.getProfileProxy(mAppContext,
                    mA2dpSinkProfileListener, PROFILE_A2DP_SINK);
        } catch (SecurityException | IllegalArgumentException e) {
            mA2dpSinkProfileRequested = false;
            Log.w(TAG, "Unable to request A2DP Sink profile proxy", e);
        }
    }

    private final BluetoothProfile.ServiceListener mA2dpSinkProfileListener =
            new BluetoothProfile.ServiceListener() {
                @Override
                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    if (profile != PROFILE_A2DP_SINK) {
                        return;
                    }
                    mA2dpSinkProfile = proxy;
                    mA2dpSinkProfileRequested = true;
                    updateConnectedDeviceFromProfile();
                    notifyPlaybackChanged();
                }

                @Override
                public void onServiceDisconnected(int profile) {
                    if (profile != PROFILE_A2DP_SINK) {
                        return;
                    }
                    mA2dpSinkProfile = null;
                    mA2dpSinkProfileRequested = false;
                    mConnectedDevice = null;
                    notifyPlaybackChanged();
                }
            };

    private final MediaBrowser.ConnectionCallback mBrowserConnectionCallback =
            new MediaBrowser.ConnectionCallback() {
                @Override
                public void onConnected() {
                    if (mMediaBrowser == null || !mMediaBrowser.isConnected()) {
                        return;
                    }
                    try {
                        setMediaController(new MediaController(mAppContext,
                                mMediaBrowser.getSessionToken()));
                    } catch (RuntimeException e) {
                        Log.w(TAG, "Unable to create Bluetooth media controller", e);
                        setMediaController(null);
                    }
                    notifyPlaybackChanged();
                }

                @Override
                public void onConnectionSuspended() {
                    setMediaController(null);
                    notifyPlaybackChanged();
                }

                @Override
                public void onConnectionFailed() {
                    setMediaController(null);
                    if (mMediaBrowser != null) {
                        mMediaBrowser.disconnect();
                        mMediaBrowser = null;
                    }
                    notifyPlaybackChanged();
                }
            };

    private final MediaController.Callback mMediaControllerCallback =
            new MediaController.Callback() {
                @Override
                public void onMetadataChanged(MediaMetadata metadata) {
                    notifyPlaybackChanged();
                }

                @Override
                public void onPlaybackStateChanged(PlaybackState state) {
                    notifyPlaybackChanged();
                }

                @Override
                public void onSessionDestroyed() {
                    setMediaController(null);
                    if (mMediaBrowser != null) {
                        mMediaBrowser.disconnect();
                        mMediaBrowser = null;
                    }
                    notifyPlaybackChanged();
                }
            };

    private void setMediaController(MediaController controller) {
        if (mMediaController != null) {
            mMediaController.unregisterCallback(mMediaControllerCallback);
        }
        mMediaController = controller;
        if (mMediaController != null) {
            mMediaController.registerCallback(mMediaControllerCallback);
        }
    }

    private void sendTransportCommand(TransportCommand command) {
        MediaController controller = mMediaController;
        if (controller == null) {
            return;
        }
        try {
            if (command == TransportCommand.PREVIOUS) {
                controller.getTransportControls().skipToPrevious();
            } else {
                controller.getTransportControls().skipToNext();
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to send Bluetooth transport command", e);
        }
    }

    private void updateConnectedDeviceFromProfile() {
        if (mA2dpSinkProfile == null) {
            return;
        }
        try {
            List<BluetoothDevice> devices = mA2dpSinkProfile.getConnectedDevices();
            mConnectedDevice = devices == null || devices.isEmpty() ? null : devices.get(0);
        } catch (SecurityException | IllegalArgumentException e) {
            mConnectedDevice = null;
        }
    }

    private static boolean isProfileConnected(BluetoothAdapter adapter, int profile) {
        try {
            return adapter.getProfileConnectionState(profile) == BluetoothProfile.STATE_CONNECTED;
        } catch (SecurityException | IllegalArgumentException e) {
            return false;
        }
    }

    private static CharSequence getMetadataText(MediaMetadata metadata, String key) {
        return metadata == null ? "" : metadata.getText(key);
    }

    private static CharSequence firstNonEmpty(CharSequence first, CharSequence second) {
        return !TextUtils.isEmpty(first) ? first : second;
    }

    /**
     * The target Bluetooth source stores "<song title> - <artist>" in ARTIST and uses TITLE
     * for the current lyric line. Split only the first exact separator so the singer portion is
     * preserved verbatim.
     */
    private static BluetoothTrackMetadata splitTrackMetadata(CharSequence artistMetadata,
            CharSequence displayTitle) {
        String combined = artistMetadata == null ? "" : artistMetadata.toString();
        int separatorIndex = combined.indexOf(" - ");
        if (separatorIndex > 0 && separatorIndex + 3 < combined.length()) {
            return new BluetoothTrackMetadata(combined.substring(0, separatorIndex).trim(),
                    combined.substring(separatorIndex + 3).trim());
        }
        return new BluetoothTrackMetadata(firstNonEmpty(displayTitle, combined), combined);
    }

    private static long resolvePosition(PlaybackState state, long duration) {
        if (state == null) {
            return 0L;
        }
        long position = Math.max(0L, state.getPosition());
        if (state.getState() == PlaybackState.STATE_PLAYING) {
            long elapsed = Math.max(0L, SystemClock.elapsedRealtime()
                    - state.getLastPositionUpdateTime());
            position += Math.round((double) elapsed * state.getPlaybackSpeed());
        }
        return duration > 0L ? Math.min(position, duration) : position;
    }

    private void notifyPlaybackChanged() {
        if (Looper.myLooper() != mMainHandler.getLooper()) {
            mMainHandler.post(this::notifyPlaybackChanged);
            return;
        }
        BluetoothPlaybackState state = getCurrentState();
        for (Listener listener : new ArrayList<>(mListeners)) {
            listener.onBluetoothPlaybackChanged(state);
        }
    }

    private void runOnMainThread(Runnable action) {
        if (Looper.myLooper() == mMainHandler.getLooper()) {
            action.run();
        } else {
            mMainHandler.post(action);
        }
    }

    private enum TransportCommand {
        PREVIOUS,
        NEXT
    }

    private static final class BluetoothTrackMetadata {
        private final CharSequence title;
        private final CharSequence artist;

        private BluetoothTrackMetadata(CharSequence title, CharSequence artist) {
            this.title = title;
            this.artist = artist;
        }
    }
}
