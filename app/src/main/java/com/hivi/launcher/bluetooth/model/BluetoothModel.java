package com.hivi.launcher.bluetooth.model;

import android.content.Context;
import android.media.AudioManager;

import com.hivi.launcher.music.model.BluetoothMediaController;
import com.hivi.launcher.music.model.BluetoothPlaybackState;

/**
 * Owns the system Bluetooth media and amplifier-volume integrations used by the input page.
 */
public final class BluetoothModel implements BluetoothMediaController.Listener {
    public interface Listener {
        void onBluetoothPageStateChanged(BluetoothPageState state);
    }

    private final BluetoothMediaController mBluetoothMediaController =
            BluetoothMediaController.getInstance();

    private AudioManager mAudioManager;
    private Listener mListener;
    private boolean mStarted;

    public void start(Context context, Listener listener) {
        if (context == null) {
            return;
        }
        mListener = listener;
        mAudioManager = (AudioManager) context.getApplicationContext().getSystemService(
                Context.AUDIO_SERVICE);
        if (!mStarted) {
            mStarted = true;
            mBluetoothMediaController.start(context);
            mBluetoothMediaController.addListener(this);
        }
        mBluetoothMediaController.refresh();
        dispatchState();
    }

    public void stop() {
        if (mStarted) {
            mBluetoothMediaController.removeListener(this);
        }
        mStarted = false;
        mListener = null;
        mAudioManager = null;
    }

    public void togglePlayback() {
        mBluetoothMediaController.playOrPause();
    }

    public void nextTrack() {
        mBluetoothMediaController.next();
    }

    public boolean disconnectConnectedDevice() {
        return mBluetoothMediaController.disconnectConnectedDevice();
    }

    public boolean resetConnectedDevice() {
        return mBluetoothMediaController.resetConnectedDevice();
    }

    public void adjustVolume(int direction) {
        if (mAudioManager == null) {
            return;
        }
        mAudioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction,
                AudioManager.FLAG_PLAY_SOUND);
        dispatchState();
    }

    public void setVolumePercent(int volumePercent) {
        if (mAudioManager == null) {
            return;
        }
        int max = Math.max(1, mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        int target = Math.round(Math.max(0, Math.min(100, volumePercent)) * max / 100f);
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target,
                AudioManager.FLAG_PLAY_SOUND);
        dispatchState();
    }

    public void toggleMute() {
        if (mAudioManager == null) {
            return;
        }
        mAudioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_PLAY_SOUND);
        dispatchState();
    }

    @Override
    public void onBluetoothPlaybackChanged(BluetoothPlaybackState state) {
        dispatchState(state);
    }

    private void dispatchState() {
        dispatchState(mBluetoothMediaController.getCurrentState());
    }

    private void dispatchState(BluetoothPlaybackState playbackState) {
        Listener listener = mListener;
        if (listener == null) {
            return;
        }
        listener.onBluetoothPageStateChanged(new BluetoothPageState(
                mBluetoothMediaController.isBluetoothAudioConnected(),
                mBluetoothMediaController.getConnectedDeviceName(),
                mBluetoothMediaController.isPlaybackControlAvailable(),
                playbackState,
                getVolumePercent(),
                isMuted()));
    }

    private int getVolumePercent() {
        if (mAudioManager == null) {
            return 0;
        }
        int current = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int max = Math.max(1, mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        return Math.round(current * 100f / max);
    }

    private boolean isMuted() {
        return mAudioManager != null && mAudioManager.isStreamMute(AudioManager.STREAM_MUSIC);
    }
}
