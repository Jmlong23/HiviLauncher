package com.ljm.audiotoollib.upnpserver.listener;

import com.ljm.audiotoollib.upnpserver.cling.support.model.TransportState;
import com.ljm.audiotoollib.upnpserver.entity.InfoEx;

public interface OnVideoControlListener {
    void playVideo();

    void pauseVideo();

    void previousVideo();

    void nextVideo();

    void seekVideo(long position);

    long getPositionVideo();

    long getDurationVideo();

    /**
     * Returns the actual state of the active video renderer so standard AVTransport queries stay
     * synchronized when playback is paused or resumed locally (for example by AI interruption).
     */
    TransportState getVideoTransportState();

    void setAVTransportURI(String currentURI, String currentURIMetaData);

    void stop();
}
