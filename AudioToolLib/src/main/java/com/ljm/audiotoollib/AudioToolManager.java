package com.ljm.audiotoollib;


import com.ljm.audiotoollib.upnpserver.UpnpServerManager;

public class AudioToolManager {


    static private AudioToolManager _toolManager;


    private final UpnpServerManager upnpServerManager;


    public static AudioToolManager instance() {
        if(_toolManager == null) {
            _toolManager = new AudioToolManager();
        }
        return _toolManager;
    }

    AudioToolManager() {
        upnpServerManager = new UpnpServerManager();
    }


    public UpnpServerManager getUpnpServerManager() {
        return upnpServerManager;
    }
}