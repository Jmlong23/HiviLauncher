package com.hivi.launcher.bluetooth.model;

import android.content.Context;

import com.hivi.launcher.audio.AudioRouteController;
import com.hivi.launcher.music.model.BluetoothMediaController;
import com.hivi.launcher.music.model.BluetoothPlaybackState;

/**
 * Owns Bluetooth media and amplifier-volume integrations used by the input page.
 */
public final class BluetoothModel implements BluetoothMediaController.Listener {
    public interface Listener {
        void onBluetoothPageStateChanged(BluetoothPageState state);
    }

    private final BluetoothMediaController mBluetoothMediaController =
            BluetoothMediaController.getInstance();
    private final AudioRouteController mAudioRouteController = AudioRouteController.getInstance();
    private final AudioRouteController.AmplifierVolumeListener mAmplifierVolumeListener =
            (volumePercent, muted) -> dispatchState();

    private Listener mListener;
    private boolean mStarted;

    public void start(Context context, Listener listener) {
        if (context == null) {
            return;
        }
        mListener = listener;
        mAudioRouteController.addAmplifierVolumeListener(mAmplifierVolumeListener);
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
        mAudioRouteController.removeAmplifierVolumeListener(mAmplifierVolumeListener);
        mStarted = false;
        mListener = null;
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
        mAudioRouteController.adjustAmplifierVolume(direction);
    }

    public void setVolumePercent(int volumePercent) {
        mAudioRouteController.setAmplifierVolume(volumePercent);
    }

    public void toggleMute() {
        mAudioRouteController.toggleAmplifierMute();
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
                mAudioRouteController.getAmplifierVolumePercent(),
                mAudioRouteController.isAmplifierMuted()));
    }
}
