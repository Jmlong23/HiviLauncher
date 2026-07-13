package com.ljm.audiotoollib.upnpserver.service;

import com.ljm.audiotoollib.upnpserver.cling.support.connectionmanager.ConnectionManagerService;
import com.ljm.audiotoollib.upnpserver.cling.support.model.Protocol;
import com.ljm.audiotoollib.upnpserver.cling.support.model.ProtocolInfo;

public class ConnectionManagerServiceImpl extends ConnectionManagerService {
    public ConnectionManagerServiceImpl() {
        try {
            sinkProtocolInfo.add(new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio/mpeg", "DLNA.ORG_PN=MP3;DLNA.ORG_OP=01"));
            sinkProtocolInfo.add(new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "video/mpeg", "DLNA.ORG_PN=MPEG1;DLNA.ORG_OP=01;DLNA.ORG_CI=0"));
        } catch (IllegalArgumentException ex) {
            ex.printStackTrace();
        }
    }
}
