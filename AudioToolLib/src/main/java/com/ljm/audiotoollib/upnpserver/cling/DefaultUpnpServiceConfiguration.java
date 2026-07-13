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

package com.ljm.audiotoollib.upnpserver.cling;

import com.ljm.audiotoollib.upnpserver.cling.binding.xml.DeviceDescriptorBinder;
import com.ljm.audiotoollib.upnpserver.cling.binding.xml.ServiceDescriptorBinder;
import com.ljm.audiotoollib.upnpserver.cling.binding.xml.UDA10DeviceDescriptorBinderImpl;
import com.ljm.audiotoollib.upnpserver.cling.binding.xml.UDA10ServiceDescriptorBinderImpl;
import com.ljm.audiotoollib.upnpserver.cling.model.ModelUtil;
import com.ljm.audiotoollib.upnpserver.cling.model.Namespace;
import com.ljm.audiotoollib.upnpserver.cling.model.message.UpnpHeaders;
import com.ljm.audiotoollib.upnpserver.cling.model.meta.RemoteDeviceIdentity;
import com.ljm.audiotoollib.upnpserver.cling.model.meta.RemoteService;
import com.ljm.audiotoollib.upnpserver.cling.model.types.ServiceType;
import com.ljm.audiotoollib.upnpserver.cling.transport.impl.DatagramIOConfigurationImpl;
import com.ljm.audiotoollib.upnpserver.cling.transport.impl.DatagramIOImpl;
import com.ljm.audiotoollib.upnpserver.cling.transport.impl.DatagramProcessorImpl;
import com.ljm.audiotoollib.upnpserver.cling.transport.impl.GENAEventProcessorImpl;
import com.ljm.audiotoollib.upnpserver.cling.transport.impl.MulticastReceiverConfigurationImpl;
import com.ljm.audiotoollib.upnpserver.cling.transport.impl.MulticastReceiverImpl;
import com.ljm.audiotoollib.upnpserver.cling.transport.impl.NetworkAddressFactoryImpl;
import com.ljm.audiotoollib.upnpserver.cling.transport.impl.SOAPActionProcessorImpl;
import com.ljm.audiotoollib.upnpserver.cling.transport.impl.StreamClientConfigurationImpl;
import com.ljm.audiotoollib.upnpserver.cling.transport.impl.StreamClientImpl;
import com.ljm.audiotoollib.upnpserver.cling.transport.impl.StreamServerConfigurationImpl;
import com.ljm.audiotoollib.upnpserver.cling.transport.impl.StreamServerImpl;
import com.ljm.audiotoollib.upnpserver.cling.transport.spi.DatagramIO;
import com.ljm.audiotoollib.upnpserver.cling.transport.spi.DatagramProcessor;
import com.ljm.audiotoollib.upnpserver.cling.transport.spi.GENAEventProcessor;
import com.ljm.audiotoollib.upnpserver.cling.transport.spi.MulticastReceiver;
import com.ljm.audiotoollib.upnpserver.cling.transport.spi.NetworkAddressFactory;
import com.ljm.audiotoollib.upnpserver.cling.transport.spi.SOAPActionProcessor;
import com.ljm.audiotoollib.upnpserver.cling.transport.spi.StreamClient;
import com.ljm.audiotoollib.upnpserver.cling.transport.spi.StreamServer;

import org.seamless.util.Exceptions;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import javax.enterprise.inject.Alternative;

/**
 * Default configuration data of a typical UPnP stack.
 * <p>
 * This configuration utilizes the default network transport implementation found in
 * {@link org.fourthline.cling.transport.impl}.
 * </p>
 * <p>
 * This configuration utilizes the DOM default descriptor binders found in
 * {@link org.fourthline.cling.binding.xml}.
 * </p>
 * <p>
 * The thread <code>Executor</code> is a bounded <code>ThreadPoolExecutor</code> with
 * a custom {@link ClingThreadFactory} (it only sets a thread name).
 * </p>
 * <p>
 * Pools are bounded but use a queued hand-off ({@link LinkedBlockingQueue}) so
 * bursts of SSDP/SOAP/GENA/descriptor traffic are buffered instead of running
 * synchronously on the HTTP acceptor thread (which would stall phone-side
 * GetPositionInfo / getInfoEx polling during music playback).
 * </p>
 * <p>
 * The default {@link Namespace} is configured without any
 * base path or prefix.
 * </p>
 *
 * @author Christian Bauer
 */
@Alternative
public class DefaultUpnpServiceConfiguration implements UpnpServiceConfiguration {

    private static Logger log = Logger.getLogger(DefaultUpnpServiceConfiguration.class.getName());

    final private int streamListenPort;

    final private ExecutorService defaultExecutorService;
    final private ExecutorService multicastReceiverExecutorService;
    final private ExecutorService datagramIOExecutorService;
    final private ExecutorService streamServerExecutorService;
    final private ExecutorService asyncProtocolExecutorService;
    final private ExecutorService syncProtocolExecutorService;

    final private DatagramProcessor datagramProcessor;
    final private SOAPActionProcessor soapActionProcessor;
    final private GENAEventProcessor genaEventProcessor;

    final private DeviceDescriptorBinder deviceDescriptorBinderUDA10;
    final private ServiceDescriptorBinder serviceDescriptorBinderUDA10;

    final private Namespace namespace;

    /**
     * Defaults to port '0', ephemeral.
     */
    public DefaultUpnpServiceConfiguration() {
        this(NetworkAddressFactoryImpl.DEFAULT_TCP_HTTP_LISTEN_PORT);
    }

    public DefaultUpnpServiceConfiguration(int streamListenPort) {
        this(streamListenPort, true);
    }

    protected DefaultUpnpServiceConfiguration(boolean checkRuntime) {
        this(NetworkAddressFactoryImpl.DEFAULT_TCP_HTTP_LISTEN_PORT, checkRuntime);
    }

    protected DefaultUpnpServiceConfiguration(int streamListenPort, boolean checkRuntime) {
        if (checkRuntime && ModelUtil.ANDROID_RUNTIME) {
            throw new Error("Unsupported runtime environment, use org.fourthline.cling.android.AndroidUpnpServiceConfiguration");
        }

        this.streamListenPort = streamListenPort;

        defaultExecutorService = createDefaultExecutorService();
        multicastReceiverExecutorService = createMulticastReceiverExecutorService();
        datagramIOExecutorService = createDatagramIOExecutorService();
        streamServerExecutorService = createStreamServerExecutorService();
        asyncProtocolExecutorService = createAsyncProtocolExecutorService();
        syncProtocolExecutorService = createSyncProtocolExecutorService();

        datagramProcessor = createDatagramProcessor();
        soapActionProcessor = createSOAPActionProcessor();
        genaEventProcessor = createGENAEventProcessor();

        deviceDescriptorBinderUDA10 = createDeviceDescriptorBinderUDA10();
        serviceDescriptorBinderUDA10 = createServiceDescriptorBinderUDA10();

        namespace = createNamespace();
    }

    public DatagramProcessor getDatagramProcessor() {
        return datagramProcessor;
    }

    public SOAPActionProcessor getSoapActionProcessor() {
        return soapActionProcessor;
    }

    public GENAEventProcessor getGenaEventProcessor() {
        return genaEventProcessor;
    }

    public StreamClient createStreamClient() {
        return new StreamClientImpl(
            new StreamClientConfigurationImpl(
                getSyncProtocolExecutorService()
            )
        );
    }

    public MulticastReceiver createMulticastReceiver(NetworkAddressFactory networkAddressFactory) {
        return new MulticastReceiverImpl(
                new MulticastReceiverConfigurationImpl(
                        networkAddressFactory.getMulticastGroup(),
                        networkAddressFactory.getMulticastPort()
                )
        );
    }

    public DatagramIO createDatagramIO(NetworkAddressFactory networkAddressFactory) {
        return new DatagramIOImpl(new DatagramIOConfigurationImpl());
    }

    public StreamServer createStreamServer(NetworkAddressFactory networkAddressFactory) {
        return new StreamServerImpl(
                new StreamServerConfigurationImpl(
                        networkAddressFactory.getStreamListenPort()
                )
        );
    }

    public Executor getMulticastReceiverExecutor() {
        return multicastReceiverExecutorService;
    }

    public Executor getDatagramIOExecutor() {
        return datagramIOExecutorService;
    }

    public ExecutorService getStreamServerExecutorService() {
        return streamServerExecutorService;
    }

    public DeviceDescriptorBinder getDeviceDescriptorBinderUDA10() {
        return deviceDescriptorBinderUDA10;
    }

    public ServiceDescriptorBinder getServiceDescriptorBinderUDA10() {
        return serviceDescriptorBinderUDA10;
    }

    public ServiceType[] getExclusiveServiceTypes() {
        return new ServiceType[0];
    }

    /**
     * @return Defaults to <code>false</code>.
     */
	public boolean isReceivedSubscriptionTimeoutIgnored() {
		return false;
	}

    public UpnpHeaders getDescriptorRetrievalHeaders(RemoteDeviceIdentity identity) {
        return null;
    }

    public UpnpHeaders getEventSubscriptionHeaders(RemoteService service) {
        return null;
    }

    /**
     * @return Defaults to 1000 milliseconds.
     */
    public int getRegistryMaintenanceIntervalMillis() {
        return 1000;
    }

    /**
     * @return Defaults to zero, disabling ALIVE flooding.
     */
    public int getAliveIntervalMillis() {
    	return 0;
    }

    public Integer getRemoteDeviceMaxAgeSeconds() {
        return null;
    }

    public Executor getAsyncProtocolExecutor() {
        return asyncProtocolExecutorService;
    }

    public ExecutorService getSyncProtocolExecutorService() {
        return syncProtocolExecutorService;
    }

    public Namespace getNamespace() {
        return namespace;
    }

    public Executor getRegistryMaintainerExecutor() {
        return getDefaultExecutorService();
    }

    public Executor getRegistryListenerExecutor() {
        return getDefaultExecutorService();
    }

    public NetworkAddressFactory createNetworkAddressFactory() {
        return createNetworkAddressFactory(streamListenPort);
    }

    public void shutdown() {
        shutdownExecutor("sync protocol", syncProtocolExecutorService);
        shutdownExecutor("async protocol", asyncProtocolExecutorService);
        shutdownExecutor("stream server", streamServerExecutorService);
        shutdownExecutor("datagram I/O", datagramIOExecutorService);
        shutdownExecutor("multicast receiver", multicastReceiverExecutorService);
        shutdownExecutor("default", defaultExecutorService);
    }

    protected void shutdownExecutor(String name, ExecutorService executorService) {
        log.fine("Shutting down " + name + " executor service");
        executorService.shutdownNow();
    }

    protected NetworkAddressFactory createNetworkAddressFactory(int streamListenPort) {
        return new NetworkAddressFactoryImpl(streamListenPort);
    }

    protected DatagramProcessor createDatagramProcessor() {
        return new DatagramProcessorImpl();
    }

    protected SOAPActionProcessor createSOAPActionProcessor() {
        return new SOAPActionProcessorImpl();
    }

    protected GENAEventProcessor createGENAEventProcessor() {
        return new GENAEventProcessorImpl();
    }

    protected DeviceDescriptorBinder createDeviceDescriptorBinderUDA10() {
        return new UDA10DeviceDescriptorBinderImpl();
    }

    protected ServiceDescriptorBinder createServiceDescriptorBinderUDA10() {
        return new UDA10ServiceDescriptorBinderImpl();
    }

    protected Namespace createNamespace() {
        return new Namespace();
    }

    protected ExecutorService getDefaultExecutorService() {
        return defaultExecutorService;
    }

    protected ExecutorService createDefaultExecutorService() {
        return new ClingExecutor("misc", getDefaultMaxPoolSize(), 64);
    }

    protected ExecutorService createMulticastReceiverExecutorService() {
        return new ClingExecutor("mcast", getIoMaxPoolSize(), 128);
    }

    protected ExecutorService createDatagramIOExecutorService() {
        return new ClingExecutor("datagram", getIoMaxPoolSize(), 128);
    }

    protected ExecutorService createStreamServerExecutorService() {
        return new ClingExecutor("stream", getStreamServerMaxPoolSize(), 512);
    }

    protected ExecutorService createAsyncProtocolExecutorService() {
        return new ClingExecutor("async", getProtocolMaxPoolSize(), 384);
    }

    protected ExecutorService createSyncProtocolExecutorService() {
        return new ClingExecutor("sync", getProtocolMaxPoolSize(), 384);
    }

    private static int getDefaultMaxPoolSize() {
        int cpuCount = Runtime.getRuntime().availableProcessors();
        return Math.max(8, Math.min(24, cpuCount * 3));
    }

    private static int getIoMaxPoolSize() {
        int cpuCount = Runtime.getRuntime().availableProcessors();
        return Math.max(8, Math.min(24, cpuCount * 3));
    }

    private static int getProtocolMaxPoolSize() {
        int cpuCount = Runtime.getRuntime().availableProcessors();
        return Math.max(24, Math.min(96, cpuCount * 12));
    }

    private static int getStreamServerMaxPoolSize() {
        int cpuCount = Runtime.getRuntime().availableProcessors();
        return Math.max(48, Math.min(128, cpuCount * 16));
    }

    public static class ClingExecutor extends ThreadPoolExecutor {
        private static final long KEEP_ALIVE_SECONDS = 30L;

        public ClingExecutor() {
            this("default", getStreamServerMaxPoolSize(), 512);
        }

        public ClingExecutor(String poolName, int maxPoolSize, int queueCapacity) {
            this(poolName,
                 maxPoolSize,
                 queueCapacity,
                 new ClingThreadFactory("cling-" + poolName + "-")
            );
        }

        public ClingExecutor(ThreadFactory threadFactory) {
            this("custom", getStreamServerMaxPoolSize(), 512, threadFactory);
        }

        private ClingExecutor(String poolName,
                              int maxPoolSize,
                              int queueCapacity,
                              ThreadFactory threadFactory) {
            super(
                    Math.max(1, maxPoolSize),
                    Math.max(1, maxPoolSize),
                    KEEP_ALIVE_SECONDS,
                    TimeUnit.SECONDS,
                    new LinkedBlockingQueue<Runnable>(Math.max(16, queueCapacity)),
                    threadFactory,
                    new ThreadPoolExecutor.CallerRunsPolicy()
            );
            // LinkedBlockingQueue prefers queueing over growing past the core size. Keep the core
            // equal to max so Cling can fan out under SSDP/SOAP bursts, then let idle threads
            // shrink back after the keep-alive timeout.
            allowCoreThreadTimeOut(true);
            log.info("Created bounded ClingExecutor name=" + poolName
                    + ", core=" + getCorePoolSize()
                    + ", max=" + getMaximumPoolSize()
                    + ", queueCapacity=" + Math.max(16, queueCapacity)
                    + ", keepAliveSeconds=" + KEEP_ALIVE_SECONDS);
        }

        @Override
        protected void afterExecute(Runnable runnable, Throwable throwable) {
            super.afterExecute(runnable, throwable);
            if (throwable != null) {
                Throwable cause = Exceptions.unwrap(throwable);
                if (cause instanceof InterruptedException) {
                    // Ignore this, might happen when we shutdownNow() the executor. We can't
                    // log at this point as the logging system might be stopped already (e.g.
                    // if it's a CDI component).
                    return;
                }
                // Log only
                log.warning("Thread terminated " + runnable + " abruptly with exception: " + throwable);
                log.warning("Root cause: " + cause);
            }
        }
    }

    // Executors.DefaultThreadFactory is package visibility (...no touching, you unworthy JDK user!)
    public static class ClingThreadFactory implements ThreadFactory {

        protected final ThreadGroup group;
        protected final AtomicInteger threadNumber = new AtomicInteger(1);
        protected final String namePrefix;

        public ClingThreadFactory() {
            this("cling-");
        }

        public ClingThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
        }

        public Thread newThread(Runnable r) {
            Thread t = new Thread(
                    group, r,
                    namePrefix + threadNumber.getAndIncrement(),
                    0
            );
            if (t.isDaemon())
                t.setDaemon(false);
            if (t.getPriority() != Thread.NORM_PRIORITY)
                t.setPriority(Thread.NORM_PRIORITY);

            return t;
        }
    }

}
