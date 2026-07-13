package com.ljm.audiotoollib.upnpserver.httpserver;

import android.content.Context;

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
import com.ljm.audiotoollib.upnpserver.cling.model.types.DeviceType;
import com.ljm.audiotoollib.upnpserver.cling.model.types.UDADeviceType;
import com.ljm.audiotoollib.upnpserver.cling.model.types.UDN;
import com.ljm.audiotoollib.upnpserver.core.Utils;
import com.ljm.audiotoollib.upnpserver.entity.SWDeviceStatus;

import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.UUID;

public final class HttpMediaServer {

    private LocalDevice mDevice;
    private IResourceServer mResourceServer;
    private final String mBaseUrl;
    private static HttpMediaServer instance;
    private boolean serverStarted;

    public static synchronized HttpMediaServer getInstance(Context context) {
        if(instance == null) {
            instance = new HttpMediaServer(context);
        }
        return instance;
    }

    public static synchronized HttpMediaServer resetInstance(Context context) {
        if (instance != null) {
            instance.stop();
        }
        instance = new HttpMediaServer(context);
        return instance;
    }

    public HttpMediaServer(Context context) {
        this(context, new IResourceServer.IResourceServerFactory.DefaultResourceServerFactoryImpl(SWDeviceStatus.AND_HARDWARE_PORT));
    }

    public HttpMediaServer(Context context, IResourceServer.IResourceServerFactory factory) {
        String address = Utils.getWiFiInfoIPAddress(context);
        mBaseUrl = String.format("http://%s:%s", address, factory.getPort());
        ContentFactory.getInstance().setServerUrl(context, mBaseUrl);
        try {
            mDevice = createLocalDevice(context, address);
            mResourceServer = factory.getInstance();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized void startServer() {
        if (mResourceServer != null && !serverStarted) {
            mResourceServer.startServer();
            serverStarted = true;
        }
    }

    public synchronized void stop() {
        if (mResourceServer != null && serverStarted) {
            mResourceServer.stopServer();
            serverStarted = false;
        }
    }

    public String getBaseUrl() {
        return mBaseUrl;
    }

    public LocalDevice getDevice() {
        return mDevice;
    }

    private static final String DMS_DESC = "MSI MediaServer";
    private static final String ID_SALT = "GNaP-MediaServer";
    public final static String TYPE_MEDIA_SERVER = "MediaServer";
    private final static int VERSION = 1;


    @SuppressWarnings({"unchecked", "rawtypes"})
    protected LocalDevice createLocalDevice(Context context, String ipAddress) throws ValidationException {
        DeviceIdentity identity = new DeviceIdentity(createUniqueSystemIdentifier(ID_SALT, ipAddress));
        DeviceType type = new UDADeviceType(TYPE_MEDIA_SERVER, VERSION);
        DeviceDetails details = new DeviceDetails(String.format("DMS  (%s)", android.os.Build.MODEL),
                new ManufacturerDetails(android.os.Build.MANUFACTURER),
                new ModelDetails(android.os.Build.MODEL, DMS_DESC, "v1", mBaseUrl));
        final LocalService<?> service = new AnnotationLocalServiceBinder().read(ContentDirectoryService.class);
        service.setManager(new DefaultServiceManager(service, ContentDirectoryService.class));
        Icon icon = null;
        try {
            icon = new Icon("image/png", 48, 48, 32, "msi.png",
                    context.getResources().getAssets().open("ic_launcher.png"));
        } catch (IOException ignored) {
        }
        return new LocalDevice(identity, type, details, icon, service);
    }

    private static UDN createUniqueSystemIdentifier(@SuppressWarnings("SameParameterValue") String salt, String ipAddress) {
        StringBuilder builder = new StringBuilder();
        builder.append(ipAddress);
        builder.append(android.os.Build.MODEL);
        builder.append(android.os.Build.MANUFACTURER);
        try {
            byte[] hash = MessageDigest.getInstance("MD5").digest(builder.toString().getBytes());
            return new UDN(new UUID(new BigInteger(-1, hash).longValue(), salt.hashCode()));
        } catch (Exception ex) {
            return new UDN(ex.getMessage() != null ? ex.getMessage() : "UNKNOWN");
        }
    }
}
