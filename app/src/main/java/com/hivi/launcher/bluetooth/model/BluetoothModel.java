package com.hivi.launcher.bluetooth.model;

import android.content.Context;

import com.hivi.launcher.audio.AudioRouteController;

/**
 * Owns the MCU Bluetooth state and amplifier-volume integrations used by the input page.
 */
public final class BluetoothModel implements McuBluetoothController.Listener {
    public interface Listener {
        void onBluetoothPageStateChanged(BluetoothPageState state);
    }

    private final McuBluetoothController mMcuBluetoothController =
            McuBluetoothController.getInstance();
    private final AudioRouteController mAudioRouteController = AudioRouteController.getInstance();
    private final AudioRouteController.AmplifierVolumeListener mAmplifierVolumeListener =
            (volumePercent, muted) -> dispatchState();

    private Listener mListener;

    public void start(Context context, Listener listener) {
        if (context == null) {
            return;
        }
        mListener = listener;
        mMcuBluetoothController.start(context);
        mMcuBluetoothController.addListener(this);
        mAudioRouteController.addAmplifierVolumeListener(mAmplifierVolumeListener);
        dispatchState();
    }

    public void stop() {
        mMcuBluetoothController.removeListener(this);
        mAudioRouteController.removeAmplifierVolumeListener(mAmplifierVolumeListener);
        mListener = null;
    }

    public void togglePlayback() {
        mMcuBluetoothController.togglePlayback();
    }

    public void nextTrack() {
        mMcuBluetoothController.next();
    }

    public void previousTrack() {
        mMcuBluetoothController.previous();
    }

    public void disconnect() {
        mMcuBluetoothController.disconnect();
    }

    public void connect() {
        mMcuBluetoothController.connect();
    }

    public void reset() {
        mMcuBluetoothController.clearPairings();
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
    public void onBluetoothStateChanged() {
        dispatchState();
    }

    @Override
    public void onBluetoothPairingsCleared() {
        // Confirmation toasts are shown globally by the main presenter; the page simply
        // re-renders from the updated state.
        dispatchState();
    }

    private void dispatchState() {
        Listener listener = mListener;
        if (listener == null) {
            return;
        }
        listener.onBluetoothPageStateChanged(new BluetoothPageState(
                mMcuBluetoothController.isConnected(),
                mMcuBluetoothController.getPhoneName(),
                mMcuBluetoothController.isConnected(),
                mMcuBluetoothController.isPlaying(),
                mAudioRouteController.getAmplifierVolumePercent(),
                mAudioRouteController.isAmplifierMuted(),
                mMcuBluetoothController.getTrackTitle(),
                mMcuBluetoothController.getTrackLyric()));
    }
}
