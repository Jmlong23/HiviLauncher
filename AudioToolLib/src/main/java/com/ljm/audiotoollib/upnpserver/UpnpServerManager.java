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
    private static final long RESTART_BIND_DELAY_MS = 1500L;

    private PlayQueueController playQueueManager;
    private AVTransportController avTransportController;

    private AudioRenderController audioRenderController;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingRestartAndBindRunnable;

    public UpnpServerManager() {
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
    }

    private void bindService(Context context, ServiceConnection conn, int flags) {
        context.getApplicationContext().bindService(createServiceIntent(context), conn, flags);
    }

    private void startService(Context context) {
        context.getApplicationContext().startService(createServiceIntent(context));
    }

    public void stopService(Context context) {
        cancelPendingRestartAndBind();
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

    public void restartAndBindUpnpServer(Context context, ServiceConnection conn, int flags, String deviceName) {
        Log.i(TAG, "restartAndBindUpnpServer name: " + deviceName
                + ", conn=" + conn + ", flags=" + flags);
        cancelPendingRestartAndBind();
        SWDeviceStatus.updateDeviceName(deviceName);
        Context appContext = context.getApplicationContext();
        stopService(appContext);
        pendingRestartAndBindRunnable = () -> {
            try {
                Log.i(TAG, "restartAndBindUpnpServer delayed start name=" + deviceName
                        + ", delayMs=" + RESTART_BIND_DELAY_MS);
                prepareUpnpInfrastructure(appContext, true);
                bindService(appContext, conn, flags);
                initControllers(appContext);
                Log.i(TAG, "restartAndBindUpnpServer finish name=" + deviceName + ", conn=" + conn + ", flags=" + flags);
            } finally {
                pendingRestartAndBindRunnable = null;
            }
        };
        mainHandler.postDelayed(pendingRestartAndBindRunnable, RESTART_BIND_DELAY_MS);
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
