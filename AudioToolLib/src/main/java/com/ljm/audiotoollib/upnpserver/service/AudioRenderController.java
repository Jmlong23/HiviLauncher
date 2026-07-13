package com.ljm.audiotoollib.upnpserver.service;

import android.content.Context;
import android.media.AudioManager;
import android.util.Log;

import com.ljm.audiotoollib.upnpserver.cling.model.types.UnsignedIntegerFourBytes;
import com.ljm.audiotoollib.upnpserver.cling.model.types.UnsignedIntegerTwoBytes;
import com.ljm.audiotoollib.upnpserver.listener.OnRenderControlListener;

/**
 *
 */
public final class AudioRenderController implements IRendererInterface.IAudioControl {

    private static final String TAG = "AudioRenderController";
    public static final String AUDIO_MODE_WIFI = "WIFI";
    public static final String AUDIO_MODE_BT = "BT";
    private static final UnsignedIntegerTwoBytes VOLUME_MUTE = new UnsignedIntegerTwoBytes(0);
    private final UnsignedIntegerFourBytes mInstanceId;
    private final AudioManager mAudioManager;
    private UnsignedIntegerTwoBytes mLastVolume;
    private UnsignedIntegerTwoBytes mCurrentVolume;
    private String mCurrentAudioMode = AUDIO_MODE_WIFI;

    private OnRenderControlListener listener;

    private static AudioRenderController instance;

    public AudioRenderController(Context context) {
        this(context, new UnsignedIntegerFourBytes(0));
    }

    public static AudioRenderController getInstance(Context context) {
        if(instance ==  null) {
            instance = new AudioRenderController(context);
        }
        return instance;
    }

    public AudioRenderController(Context context, UnsignedIntegerFourBytes instanceId) {
        mInstanceId = instanceId;
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        final int MAX_MUSIC = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        final int volume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
//        mCurrentVolume = new UnsignedIntegerTwoBytes(volume * 100 / MAX_MUSIC);
        mLastVolume = new UnsignedIntegerTwoBytes(volume * 100 / MAX_MUSIC);
        mCurrentVolume = new UnsignedIntegerTwoBytes(50);
    }

    public UnsignedIntegerFourBytes getInstanceId() {
        return mInstanceId;
    }

    @Override
    public void setMute(String channelName, boolean desiredMute) {
        Log.i(TAG,"setMute value:" + desiredMute);
        if (desiredMute) {
            mLastVolume = mCurrentVolume;
        }
        setVolume(channelName, desiredMute ? VOLUME_MUTE : mLastVolume);
    }

    @Override
    public boolean getMute(String channelName) {
        Log.i(TAG,"getMute value:" + channelName);
        return getVolume(channelName).getValue() == 0L;
    }

    @Override
    public void setVolume(String channelName, UnsignedIntegerTwoBytes desiredVolume) {
        Log.i(TAG,"setVolume desiredVolume:" + desiredVolume);
        mCurrentVolume = desiredVolume;
        if(listener != null) {
            listener.onSetVolume(desiredVolume.getValue().intValue());
        }
//        int volume = desiredVolume.getValue().intValue();
//        int adjustVolume = volume * mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) / 100;
//        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, adjustVolume, AudioManager.FLAG_PLAY_SOUND | AudioManager.FLAG_SHOW_UI);
    }

    @Override
    public UnsignedIntegerTwoBytes getVolume(String channelName) {
        Log.i(TAG,"getVolume: " + mCurrentVolume);
        if(listener != null) {
            return new UnsignedIntegerTwoBytes(listener.onGetVolume());
        }
        return mCurrentVolume;
    }

    @Override
    public void setAudioMode(String mode) {
        Log.i(TAG, "setAudioMode mode: " + mode);
        if (mode == null) {
            return;
        }
        String audioMode = mode.trim();

        if (AUDIO_MODE_WIFI.equalsIgnoreCase(audioMode)) {
            mCurrentAudioMode = AUDIO_MODE_WIFI;
        } else if (AUDIO_MODE_BT.equalsIgnoreCase(audioMode)) {
            mCurrentAudioMode = AUDIO_MODE_BT;
        } else {
            Log.w(TAG, "setAudioMode unsupported mode: " + mode);
            return;
        }

        if (listener != null) {
            listener.onSetAudioMode(mCurrentAudioMode);
        }
    }

    @Override
    public String getAudioMode() {
        if (listener != null) {
            String mode = listener.onGetAudioMode();
            if (AUDIO_MODE_WIFI.equalsIgnoreCase(mode)) {
                mCurrentAudioMode = AUDIO_MODE_WIFI;
                return AUDIO_MODE_WIFI;
            }
            if (AUDIO_MODE_BT.equalsIgnoreCase(mode)) {
                mCurrentAudioMode = AUDIO_MODE_BT;
                return AUDIO_MODE_BT;
            }
        }
        Log.i(TAG, "getAudioMode: " + mCurrentAudioMode);
        return mCurrentAudioMode;
    }

    @Override
    public void setRemoteControlMode(UnsignedIntegerTwoBytes mode) {
        Log.i(TAG, "mode: " + mode);
        if(listener != null) {
            listener.onSetRemoteControlMode(mode.getValue().intValue());
        }
    }

    @Override
    public void setAudioBackground(String audioContext) {
        Log.i(TAG,"setAudioBackground");
//        Gson gson = new Gson();
//        AudioBackgroundInfo audioBackgroundInfo = gson.fromJson(audioContext, AudioBackgroundInfo.class);
//        Log.i(TAG,"setAudioBackground AudioContext: " + audioContext + "url: " + audioBackgroundInfo.getUrl() + "size: " + audioBackgroundInfo.getTerraceTypeList().size());
        if(listener != null) {
            listener.onSetAudioBackground(audioContext);
        }
    }

    @Override
    public void selectAudioBackground(String AudioContext) {
        Log.i(TAG, "selectAudioBackground AudioContext: " + AudioContext);
        if(listener != null){
            listener.onSelectAudioBackground(AudioContext);
        }
    }

    @Override
    public void cancelAudioBackground() {
        Log.i(TAG, "clearAudioBackground");
        if(listener != null) {
            listener.onCancelAudioBackground();
        }
    }

    @Override
    public void setExtraInfo(String info) {
        Log.i(TAG,"setExtraInfo info: " + info);
        if(listener != null){
            listener.onSetExtraInfo(info);
        }
    }


    public void setListener(OnRenderControlListener listener){
        this.listener = listener;
    }
}
