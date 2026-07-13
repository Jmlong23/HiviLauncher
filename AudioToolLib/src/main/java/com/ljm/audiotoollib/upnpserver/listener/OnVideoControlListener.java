package com.ljm.audiotoollib.upnpserver.listener;

import com.ljm.audiotoollib.upnpserver.entity.InfoEx;

public interface OnVideoControlListener {
    void playVideo();

    void pauseVideo();

    void previousVideo();

    void nextVideo();

    void seekVideo(long position);

    long getPositionVideo();

    long getDurationVideo();

    void setAVTransportURI(String currentURI, String currentURIMetaData);

    void stop();
}
