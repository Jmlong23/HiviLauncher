package com.hivi.launcher.microphone.model;

import android.content.Context;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;

import com.hivi.launcher.audio.AudioRouteController;

public final class MicrophoneModel {
    private static final int DEFAULT_KARAOKE_VOLUME = 50;

    public enum VolumeChannel {
        AMPLIFIER,
        MICROPHONE,
        EFFECT
    }

    public interface Listener {
        void onMicrophonePageStateChanged(int amplifierVolumePercent, boolean amplifierMuted,
                int microphoneVolumePercent, boolean microphoneMuted, int effectVolumePercent,
                boolean effectMuted, boolean microphoneConnected);
    }

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
    private final AudioRouteController mAudioRouteController = AudioRouteController.getInstance();
    private final AudioRouteController.AmplifierVolumeListener mAmplifierVolumeListener =
            (volumePercent, muted) -> dispatchPageState();

    private AudioManager mAudioManager;
    private Listener mListener;
    private Context mContext;
    private boolean mAudioDeviceCallbackRegistered;
    private int mMicrophoneVolumePercent = DEFAULT_KARAOKE_VOLUME;
    private int mEffectVolumePercent = DEFAULT_KARAOKE_VOLUME;
    private int mLastMicrophoneVolumePercent = DEFAULT_KARAOKE_VOLUME;
    private int mLastEffectVolumePercent = DEFAULT_KARAOKE_VOLUME;
    private boolean mMicrophoneMuted;
    private boolean mEffectMuted;

    public void start(Context context, Listener listener) {
        if (context == null) {
            return;
        }
        mContext = context.getApplicationContext();
        mListener = listener;
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mMicrophoneVolumePercent = mAudioRouteController.getStoredVolume(mContext,
                AudioRouteController.SERIAL_PORT_MIC_VOLUME_KEY, DEFAULT_KARAOKE_VOLUME);
        mEffectVolumePercent = mAudioRouteController.getStoredVolume(mContext,
                AudioRouteController.SERIAL_PORT_CMD_SFX_KEY, DEFAULT_KARAOKE_VOLUME);
        mLastMicrophoneVolumePercent = mMicrophoneVolumePercent;
        mLastEffectVolumePercent = mEffectVolumePercent;
        mAudioRouteController.addAmplifierVolumeListener(mAmplifierVolumeListener);
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
        mAudioRouteController.removeAmplifierVolumeListener(mAmplifierVolumeListener);
        mAudioDeviceCallbackRegistered = false;
        mAudioManager = null;
        mListener = null;
        mContext = null;
    }

    public void adjustVolume(VolumeChannel channel, int direction) {
        if (channel == VolumeChannel.AMPLIFIER) {
            adjustAmplifierVolume(direction);
            return;
        }
        setKaraokeVolume(channel, getKaraokeVolume(channel) + getVolumeAdjustment(direction));
    }

    public void setVolumePercent(VolumeChannel channel, int volumePercent) {
        if (channel == VolumeChannel.AMPLIFIER) {
            setAmplifierVolume(volumePercent);
            return;
        }
        setKaraokeVolume(channel, volumePercent);
    }

    public void toggleMute(VolumeChannel channel) {
        if (channel == VolumeChannel.AMPLIFIER) {
            toggleAmplifierMute();
            return;
        }
        toggleKaraokeMute(channel);
    }

    private void adjustAmplifierVolume(int direction) {
        mAudioRouteController.adjustAmplifierVolume(direction);
    }

    private void setAmplifierVolume(int volumePercent) {
        mAudioRouteController.setAmplifierVolume(volumePercent);
    }

    private void toggleAmplifierMute() {
        mAudioRouteController.toggleAmplifierMute();
    }

    private void setKaraokeVolume(VolumeChannel channel, int volumePercent) {
        int volume = clampVolume(volumePercent);
        if (channel == VolumeChannel.MICROPHONE) {
            mMicrophoneVolumePercent = volume;
            if (volume > 0) {
                mLastMicrophoneVolumePercent = volume;
            }
            mMicrophoneMuted = false;
            AudioRouteController.getInstance().setMicrophoneVolume(volume);
        } else {
            mEffectVolumePercent = volume;
            if (volume > 0) {
                mLastEffectVolumePercent = volume;
            }
            mEffectMuted = false;
            AudioRouteController.getInstance().setEffectVolume(volume);
        }
        dispatchPageState();
    }

    private void toggleKaraokeMute(VolumeChannel channel) {
        if (channel == VolumeChannel.MICROPHONE) {
            if (mMicrophoneMuted) {
                mMicrophoneMuted = false;
                mMicrophoneVolumePercent = getRestoreVolume(mLastMicrophoneVolumePercent);
                AudioRouteController.getInstance().setMicrophoneVolume(mMicrophoneVolumePercent);
            } else {
                if (mMicrophoneVolumePercent > 0) {
                    mLastMicrophoneVolumePercent = mMicrophoneVolumePercent;
                }
                mMicrophoneMuted = true;
                AudioRouteController.getInstance().setMicrophoneVolume(0);
            }
        } else {
            if (mEffectMuted) {
                mEffectMuted = false;
                mEffectVolumePercent = getRestoreVolume(mLastEffectVolumePercent);
                AudioRouteController.getInstance().setEffectVolume(mEffectVolumePercent);
            } else {
                if (mEffectVolumePercent > 0) {
                    mLastEffectVolumePercent = mEffectVolumePercent;
                }
                mEffectMuted = true;
                AudioRouteController.getInstance().setEffectVolume(0);
            }
        }
        dispatchPageState();
    }

    private void dispatchPageState() {
        Listener listener = mListener;
        if (listener != null) {
            listener.onMicrophonePageStateChanged(mAudioRouteController.getAmplifierVolumePercent(),
                    mAudioRouteController.isAmplifierMuted(),
                    mMicrophoneVolumePercent, mMicrophoneMuted, mEffectVolumePercent,
                    mEffectMuted, isExternalMicrophoneConnected());
        }
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

    private int getKaraokeVolume(VolumeChannel channel) {
        return channel == VolumeChannel.MICROPHONE ? mMicrophoneVolumePercent
                : mEffectVolumePercent;
    }

    private int getVolumeAdjustment(int direction) {
        if (direction > 0) {
            return 1;
        }
        if (direction < 0) {
            return -1;
        }
        return 0;
    }

    private int getRestoreVolume(int volumePercent) {
        return volumePercent > 0 ? volumePercent : DEFAULT_KARAOKE_VOLUME;
    }

    private int clampVolume(int volumePercent) {
        return Math.max(0, Math.min(100, volumePercent));
    }
}
