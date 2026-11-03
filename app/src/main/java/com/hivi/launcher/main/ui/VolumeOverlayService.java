package com.hivi.launcher.main.ui;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.view.ContextThemeWrapper;
import android.view.WindowManager;

import com.hivi.launcher.R;
import com.hivi.launcher.audio.AudioRouteController;
import com.hivi.launcher.utils.LocaleHelper;
import com.hivi.launcher.utils.log.AppLog;

/** Owns the volume window without starting or bringing any activity to the foreground. */
public final class VolumeOverlayService extends Service implements VolumeDialog.Listener {
    public static final String ACTION_TOGGLE = "com.hivi.launcher.action.TOGGLE_VOLUME_DIALOG";
    private static final String TAG = "VolumeOverlayService";
    private static VolumeOverlayService sInstance;

    private final AudioRouteController mAudioController = AudioRouteController.getInstance();
    private VolumeDialog mDialog;
    private final AudioRouteController.AmplifierVolumeListener mVolumeListener =
            (volume, muted) -> {
                if (mDialog != null) {
                    mDialog.updateVolume(volume, muted);
                }
            };
    private int mLastStartId;

    public static void show(Context context) {
        context.startService(new Intent(context, VolumeOverlayService.class));
    }

    public static boolean isShowing() {
        return sInstance != null && sInstance.mDialog != null && sInstance.mDialog.isShowing();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        mAudioController.initialize(this);
        mAudioController.addAmplifierVolumeListener(mVolumeListener);
        Context context = new ContextThemeWrapper(LocaleHelper.applyLocale(this), R.style.AppTheme);
        mDialog = new VolumeDialog(context, this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mLastStartId = startId;
        if (intent != null && ACTION_TOGGLE.equals(intent.getAction()) && mDialog.isShowing()) {
            mDialog.dismiss();
            return START_NOT_STICKY;
        }
        try {
            mDialog.show(mAudioController.getAmplifierVolumePercent(),
                    mAudioController.isAmplifierMuted());
        } catch (WindowManager.BadTokenException | SecurityException e) {
            AppLog.e(TAG, "Unable to show volume overlay", e);
            stopSelf(startId);
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onVolumeAdjusted(int direction) {
        mAudioController.adjustAmplifierVolume(direction);
    }

    @Override
    public void onVolumeChanged(int volumePercent) {
        mAudioController.setAmplifierVolume(volumePercent);
    }

    @Override
    public void onMuteToggleRequested() {
        mAudioController.toggleAmplifierMute();
    }

    @Override
    public void onDialogDismissed() {
        stopSelf(mLastStartId);
    }

    @Override
    public void onDestroy() {
        mAudioController.removeAmplifierVolumeListener(mVolumeListener);
        sInstance = null;
        if (mDialog != null) {
            mDialog.dismiss();
            mDialog = null;
        }
        super.onDestroy();
    }
}
