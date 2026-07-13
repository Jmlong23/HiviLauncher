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

package com.ljm.audiotoollib.upnpserver.cling.support.shared;

import com.ljm.audiotoollib.upnpserver.cling.binding.xml.DeviceDescriptorBinder;
import com.ljm.audiotoollib.upnpserver.cling.binding.xml.ServiceDescriptorBinder;
import com.ljm.audiotoollib.upnpserver.cling.model.DefaultServiceManager;
import com.ljm.audiotoollib.upnpserver.cling.model.message.UpnpHeaders;
import com.ljm.audiotoollib.upnpserver.cling.model.meta.LocalService;
import com.ljm.audiotoollib.upnpserver.cling.protocol.ProtocolFactory;
import com.ljm.audiotoollib.upnpserver.cling.protocol.RetrieveRemoteDescriptors;
import com.ljm.audiotoollib.upnpserver.cling.protocol.sync.ReceivingAction;
import com.ljm.audiotoollib.upnpserver.cling.protocol.sync.ReceivingEvent;
import com.ljm.audiotoollib.upnpserver.cling.protocol.sync.ReceivingRetrieval;
import com.ljm.audiotoollib.upnpserver.cling.protocol.sync.ReceivingSubscribe;
import com.ljm.audiotoollib.upnpserver.cling.protocol.sync.ReceivingUnsubscribe;
import com.ljm.audiotoollib.upnpserver.cling.protocol.sync.SendingAction;
import com.ljm.audiotoollib.upnpserver.cling.protocol.sync.SendingEvent;
import com.ljm.audiotoollib.upnpserver.cling.protocol.sync.SendingRenewal;
import com.ljm.audiotoollib.upnpserver.cling.protocol.sync.SendingSubscribe;
import com.ljm.audiotoollib.upnpserver.cling.protocol.sync.SendingUnsubscribe;
import com.ljm.audiotoollib.upnpserver.cling.registry.Registry;
import com.ljm.audiotoollib.upnpserver.cling.transport.Router;
import com.ljm.audiotoollib.upnpserver.cling.transport.spi.DatagramIO;
import com.ljm.audiotoollib.upnpserver.cling.transport.spi.DatagramProcessor;
import com.ljm.audiotoollib.upnpserver.cling.transport.spi.GENAEventProcessor;
import com.ljm.audiotoollib.upnpserver.cling.transport.spi.MulticastReceiver;
import com.ljm.audiotoollib.upnpserver.cling.transport.spi.SOAPActionProcessor;
import com.ljm.audiotoollib.upnpserver.cling.transport.spi.StreamClient;
import com.ljm.audiotoollib.upnpserver.cling.transport.spi.StreamServer;
import com.ljm.audiotoollib.upnpserver.cling.transport.spi.UpnpStream;

import org.seamless.swing.logging.LogCategory;

import java.util.ArrayList;
import java.util.logging.Level;

/**
 * @author Christian Bauer
 */
public class CoreLogCategories extends ArrayList<LogCategory> {

    public CoreLogCategories() {
        super(10);

        add(new LogCategory("Network", new LogCategory.Group[]{

                new LogCategory.Group(
                        "UDP communication",
                        new LogCategory.LoggerLevel[]{
                                new LogCategory.LoggerLevel(DatagramIO.class.getName(), Level.FINE),
                                new LogCategory.LoggerLevel(MulticastReceiver.class.getName(), Level.FINE),
                        }
                ),

                new LogCategory.Group(
                        "UDP datagram processing and content",
                        new LogCategory.LoggerLevel[]{
                                new LogCategory.LoggerLevel(DatagramProcessor.class.getName(), Level.FINER)
                        }
                ),

                new LogCategory.Group(
                        "TCP communication",
                        new LogCategory.LoggerLevel[]{
                                new LogCategory.LoggerLevel(UpnpStream.class.getName(), Level.FINER),
                                new LogCategory.LoggerLevel(StreamServer.class.getName(), Level.FINE),
                                new LogCategory.LoggerLevel(StreamClient.class.getName(), Level.FINE),
                        }
                ),

                new LogCategory.Group(
                        "SOAP action message processing and content",
                        new LogCategory.LoggerLevel[]{
                                new LogCategory.LoggerLevel(SOAPActionProcessor.class.getName(), Level.FINER)
                        }
                ),

                new LogCategory.Group(
                        "GENA event message processing and content",
                        new LogCategory.LoggerLevel[]{
                                new LogCategory.LoggerLevel(GENAEventProcessor.class.getName(), Level.FINER)
                        }
                ),

                new LogCategory.Group(
                        "HTTP header processing",
                        new LogCategory.LoggerLevel[]{
                                new LogCategory.LoggerLevel(UpnpHeaders.class.getName(), Level.FINER)
                        }
                ),
        }));


        add(new LogCategory("UPnP Protocol", new LogCategory.Group[]{

                new LogCategory.Group(
                        "Discovery (Notification & Search)",
                        new LogCategory.LoggerLevel[]{
                                new LogCategory.LoggerLevel(ProtocolFactory.class.getName(), Level.FINER),
                                new LogCategory.LoggerLevel("org.fourthline.cling.protocol.async", Level.FINER)
                        }
                ),

                new LogCategory.Group(
                        "Description",
                        new LogCategory.LoggerLevel[]{
                                new LogCategory.LoggerLevel(ProtocolFactory.class.getName(), Level.FINER),
                                new LogCategory.LoggerLevel(RetrieveRemoteDescriptors.class.getName(), Level.FINE),
                                new LogCategory.LoggerLevel(ReceivingRetrieval.class.getName(), Level.FINE),
                                new LogCategory.LoggerLevel(DeviceDescriptorBinder.class.getName(), Level.FINE),
                                new LogCategory.LoggerLevel(ServiceDescriptorBinder.class.getName(), Level.FINE),
                        }
                ),

                new LogCategory.Group(
                        "Control",
                        new LogCategory.LoggerLevel[]{
                                new LogCategory.LoggerLevel(ProtocolFactory.class.getName(), Level.FINER),
                                new LogCategory.LoggerLevel(ReceivingAction.class.getName(), Level.FINER),
                                new LogCategory.LoggerLevel(SendingAction.class.getName(), Level.FINER),
                        }
                ),

                new LogCategory.Group(
                        "GENA ",
                        new LogCategory.LoggerLevel[]{
                                new LogCategory.LoggerLevel("org.fourthline.cling.model.gena", Level.FINER),
                                new LogCategory.LoggerLevel(ProtocolFactory.class.getName(), Level.FINER),
                                new LogCategory.LoggerLevel(ReceivingEvent.class.getName(), Level.FINER),
                                new LogCategory.LoggerLevel(ReceivingSubscribe.class.getName(), Level.FINER),
                                new LogCategory.LoggerLevel(ReceivingUnsubscribe.class.getName(), Level.FINER),
                                new LogCategory.LoggerLevel(SendingEvent.class.getName(), Level.FINER),
                                new LogCategory.LoggerLevel(SendingSubscribe.class.getName(), Level.FINER),
                                new LogCategory.LoggerLevel(SendingUnsubscribe.class.getName(), Level.FINER),
                                new LogCategory.LoggerLevel(SendingRenewal.class.getName(), Level.FINER),
                        }
                ),
        }));

        add(new LogCategory("Core", new LogCategory.Group[]{

                new LogCategory.Group(
                        "Router",
                        new LogCategory.LoggerLevel[]{
                                new LogCategory.LoggerLevel(Router.class.getName(), Level.FINER)
                        }
                ),

                new LogCategory.Group(
                        "Registry",
                        new LogCategory.LoggerLevel[]{
                                new LogCategory.LoggerLevel(Registry.class.getName(), Level.FINER),
                        }
                ),

                new LogCategory.Group(
                        "Local service binding & invocation",
                        new LogCategory.LoggerLevel[]{
                                new LogCategory.LoggerLevel("org.fourthline.cling.binding.annotations", Level.FINER),
                                new LogCategory.LoggerLevel(LocalService.class.getName(), Level.FINER),
                                new LogCategory.LoggerLevel("org.fourthline.cling.model.action", Level.FINER),
                                new LogCategory.LoggerLevel("org.fourthline.cling.model.state", Level.FINER),
                                new LogCategory.LoggerLevel(DefaultServiceManager.class.getName(), Level.FINER)
                        }
                ),

                new LogCategory.Group(
                        "Control Point interaction",
                        new LogCategory.LoggerLevel[]{
                                new LogCategory.LoggerLevel("org.fourthline.cling.controlpoint", Level.FINER),
                        }
                ),
        }));

    }

}
