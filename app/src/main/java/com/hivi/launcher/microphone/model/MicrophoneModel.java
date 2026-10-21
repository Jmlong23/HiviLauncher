package com.hivi.launcher.microphone.model;

import android.content.Context;

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

    private final AudioRouteController mAudioRouteController = AudioRouteController.getInstance();
    private final AudioRouteController.AmplifierVolumeListener mAmplifierVolumeListener =
            (volumePercent, muted) -> dispatchPageState();
    private final AudioRouteController.McuMicrophoneListener mMcuMicrophoneListener =
            connected -> dispatchPageState();

    private Listener mListener;
    private Context mContext;
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
        mMicrophoneVolumePercent = mAudioRouteController.getStoredVolume(mContext,
                AudioRouteController.SERIAL_PORT_MIC_VOLUME_KEY, DEFAULT_KARAOKE_VOLUME);
        mEffectVolumePercent = mAudioRouteController.getStoredVolume(mContext,
                AudioRouteController.SERIAL_PORT_CMD_SFX_KEY, DEFAULT_KARAOKE_VOLUME);
        mLastMicrophoneVolumePercent = mMicrophoneVolumePercent;
        mLastEffectVolumePercent = mEffectVolumePercent;
        mMicrophoneMuted = mMicrophoneVolumePercent == 0;
        mEffectMuted = mEffectVolumePercent == 0;
        mAudioRouteController.addAmplifierVolumeListener(mAmplifierVolumeListener);
        mAudioRouteController.addMcuMicrophoneListener(mMcuMicrophoneListener);
        dispatchPageState();
    }

    public void stop() {
        mAudioRouteController.removeAmplifierVolumeListener(mAmplifierVolumeListener);
        mAudioRouteController.removeMcuMicrophoneListener(mMcuMicrophoneListener);
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
            if (volume == 0) {
                if (mMicrophoneVolumePercent > 0) {
                    mLastMicrophoneVolumePercent = mMicrophoneVolumePercent;
                }
                mMicrophoneVolumePercent = 0;
                mMicrophoneMuted = true;
            } else {
                mMicrophoneVolumePercent = volume;
                mLastMicrophoneVolumePercent = volume;
                mMicrophoneMuted = false;
            }
            AudioRouteController.getInstance().setMicrophoneVolume(volume);
        } else {
            if (volume == 0) {
                if (mEffectVolumePercent > 0) {
                    mLastEffectVolumePercent = mEffectVolumePercent;
                }
                mEffectVolumePercent = 0;
                mEffectMuted = true;
            } else {
                mEffectVolumePercent = volume;
                mLastEffectVolumePercent = volume;
                mEffectMuted = false;
            }
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
                mMicrophoneVolumePercent = 0;
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
                mEffectVolumePercent = 0;
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
                    mEffectMuted, mAudioRouteController.isMicrophoneConnected());
        }
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
