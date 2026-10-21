package com.ljm.audiotoollib.upnpserver;

import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.ljm.audiotoollib.upnpserver.cling.UpnpServiceConfiguration;
import com.ljm.audiotoollib.upnpserver.cling.android.AndroidUpnpServiceConfiguration;
import com.ljm.audiotoollib.upnpserver.cling.android.AndroidUpnpServiceImpl;
import com.ljm.audiotoollib.upnpserver.cling.android.FixedAndroidLogHandler;
import com.ljm.audiotoollib.upnpserver.cling.binding.annotations.AnnotationLocalServiceBinder;
import com.ljm.audiotoollib.upnpserver.cling.model.DefaultServiceManager;
import com.ljm.audiotoollib.upnpserver.cling.model.ValidationException;
import com.ljm.audiotoollib.upnpserver.cling.model.meta.DeviceDetails;
import com.ljm.audiotoollib.upnpserver.cling.model.meta.DeviceIdentity;
import com.ljm.audiotoollib.upnpserver.cling.model.meta.Icon;
import com.ljm.audiotoollib.upnpserver.cling.model.meta.LocalDevice;
import com.ljm.audiotoollib.upnpserver.cling.model.meta.LocalService;
import com.ljm.audiotoollib.upnpserver.cling.model.meta.ManufacturerDetails;
import com.ljm.audiotoollib.upnpserver.cling.model.meta.ModelDetails;
import com.ljm.audiotoollib.upnpserver.cling.model.types.UDADeviceType;
import com.ljm.audiotoollib.upnpserver.cling.model.types.UDN;
import com.ljm.audiotoollib.upnpserver.cling.protocol.sync.ReceivingRetrieval;
import com.ljm.audiotoollib.upnpserver.cling.support.avtransport.lastchange.AVTransportLastChangeParser;
import com.ljm.audiotoollib.upnpserver.cling.support.lastchange.LastChange;
import com.ljm.audiotoollib.upnpserver.cling.support.lastchange.LastChangeAwareServiceManager;
import com.ljm.audiotoollib.upnpserver.cling.support.renderingcontrol.lastchange.RenderingControlLastChangeParser;
import com.ljm.audiotoollib.upnpserver.entity.PlayQueueLastChangeParser;
import com.ljm.audiotoollib.upnpserver.entity.SWDeviceStatus;
import com.ljm.audiotoollib.upnpserver.service.AVTransportController;
import com.ljm.audiotoollib.upnpserver.service.AVTransportServiceImpl;
import com.ljm.audiotoollib.upnpserver.service.AudioRenderController;
import com.ljm.audiotoollib.upnpserver.service.AudioRenderServiceImpl;
import com.ljm.audiotoollib.upnpserver.service.ConnectionManagerServiceImpl;
import com.ljm.audiotoollib.upnpserver.service.PlayQueueController;
import com.ljm.audiotoollib.upnpserver.service.PlayQueueServiceImpl;
import com.ljm.audiotoollib.upnpserver.service.RenderControlManager;
import com.ljm.audiotoollib.upnpserver.service.RoleController;
import com.ljm.audiotoollib.upnpserver.utils.UpnpNetworkUtils;

import org.seamless.util.logging.LoggingUtil;

import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 */
public class DLNARendererService extends AndroidUpnpServiceImpl {

    private final String TAG = "DLNARendererService";
    private final RenderControlManager mRenderControlManager = new RenderControlManager();
    private PlayQueueController mPlayQueueManager;

    private LastChange mAvTransportLastChange;
    private LastChange mAudioControlLastChange;
    private LastChange mPlayQueueLastChange;
    private final RendererServiceBinder mBinder = new RendererServiceBinder();
    private LocalDevice mRendererDevice;
    private volatile AVTransportServiceImpl mAvTransportService;
    private volatile AudioRenderServiceImpl mAudioRenderService;
    private volatile PlayQueueServiceImpl mPlayQueueService;
    private final Handler stateHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean stateSamplePending = new AtomicBoolean();
    private volatile boolean destroyed;
    private final Runnable stateSampler = new Runnable() {
        @Override public void run() {
            if (destroyed) return;
            if (stateSamplePending.compareAndSet(false, true)) {
                // Player and UI-owned values are read on the main thread, never under Cling locks.
                try {
                    if (mAudioRenderService != null) mAudioRenderService.refreshState();
                } catch (Exception e) { Log.w(TAG, "sample RenderingControl failed", e); }
                try {
                    if (mAvTransportService != null) mAvTransportService.refreshState();
                } catch (Exception e) { Log.w(TAG, "sample AVTransport failed", e); }
                try {
                    if (mPlayQueueService != null) mPlayQueueService.refreshState();
                } catch (Exception e) { Log.w(TAG, "sample PlayQueue failed", e); }
                transportEventExecutor.execute(() -> stateSamplePending.set(false));
            }
            stateHandler.postDelayed(this, 1000);
        }
    };
    private final ExecutorService transportEventExecutor = Executors.newSingleThreadExecutor();
    /** 事件分发与 SOAP 动作共用 manager 锁，默认 500ms 超时 + 单次尝试会静默丢事件 */
    private static final int EVENT_DISPATCH_ATTEMPTS = 3;
    private static String macAddress = "";
    /** 当前运行的实例，供改名等外部操作在原服务上原地重注册，避免销毁重建整个 UPnP 栈 */
    private static volatile DLNARendererService sRunningInstance;

    public static DLNARendererService getRunningInstance() {
        return sRunningInstance;
    }

    /**
     * Refresh the dynamic friendly name and discovery advertisements without replacing services
     * or cancelling their existing GENA subscriptions.
     *
     * @return true if refreshed; false if the renderer is not ready.
     */
    public synchronized boolean reannounceWithUpdatedDeviceName() {
        if (upnpService == null || upnpService.getRegistry() == null || mRendererDevice == null) {
            return false;
        }
        try {
            // 描述符缓存按 URI（含 UDN）缓存，UDN 不变则 key 不变，必须清掉否则回放旧名描述符
            ReceivingRetrieval.clearDescriptorCache();
            upnpService.getRegistry().advertiseLocalDevices();
            stateHandler.removeCallbacks(stateSampler);
            stateHandler.post(stateSampler);
            Log.i(TAG, "reannounceWithUpdatedDeviceName done, name: " + SWDeviceStatus.deviceName);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "reannounceWithUpdatedDeviceName failed", e);
            return false;
        }
    }

    @Override
    protected UpnpServiceConfiguration createConfiguration() {
        return new AndroidUpnpServiceConfiguration() {
            @Override
            public int getAliveIntervalMillis() {
                return 5 * 1000;
            }
        };
    }

    @Override
    public void onCreate() {
        LoggingUtil.resetRootHandler(new FixedAndroidLogHandler());
        super.onCreate();
        sRunningInstance = this;

        mPlayQueueManager = PlayQueueController.getInstance(getApplicationContext());
        mRenderControlManager.addControl(AudioRenderController.getInstance(getApplicationContext()));
        mRenderControlManager.addControl(AVTransportController.getInstance());
        UpnpNetworkUtils networkUtils = new UpnpNetworkUtils(getApplicationContext());
        macAddress = networkUtils.getWlanMACAddress();
        SWDeviceStatus.setMacAddress(networkUtils.getWlanMACAddress());
        Log.e(TAG, "dlna ip: " + networkUtils.getWlanIpAddress() + " macAddress: " + macAddress);
        SWDeviceStatus.setIpAddress(networkUtils.getWlanIpAddress());
        try {
            mRendererDevice = createRendererDevice(getApplicationContext());
            upnpService.getRegistry().addDevice(mRendererDevice);
            stateHandler.postDelayed(stateSampler, 1000);
        } catch (Exception e) {
            e.printStackTrace();
            stopSelf();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {
        destroyed = true;
        stateHandler.removeCallbacks(stateSampler);
        sRunningInstance = null;
        if (mAvTransportService != null) {
            mAvTransportService.close();
        }
        if (mAudioRenderService != null) {
            mAudioRenderService.close();
        }
        if (mPlayQueueService != null) mPlayQueueService.close();
        transportEventExecutor.shutdown();
        if (mRendererDevice != null && upnpService != null && upnpService.getRegistry() != null) {
            upnpService.getRegistry().removeDevice(mRendererDevice);
        }
        super.onDestroy();
    }

    @Override
    protected void onUpnpServiceShutdownCompleted() {
        Log.i(TAG, "UPnP shutdown completed for renderer service");
        UpnpServerManager.onRendererServiceShutdownCompleted();
    }

    private static final String DMS_DESC = "MPI MediaPlayer";
    private static final String ID_SALT = "MediaPlayer";
    public final static String TYPE_MEDIA_PLAYER = "MediaRenderer";
    private final static int VERSION = 1;

    protected LocalDevice createRendererDevice(Context context) throws ValidationException, IOException {
        UDN udn = createUniqueSystemIdentifier(ID_SALT);
        Log.i(TAG, "udn uuid: " + udn.getIdentifierString());
        DeviceIdentity deviceIdentity = new DeviceIdentity(udn);
        UDADeviceType deviceType = new UDADeviceType(TYPE_MEDIA_PLAYER, VERSION);
        DeviceDetails details = new DeviceDetails(SWDeviceStatus.deviceName,
                new ManufacturerDetails(android.os.Build.MANUFACTURER),
                new ModelDetails(android.os.Build.MODEL, DMS_DESC, "v1", String.format("http://%s", SWDeviceStatus.companyWebsite))) {
            @Override public String getFriendlyName() {
                return SWDeviceStatus.deviceName;
            }
        };
        Icon[] icons = null;
        return new LocalDevice(deviceIdentity, deviceType, details, icons, generateLocalServices());
    }

    @SuppressWarnings("unchecked")
    protected LocalService<?>[] generateLocalServices() {

        // connection
        LocalService<ConnectionManagerServiceImpl> connectionManagerService = new AnnotationLocalServiceBinder().read(ConnectionManagerServiceImpl.class);
        connectionManagerService.setManager(new DefaultServiceManager<ConnectionManagerServiceImpl>(connectionManagerService, ConnectionManagerServiceImpl.class) {
            @Override
            protected ConnectionManagerServiceImpl createServiceInstance() {
                return new ConnectionManagerServiceImpl();
            }
        });

        // av transport service
        mAvTransportLastChange = new LastChange(new AVTransportLastChangeParser());
        final LastChange avTransportLastChange = mAvTransportLastChange;
        LocalService<AVTransportServiceImpl> avTransportService = new AnnotationLocalServiceBinder().read(AVTransportServiceImpl.class);
        avTransportService.setManager(new LastChangeAwareServiceManager<AVTransportServiceImpl>(avTransportService, new AVTransportLastChangeParser()) {
            @Override
            protected AVTransportServiceImpl createServiceInstance() {
                if (mAvTransportService != null) {
                    mAvTransportService.close();
                }
                mAvTransportService = new AVTransportServiceImpl(avTransportLastChange,
                        mRenderControlManager, event -> transportEventExecutor.execute(() -> {
                            for (int attempt = 1; attempt <= EVENT_DISPATCH_ATTEMPTS; attempt++) {
                                try {
                                    avTransportService.getManager().execute(manager -> event.run());
                                    return;
                                } catch (Exception exception) {
                                    Log.w(TAG, "dispatch AVTransport state event failed, attempt " + attempt, exception);
                                }
                            }
                        }));
                return mAvTransportService;
            }

            @Override
            protected int getLockTimeoutMillis() {
                return 2500;
            }
        });

        // render service
        mAudioControlLastChange = new LastChange(new RenderingControlLastChangeParser());
        final LastChange audioControlLastChange = mAudioControlLastChange;
        LocalService<AudioRenderServiceImpl> renderingControlService = new AnnotationLocalServiceBinder().read(AudioRenderServiceImpl.class);
        renderingControlService.setManager(new LastChangeAwareServiceManager<AudioRenderServiceImpl>(renderingControlService, new RenderingControlLastChangeParser()) {
            @Override
            protected AudioRenderServiceImpl createServiceInstance() {
                if (mAudioRenderService != null) {
                    mAudioRenderService.close();
                }
                mAudioRenderService = new AudioRenderServiceImpl(audioControlLastChange,
                        mRenderControlManager, RoleController.getInstance(),
                        event -> transportEventExecutor.execute(() -> {
                            for (int attempt = 1; attempt <= EVENT_DISPATCH_ATTEMPTS; attempt++) {
                                try {
                                    renderingControlService.getManager().execute(manager -> event.run());
                                    return;
                                } catch (Exception exception) {
                                    Log.w(TAG, "dispatch RenderingControl role event failed, attempt " + attempt, exception);
                                }
                            }
                        }));
                return mAudioRenderService;
            }

            @Override
            protected int getLockTimeoutMillis() {
                return 2500;
            }
        });

        // playQueue service
        mPlayQueueLastChange = new LastChange(new PlayQueueLastChangeParser());
        final LastChange playQueueLastChange = mPlayQueueLastChange;
        LocalService<PlayQueueServiceImpl> playQueueService = new AnnotationLocalServiceBinder().read(PlayQueueServiceImpl.class);
        playQueueService.setManager(new LastChangeAwareServiceManager<PlayQueueServiceImpl>(playQueueService, new PlayQueueLastChangeParser()) {
            @Override
            protected PlayQueueServiceImpl createServiceInstance() {
                if (mPlayQueueService != null) mPlayQueueService.close();
                mPlayQueueService = new PlayQueueServiceImpl(playQueueLastChange, mPlayQueueManager,
                        event -> transportEventExecutor.execute(() -> {
                            try {
                                playQueueService.getManager().execute(manager -> event.run());
                            } catch (Exception exception) {
                                Log.w(TAG, "dispatch PlayQueue event failed", exception);
                            }
                        }));
                return mPlayQueueService;
            }
        });


        return new LocalService[]{connectionManagerService, avTransportService, renderingControlService, playQueueService};
    }

    private static UDN createUniqueSystemIdentifier(@SuppressWarnings("SameParameterValue") String salt) {
        StringBuilder builder = new StringBuilder();
        builder.append(SWDeviceStatus.companyWebsite);
        builder.append(android.os.Build.MODEL);
        builder.append(android.os.Build.MANUFACTURER);
        builder.append(macAddress);
        try {
            byte[] hash = MessageDigest.getInstance("MD5").digest(builder.toString().getBytes());
            return new UDN(new UUID(new BigInteger(-1, hash).longValue(), salt.hashCode()));
        } catch (Exception ex) {
            return new UDN(ex.getMessage() != null ? ex.getMessage() : "UNKNOWN");
        }
    }

    // -------------------------------------------------------------------------------------------
    // - Binder
    // -------------------------------------------------------------------------------------------
    public class RendererServiceBinder extends Binder {
        public DLNARendererService getRendererService() {
            return DLNARendererService.this;
        }
    }
}
