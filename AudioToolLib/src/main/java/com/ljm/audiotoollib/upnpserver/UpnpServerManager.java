package com.ljm.audiotoollib.upnpserver;

import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.ljm.audiotoollib.upnpserver.entity.SWDeviceStatus;
import com.ljm.audiotoollib.upnpserver.cling.protocol.sync.ReceivingRetrieval;
import com.ljm.audiotoollib.upnpserver.httpserver.HttpMediaServer;
import com.ljm.audiotoollib.upnpserver.service.AVTransportController;
import com.ljm.audiotoollib.upnpserver.service.AudioRenderController;
import com.ljm.audiotoollib.upnpserver.service.PlayQueueController;


public class UpnpServerManager {
    private static final String TAG = UpnpServerManager.class.getSimpleName();
    private static final long RESTART_SHUTDOWN_TIMEOUT_MS = 10000L;
    private static volatile UpnpServerManager activeManager;

    private PlayQueueController playQueueManager;
    private AVTransportController avTransportController;

    private AudioRenderController audioRenderController;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingRestartAndBindRunnable;
    private Runnable pendingShutdownTimeoutRunnable;
    private boolean restartAndBindInProgress;
    private boolean waitingForRendererShutdown;
    private Context pendingRestartContext;
    private ServiceConnection pendingRestartConnection;
    private int pendingRestartFlags;
    private String pendingRestartDeviceName;

    public UpnpServerManager() {
        activeManager = this;
        avTransportController = AVTransportController.getInstance();
    }


    private Intent createServiceIntent(Context context) {
        return new Intent(context, DLNARendererService.class);
    }

    private void cancelPendingRestartAndBind() {
        if (pendingRestartAndBindRunnable != null) {
            mainHandler.removeCallbacks(pendingRestartAndBindRunnable);
            pendingRestartAndBindRunnable = null;
        }
        if (pendingShutdownTimeoutRunnable != null) {
            mainHandler.removeCallbacks(pendingShutdownTimeoutRunnable);
            pendingShutdownTimeoutRunnable = null;
        }
    }

    private void clearRestartAndBindState() {
        cancelPendingRestartAndBind();
        restartAndBindInProgress = false;
        waitingForRendererShutdown = false;
        pendingRestartContext = null;
        pendingRestartConnection = null;
        pendingRestartFlags = 0;
        pendingRestartDeviceName = null;
    }

    private void bindService(Context context, ServiceConnection conn, int flags) {
        context.getApplicationContext().bindService(createServiceIntent(context), conn, flags);
    }

    private void startService(Context context) {
        context.getApplicationContext().startService(createServiceIntent(context));
    }

    public void stopService(Context context) {
        clearRestartAndBindState();
        context.getApplicationContext().stopService(createServiceIntent(context));
    }

    private void initControllers(Context context) {
        playQueueManager = PlayQueueController.getInstance(context);
        avTransportController = AVTransportController.getInstance();
        audioRenderController = AudioRenderController.getInstance(context);
    }

    private void prepareUpnpInfrastructure(Context context, boolean forceRefreshHttpServer) {
        ReceivingRetrieval.clearDescriptorCache();
        if (forceRefreshHttpServer) {
            HttpMediaServer.resetInstance(context);
        }
        HttpMediaServer.getInstance(context).startServer();
        startService(context);
    }

    public void restartUpnpServer(Context context, String deviceName) {
        cancelPendingRestartAndBind();
        Log.i(TAG, "restartUpnpServer name: " + deviceName);
        SWDeviceStatus.updateDeviceName(deviceName);
        stopService(context);
        prepareUpnpInfrastructure(context, true);
        initControllers(context);
    }

    /**
     * 原地更新设备名称并重发 SSDP 公告（byebye→alive），不销毁/重建 UPnP 服务。
     * 改名应优先走此方法；仅当 UPnP 服务未运行（无法原地重注册）时，调用方才回退到
     * restartAndBindUpnpServer 完整重启。
     *
     * @return true 已原地完成；false 服务未就绪，需调用方兜底重启
     */
    public boolean updateDeviceNameInPlace(String deviceName) {
        Log.i(TAG, "updateDeviceNameInPlace name: " + deviceName);
        SWDeviceStatus.updateDeviceName(deviceName);
        DLNARendererService service = DLNARendererService.getRunningInstance();
        if (service == null) {
            Log.w(TAG, "updateDeviceNameInPlace renderer service not running, need full restart");
            return false;
        }
        return service.reannounceWithUpdatedDeviceName();
    }

    public void restartAndBindUpnpServer(Context context, ServiceConnection conn, int flags, String deviceName) {
        Log.i(TAG, "restartAndBindUpnpServer name: " + deviceName
                + ", conn=" + conn + ", flags=" + flags);
        if (restartAndBindInProgress) {
            Log.i(TAG, "restartAndBindUpnpServer already in progress, ignore duplicate request");
            return;
        }
        SWDeviceStatus.updateDeviceName(deviceName);
        Context appContext = context.getApplicationContext();
        restartAndBindInProgress = true;
        waitingForRendererShutdown = true;
        pendingRestartContext = appContext;
        pendingRestartConnection = conn;
        pendingRestartFlags = flags;
        pendingRestartDeviceName = deviceName;

        boolean stopRequested = appContext.stopService(createServiceIntent(appContext));
        if (!stopRequested) {
            Log.i(TAG, "restartAndBindUpnpServer no running renderer service, start immediately");
            waitingForRendererShutdown = false;
            schedulePendingRestartAndBind("NO_RUNNING_SERVICE");
            return;
        }

        Log.i(TAG, "restartAndBindUpnpServer waiting for previous renderer UPnP shutdown");
        pendingShutdownTimeoutRunnable = () -> {
            if (!restartAndBindInProgress || !waitingForRendererShutdown) {
                return;
            }
            Log.w(TAG, "renderer UPnP shutdown callback timed out, starting replacement after "
                    + RESTART_SHUTDOWN_TIMEOUT_MS + "ms");
            waitingForRendererShutdown = false;
            schedulePendingRestartAndBind("SHUTDOWN_TIMEOUT");
        };
        mainHandler.postDelayed(pendingShutdownTimeoutRunnable, RESTART_SHUTDOWN_TIMEOUT_MS);
    }

    private void schedulePendingRestartAndBind(String reason) {
        if (!restartAndBindInProgress || pendingRestartContext == null) {
            return;
        }
        if (pendingRestartAndBindRunnable != null) {
            mainHandler.removeCallbacks(pendingRestartAndBindRunnable);
        }
        pendingRestartAndBindRunnable = () -> {
            try {
                if (!restartAndBindInProgress || waitingForRendererShutdown) {
                    return;
                }
                Log.i(TAG, "restartAndBindUpnpServer starting replacement, reason=" + reason
                        + ", name=" + pendingRestartDeviceName);
                prepareUpnpInfrastructure(pendingRestartContext, true);
                bindService(pendingRestartContext, pendingRestartConnection, pendingRestartFlags);
                initControllers(pendingRestartContext);
                Log.i(TAG, "restartAndBindUpnpServer replacement start requested, name="
                        + pendingRestartDeviceName + ", conn=" + pendingRestartConnection
                        + ", flags=" + pendingRestartFlags);
            } catch (RuntimeException e) {
                Log.e(TAG, "restartAndBindUpnpServer failed to start replacement", e);
                clearRestartAndBindState();
            } finally {
                pendingRestartAndBindRunnable = null;
            }
        };
        mainHandler.post(pendingRestartAndBindRunnable);
    }

    static void onRendererServiceShutdownCompleted() {
        UpnpServerManager manager = activeManager;
        if (manager != null) {
            manager.handleRendererServiceShutdownCompleted();
        }
    }

    private void handleRendererServiceShutdownCompleted() {
        mainHandler.post(() -> {
            if (!restartAndBindInProgress || !waitingForRendererShutdown) {
                Log.i(TAG, "renderer UPnP shutdown completed with no pending restart");
                return;
            }
            Log.i(TAG, "renderer UPnP shutdown completed, starting replacement");
            waitingForRendererShutdown = false;
            if (pendingShutdownTimeoutRunnable != null) {
                mainHandler.removeCallbacks(pendingShutdownTimeoutRunnable);
                pendingShutdownTimeoutRunnable = null;
            }
            schedulePendingRestartAndBind("SHUTDOWN_COMPLETED");
        });
    }

    public boolean isRestartAndBindInProgress() {
        return restartAndBindInProgress;
    }

    public void onRendererServiceBound() {
        if (!restartAndBindInProgress) {
            return;
        }
        Log.i(TAG, "restartAndBindUpnpServer renderer service bound");
        clearRestartAndBindState();
    }

    public void initUpnpServer(Context context, ServiceConnection conn, int flags, String deviceName){
        cancelPendingRestartAndBind();
        Log.i(TAG, "initUpnpServer name: " + deviceName);
        SWDeviceStatus.updateDeviceName(deviceName);
        prepareUpnpInfrastructure(context, false);
        bindService(context, conn, flags);
        initControllers(context);
    }

    public PlayQueueController getPlayQueueManager() {
        return playQueueManager;
    }

    public AVTransportController getAvTransportController() {
        if (avTransportController == null) {
            avTransportController = AVTransportController.getInstance();
        }
        return avTransportController;
    }

    public  AudioRenderController getAudioRenderController() { return audioRenderController; }
}
