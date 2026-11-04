package com.hivi.launcher.bluetooth.model;

import android.content.Context;

import com.hivi.launcher.audio.SpectrumSubscription;

/**
 * Owns the MCU Bluetooth state and the FFT capture used by the input page's spectrum.
 */
public final class BluetoothModel implements McuBluetoothController.Listener {
    public interface Listener {
        void onBluetoothPageStateChanged(BluetoothPageState state);

        void onBluetoothFftData(byte[] fftData);
    }

    private final McuBluetoothController mMcuBluetoothController =
            McuBluetoothController.getInstance();
    private final SpectrumSubscription mSpectrumSubscription = new SpectrumSubscription();

    private Listener mListener;

    public void start(Context context, Listener listener) {
        if (context == null) {
            return;
        }
        mListener = listener;
        mMcuBluetoothController.start(context);
        mMcuBluetoothController.addListener(this);
        mSpectrumSubscription.start(fftData -> {
            Listener currentListener = mListener;
            if (currentListener != null) {
                currentListener.onBluetoothFftData(fftData);
            }
        });
        dispatchState();
    }

    public void stop() {
        mMcuBluetoothController.removeListener(this);
        mSpectrumSubscription.stop();
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
                mMcuBluetoothController.getTrackTitle(),
                mMcuBluetoothController.getTrackLyric()));
    }
}
