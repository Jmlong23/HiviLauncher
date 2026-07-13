/*
 * Copyright (C) 2013 4th Line GmbH, Switzerland
 *
 * The contents of this file are subject to the terms of either the GNU
 * Lesser General Public License Version 2 or later ("LGPL") or the
 * Common Development and Distribution License Version 1 or later
 * ("CDDL") (collectively, the "License"). You may not use this file
 * except in compliance with the License. See LICENSE.txt for more
 * information.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */
package com.ljm.audiotoollib.upnpserver.cling.mock;

import com.ljm.audiotoollib.upnpserver.cling.UpnpService;
import com.ljm.audiotoollib.upnpserver.cling.model.action.ActionInvocation;
import com.ljm.audiotoollib.upnpserver.cling.model.gena.LocalGENASubscription;
import com.ljm.audiotoollib.upnpserver.cling.model.gena.RemoteGENASubscription;
import com.ljm.audiotoollib.upnpserver.cling.model.message.IncomingDatagramMessage;
import com.ljm.audiotoollib.upnpserver.cling.model.message.StreamRequestMessage;
import com.ljm.audiotoollib.upnpserver.cling.model.message.header.UpnpHeader;
import com.ljm.audiotoollib.upnpserver.cling.model.meta.LocalDevice;
import com.ljm.audiotoollib.upnpserver.cling.protocol.ProtocolCreationException;
import com.ljm.audiotoollib.upnpserver.cling.protocol.ProtocolFactory;
import com.ljm.audiotoollib.upnpserver.cling.protocol.ReceivingAsync;
import com.ljm.audiotoollib.upnpserver.cling.protocol.ReceivingSync;
import com.ljm.audiotoollib.upnpserver.cling.protocol.async.SendingNotificationAlive;
import com.ljm.audiotoollib.upnpserver.cling.protocol.async.SendingNotificationByebye;
import com.ljm.audiotoollib.upnpserver.cling.protocol.async.SendingSearch;
import com.ljm.audiotoollib.upnpserver.cling.protocol.sync.SendingAction;
import com.ljm.audiotoollib.upnpserver.cling.protocol.sync.SendingEvent;
import com.ljm.audiotoollib.upnpserver.cling.protocol.sync.SendingRenewal;
import com.ljm.audiotoollib.upnpserver.cling.protocol.sync.SendingSubscribe;
import com.ljm.audiotoollib.upnpserver.cling.protocol.sync.SendingUnsubscribe;

import java.net.URL;

import javax.enterprise.inject.Alternative;

/**
 * @author Christian Bauer
 */
@Alternative
public class MockProtocolFactory implements ProtocolFactory {

    @Override
    public UpnpService getUpnpService() {
        return null;
    }

    @Override
    public ReceivingAsync createReceivingAsync(IncomingDatagramMessage message) throws ProtocolCreationException {
        return null;
    }

    @Override
    public ReceivingSync createReceivingSync(StreamRequestMessage requestMessage) throws ProtocolCreationException {
        return null;
    }

    @Override
    public SendingNotificationAlive createSendingNotificationAlive(LocalDevice localDevice) {
        return null;
    }

    @Override
    public SendingNotificationByebye createSendingNotificationByebye(LocalDevice localDevice) {
        return null;
    }

    @Override
    public SendingSearch createSendingSearch(UpnpHeader searchTarget, int mxSeconds) {
        return null;
    }

    @Override
    public SendingAction createSendingAction(ActionInvocation actionInvocation, URL controlURL) {
        return null;
    }

    @Override
    public SendingSubscribe createSendingSubscribe(RemoteGENASubscription subscription) {
        return null;
    }

    @Override
    public SendingRenewal createSendingRenewal(RemoteGENASubscription subscription) {
        return null;
    }

    @Override
    public SendingUnsubscribe createSendingUnsubscribe(RemoteGENASubscription subscription) {
        return null;
    }

    @Override
    public SendingEvent createSendingEvent(LocalGENASubscription subscription) {
        return null;
    }
}
