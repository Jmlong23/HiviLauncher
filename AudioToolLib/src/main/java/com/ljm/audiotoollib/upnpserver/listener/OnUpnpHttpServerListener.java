package com.ljm.audiotoollib.upnpserver.listener;

import com.ljm.audiotoollib.upnpserver.entity.PlayStatusBean;

public interface OnUpnpHttpServerListener {
    PlayStatusBean getPlayerStatus();
    void setPlayerCmdPlay(String url);
    void setPlayerCmdSlaveVol(int vol);
    void restoreToDefault();

    /**
     * 控制端通过 HTTP setDeviceName 命令改名，且名称已通过校验、本地真值已更新。
     * 实现方负责持久化新名称并重启 SSDP 发现服务（byebye 后 alive 携带新名）。
     */
    void setDeviceName(String name);
}
