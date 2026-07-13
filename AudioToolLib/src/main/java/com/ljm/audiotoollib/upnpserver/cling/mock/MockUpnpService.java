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
import com.ljm.audiotoollib.upnpserver.cling.UpnpServiceConfiguration;
import com.ljm.audiotoollib.upnpserver.cling.controlpoint.ControlPoint;
import com.ljm.audiotoollib.upnpserver.cling.controlpoint.ControlPointImpl;
import com.ljm.audiotoollib.upnpserver.cling.model.message.header.UpnpHeader;
import com.ljm.audiotoollib.upnpserver.cling.model.meta.LocalDevice;
import com.ljm.audiotoollib.upnpserver.cling.protocol.ProtocolFactory;
import com.ljm.audiotoollib.upnpserver.cling.protocol.ProtocolFactoryImpl;
import com.ljm.audiotoollib.upnpserver.cling.protocol.async.SendingNotificationAlive;
import com.ljm.audiotoollib.upnpserver.cling.protocol.async.SendingSearch;
import com.ljm.audiotoollib.upnpserver.cling.registry.Registry;
import com.ljm.audiotoollib.upnpserver.cling.registry.RegistryImpl;
import com.ljm.audiotoollib.upnpserver.cling.registry.RegistryMaintainer;
import com.ljm.audiotoollib.upnpserver.cling.transport.RouterException;
import com.ljm.audiotoollib.upnpserver.cling.transport.spi.NetworkAddressFactory;

import javax.enterprise.inject.Alternative;

/**
 * Simplifies testing of core and non-core modules.
 * <p>
 * It uses the {@link MockProtocolFactory}.
 * </p>
 *
 * @author Christian Bauer
 */
@Alternative
public class MockUpnpService implements UpnpService {

    protected final UpnpServiceConfiguration configuration;
    protected final ControlPoint controlPoint;
    protected final ProtocolFactory protocolFactory;
    protected final Registry registry;
    protected final MockRouter router;

    protected final NetworkAddressFactory networkAddressFactory;

    /**
     * Single-thread of execution for the whole UPnP stack, no ALIVE messages or registry maintenance.
     */
    public MockUpnpService() {
        this(false, new MockUpnpServiceConfiguration(false, false));
    }

    /**
     * No ALIVE messages.
     */
    public MockUpnpService(MockUpnpServiceConfiguration configuration) {
        this(false, configuration);
    }

    /**
     * Single-thread of execution for the whole UPnP stack, except one background registry maintenance thread.
     */
    public MockUpnpService(final boolean sendsAlive, final boolean maintainsRegistry) {
        this(sendsAlive, new MockUpnpServiceConfiguration(maintainsRegistry, false));
    }

    public MockUpnpService(final boolean sendsAlive, final boolean maintainsRegistry, final boolean multiThreaded) {
        this(sendsAlive, new MockUpnpServiceConfiguration(maintainsRegistry, multiThreaded));
    }

    public MockUpnpService(final boolean sendsAlive, final MockUpnpServiceConfiguration configuration) {

        this.configuration = configuration;

        this.protocolFactory = createProtocolFactory(this, sendsAlive);

        this.registry = new RegistryImpl(this) {
            @Override
            protected RegistryMaintainer createRegistryMaintainer() {
                return configuration.isMaintainsRegistry() ? super.createRegistryMaintainer() : null;
            }
        };

        this.networkAddressFactory = this.configuration.createNetworkAddressFactory();

        this.router = createRouter();

        this.controlPoint = new ControlPointImpl(configuration, protocolFactory, registry);
    }

    protected ProtocolFactory createProtocolFactory(UpnpService service, boolean sendsAlive) {
        return new MockProtocolFactory(service, sendsAlive);
    }

    protected MockRouter createRouter() {
        return new MockRouter(getConfiguration(), getProtocolFactory());
    }

    /**
     * This factory customizes several protocols.
     * <p>
     * The {@link SendingNotificationAlive} protocol
     * only sends messages if this feature is enabled when instantiating the factory.
     * </p>
     * <p>
     * The {@link SendingSearch} protocol doesn't wait between
     * sending search message bulks, this speeds up testing.
     * </p>
     */
    public static class MockProtocolFactory extends ProtocolFactoryImpl {

        private boolean sendsAlive;

        public MockProtocolFactory(UpnpService upnpService, boolean sendsAlive) {
            super(upnpService);
            this.sendsAlive = sendsAlive;
        }

        @Override
        public SendingNotificationAlive createSendingNotificationAlive(LocalDevice localDevice) {
            return new SendingNotificationAlive(getUpnpService(), localDevice) {
                @Override
                protected void execute() throws RouterException {
                    if (sendsAlive) super.execute();
                }
            };
        }

        @Override
        public SendingSearch createSendingSearch(UpnpHeader searchTarget, int mxSeconds) {
            return new SendingSearch(getUpnpService(), searchTarget, mxSeconds) {
                @Override
                public int getBulkIntervalMilliseconds() {
                    return 0; // Don't wait
                }
            };
        }
    }

    public UpnpServiceConfiguration getConfiguration() {
        return configuration;
    }

    public ControlPoint getControlPoint() {
        return controlPoint;
    }

    public ProtocolFactory getProtocolFactory() {
        return protocolFactory;
    }

    public Registry getRegistry() {
        return registry;
    }

    public MockRouter getRouter() {
        return router;
    }

    public void shutdown() {
        getRegistry().shutdown();
        getConfiguration().shutdown();
    }
}
