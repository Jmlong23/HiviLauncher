package com.hivi.launcher.microphone.model;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;

public final class MicrophoneModel {
    private static final String ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION";

    public interface Listener {
        void onMicrophonePageStateChanged(int volumePercent, boolean muted,
                boolean microphoneConnected);
    }

    private final BroadcastReceiver mVolumeChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            dispatchPageState();
        }
    };
    private final AudioDeviceCallback mAudioDeviceCallback = new AudioDeviceCallback() {
        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            dispatchPageState();
        }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
            dispatchPageState();
        }
    };

    private AudioManager mAudioManager;
    private Listener mListener;
    private Context mContext;
    private boolean mReceiverRegistered;
    private boolean mAudioDeviceCallbackRegistered;

    public void start(Context context, Listener listener) {
        if (context == null) {
            return;
        }
        mContext = context.getApplicationContext();
        mListener = listener;
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        if (!mReceiverRegistered) {
            mContext.registerReceiver(mVolumeChangedReceiver, new IntentFilter(ACTION_VOLUME_CHANGED));
            mReceiverRegistered = true;
        }
        if (mAudioManager != null && !mAudioDeviceCallbackRegistered) {
            mAudioManager.registerAudioDeviceCallback(mAudioDeviceCallback, null);
            mAudioDeviceCallbackRegistered = true;
        }
        dispatchPageState();
    }

    public void stop() {
        if (mAudioDeviceCallbackRegistered && mAudioManager != null) {
            mAudioManager.unregisterAudioDeviceCallback(mAudioDeviceCallback);
        }
        mAudioDeviceCallbackRegistered = false;
        if (mReceiverRegistered && mContext != null) {
            try {
                mContext.unregisterReceiver(mVolumeChangedReceiver);
            } catch (IllegalArgumentException ignored) {
                // The receiver may already have been unregistered while the activity is stopping.
            }
        }
        mReceiverRegistered = false;
        mAudioManager = null;
        mListener = null;
        mContext = null;
    }

    public void adjustVolume(int direction) {
        if (mAudioManager == null) {
            return;
        }
        mAudioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction,
                AudioManager.FLAG_PLAY_SOUND);
        dispatchPageState();
    }

    public void setVolumePercent(int volumePercent) {
        if (mAudioManager == null) {
            return;
        }
        int max = Math.max(1, mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        int target = Math.round(Math.max(0, Math.min(100, volumePercent)) * max / 100f);
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target,
                AudioManager.FLAG_PLAY_SOUND);
        dispatchPageState();
    }

    public void toggleMute() {
        if (mAudioManager == null) {
            return;
        }
        mAudioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_PLAY_SOUND);
        dispatchPageState();
    }

    private void dispatchPageState() {
        Listener listener = mListener;
        if (listener != null) {
            listener.onMicrophonePageStateChanged(getVolumePercent(), isMuted(),
                    isExternalMicrophoneConnected());
        }
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

    private boolean isExternalMicrophoneConnected() {
        if (mAudioManager == null) {
            return false;
        }
        AudioDeviceInfo[] inputDevices = mAudioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);
        for (AudioDeviceInfo device : inputDevices) {
            switch (device.getType()) {
                case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
                case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                case AudioDeviceInfo.TYPE_USB_ACCESSORY:
                case AudioDeviceInfo.TYPE_USB_DEVICE:
                case AudioDeviceInfo.TYPE_USB_HEADSET:
                    return true;
                default:
                    break;
            }
        }
        return false;
    }
}
