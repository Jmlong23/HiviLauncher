package com.ljm.audiotoollib.upnpserver.listener;

import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.widget.VideoView;

import com.ljm.audiotoollib.upnpserver.entity.InfoEx;

public interface OnMediaControlListener {
    void play();

    void pause();

    void previous();

    void next();

    void seek(long position);

    void onLyricReceived(String lyric, String terraceType);

    InfoEx getInfoEx();
}
