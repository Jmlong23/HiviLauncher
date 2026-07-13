package com.ljm.audiotoollib.upnpserver.listener;

import com.ljm.audiotoollib.upnpserver.entity.PlayMusicListType;

public interface OnPlayMusicByIndexListener {
    void onPlay(PlayMusicListType playMusicList);

    int getLoopMode();

    void setLoopMode(int mode);
}
