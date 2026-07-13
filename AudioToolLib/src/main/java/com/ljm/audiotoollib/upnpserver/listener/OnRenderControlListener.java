package com.ljm.audiotoollib.upnpserver.listener;

public interface OnRenderControlListener {
    int onGetVolume();

    void onSetVolume(int value);

    String onGetAudioMode();

    void onSetAudioMode(String mode);

    void onSetRemoteControlMode(int value);

    void onSetAudioBackground(String value);
    void onSelectAudioBackground(String AudioContext);
    void onCancelAudioBackground();
    void onSetExtraInfo(String info);
}
