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

package com.ljm.audiotoollib.upnpserver.cling.protocol.sync;

import com.ljm.audiotoollib.upnpserver.cling.UpnpService;
import com.ljm.audiotoollib.upnpserver.cling.model.gena.LocalGENASubscription;
import com.ljm.audiotoollib.upnpserver.cling.model.message.StreamResponseMessage;
import com.ljm.audiotoollib.upnpserver.cling.model.message.gena.OutgoingEventRequestMessage;
import com.ljm.audiotoollib.upnpserver.cling.model.message.header.UpnpHeader;
import com.ljm.audiotoollib.upnpserver.cling.model.types.UnsignedIntegerFourBytes;
import com.ljm.audiotoollib.upnpserver.cling.protocol.SendingSync;
import com.ljm.audiotoollib.upnpserver.cling.transport.RouterException;

import java.net.URL;
import java.util.logging.Logger;

/**
 * Sending GENA event messages to remote subscribers.
 * <p>
 * Any {@link LocalGENASubscription} instantiates and executes this protocol
 * when the state of a local service changes. However, a remote subscriber might require event
 * notification messages on more than one callback URL, so this protocol potentially sends
 * many messages. What is returned is always the last response, that is, the response for the
 * message sent to the last callback URL in the list of the subscriber.
 * </p>
 *
 * @author Christian Bauer
 */
public class SendingEvent extends SendingSync<OutgoingEventRequestMessage, StreamResponseMessage> {

    final private static Logger log = Logger.getLogger(SendingEvent.class.getName());

    final protected String subscriptionId;
    final protected OutgoingEventRequestMessage[] requestMessages;
    final protected UnsignedIntegerFourBytes currentSequence;
    private final String serviceType;
    private final boolean renderingControl;

    public SendingEvent(UpnpService upnpService, LocalGENASubscription subscription) {
        super(upnpService, null); // Special case, we actually need to send several messages to each callback URL

        // TODO: Ugly design! It is critical (concurrency) that we prepare the event messages here, in the constructor thread!

        subscriptionId = subscription.getSubscriptionId();
        serviceType = subscription.getService().getServiceType().toString();
        renderingControl = "RenderingControl".equals(subscription.getService().getServiceType().getType());

        requestMessages = new OutgoingEventRequestMessage[subscription.getCallbackURLs().size()];
        int i = 0;
        for (URL url : subscription.getCallbackURLs()) {
            requestMessages[i] = new OutgoingEventRequestMessage(subscription, url);
            getUpnpService().getConfiguration().getGenaEventProcessor().writeBody(requestMessages[i]);
            i++;
        }

        currentSequence = new UnsignedIntegerFourBytes(subscription.getCurrentSequence().getValue());

        // Always increment sequence now, as (its value) has already been set on the headers and the
        // next event will use the incremented value
        subscription.incrementSequence();
    }

    protected StreamResponseMessage executeSync() throws RouterException {

        log.fine("Sending event for subscription: " + subscriptionId);

        StreamResponseMessage lastResponse = null;

        for (OutgoingEventRequestMessage requestMessage : requestMessages) {

            if (currentSequence.getValue() == 0) {
                log.fine("Sending initial event message to callback URL: " + requestMessage.getUri());
            } else {
                log.fine("Sending event message '"+currentSequence+"' to callback URL: " + requestMessage.getUri());
            }


            // Trace role, wallpaper and initial snapshots without logging every volume sample.
            String context = "SID=" + subscriptionId + ", service=" + serviceType
                    + ", SEQ=" + requestMessage.getHeaders().getFirstHeaderString(UpnpHeader.Type.SEQ)
                    + ", callback=" + requestMessage.getUri();
            boolean trace = renderingControl && (currentSequence.getValue() == 0
                    || requestMessage.getBodyString().contains("&lt;RoleId")
                    || requestMessage.getBodyString().contains("&lt;WallpaperId"));
            if (trace) log.info("GENA NOTIFY sending: " + context);
            try {
                lastResponse = getUpnpService().getRouter().send(requestMessage);
            } catch (RouterException ex) {
                log.warning("GENA NOTIFY transport failed: " + context + ", cause=" + ex);
                throw ex;
            }
            if (lastResponse == null) {
                log.warning("GENA NOTIFY no HTTP response (check callback listener/network): " + context);
            } else if (lastResponse.getOperation().isFailed()) {
                log.warning("GENA NOTIFY rejected: " + context
                        + ", HTTP=" + lastResponse.getOperation().getStatusCode());
            } else if (trace) {
                log.info("GENA NOTIFY delivered: " + context
                        + ", HTTP=" + lastResponse.getOperation().getStatusCode());
            }
            log.fine("Received event callback response: " + lastResponse);

        }

        // It's not really used, so just return the last one - we have only one callback URL most of the
        // time anyway
        return lastResponse;

    }
}
