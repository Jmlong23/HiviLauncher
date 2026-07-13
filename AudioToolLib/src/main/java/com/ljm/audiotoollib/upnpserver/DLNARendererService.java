package com.ljm.audiotoollib.upnpserver;

import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
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
import com.ljm.audiotoollib.upnpserver.utils.UpnpNetworkUtils;

import org.seamless.util.logging.LoggingUtil;

import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.UUID;

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
    private static String macAddress = "";

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
        if (mRendererDevice != null && upnpService != null && upnpService.getRegistry() != null) {
            upnpService.getRegistry().removeDevice(mRendererDevice);
        }
        super.onDestroy();
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
                new ModelDetails(android.os.Build.MODEL, DMS_DESC, "v1", String.format("http://%s", SWDeviceStatus.companyWebsite)));
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
        LocalService<AVTransportServiceImpl> avTransportService = new AnnotationLocalServiceBinder().read(AVTransportServiceImpl.class);
        avTransportService.setManager(new LastChangeAwareServiceManager<AVTransportServiceImpl>(avTransportService, new AVTransportLastChangeParser()) {
            @Override
            protected AVTransportServiceImpl createServiceInstance() {
                return new AVTransportServiceImpl(mAvTransportLastChange, mRenderControlManager);
            }
        });

        // render service
        mAudioControlLastChange = new LastChange(new RenderingControlLastChangeParser());
        LocalService<AudioRenderServiceImpl> renderingControlService = new AnnotationLocalServiceBinder().read(AudioRenderServiceImpl.class);
        renderingControlService.setManager(new LastChangeAwareServiceManager<AudioRenderServiceImpl>(renderingControlService, new RenderingControlLastChangeParser()) {
            @Override
            protected AudioRenderServiceImpl createServiceInstance() {
                return new AudioRenderServiceImpl(mAudioControlLastChange, mRenderControlManager);
            }
        });

        // playQueue service
        mPlayQueueLastChange = new LastChange(new PlayQueueLastChangeParser());
        LocalService<PlayQueueServiceImpl> playQueueService = new AnnotationLocalServiceBinder().read(PlayQueueServiceImpl.class);
        playQueueService.setManager(new LastChangeAwareServiceManager<PlayQueueServiceImpl>(playQueueService, new PlayQueueLastChangeParser()) {
            @Override
            protected PlayQueueServiceImpl createServiceInstance() {
                return new PlayQueueServiceImpl(mPlayQueueLastChange, mPlayQueueManager);
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
