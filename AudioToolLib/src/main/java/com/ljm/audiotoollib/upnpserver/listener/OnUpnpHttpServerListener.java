package com.ljm.audiotoollib.upnpserver.listener;

import com.ljm.audiotoollib.upnpserver.entity.PlayStatusBean;

public interface OnUpnpHttpServerListener {
    PlayStatusBean getPlayerStatus();
    void setPlayerCmdPlay(String url);
    void setPlayerCmdSlaveVol(int vol);
    void restoreToDefault();
}
