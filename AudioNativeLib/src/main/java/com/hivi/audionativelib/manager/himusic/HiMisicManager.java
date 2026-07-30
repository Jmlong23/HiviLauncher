package com.hivi.audionativelib.manager.himusic;

import com.hivi.audionativelib.NativeLog;

public class HiMisicManager {
    private static final String TAG = "HiMisicManager";

    public native int initDevices();

    public native int dmsdpGetAudioHandler();


    public void onMediaChange(MediaInfo info) {
        NativeLog.i(TAG,"onMediaChange");

    }

    public void onPlayState(int value) {
        NativeLog.i(TAG,"onPlayState value: " + value);

    }

    public void onPlayProgress(int v1, int v2) {
//        Log.i(TAG,"onPlayProgress");

    }

}
