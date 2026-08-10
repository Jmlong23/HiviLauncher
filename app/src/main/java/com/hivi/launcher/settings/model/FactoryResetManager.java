package com.hivi.launcher.settings.model;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import com.hivi.launcher.R;
import com.hivi.launcher.ai.presenter.AiPresenter;
import com.hivi.launcher.audio.AudioRouteController;
import com.hivi.launcher.music.model.BluetoothMediaController;
import com.hivi.launcher.music.model.UpnpPlaybackManager;
import com.hivi.launcher.onboarding.model.FirstUseGuideStore;
import com.hivi.launcher.utils.LocaleHelper;
import com.hivi.launcher.utils.log.AppLog;
import com.hivi.launcher.utils.network.AuthorizationStore;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Coordinates the Launcher-side parts of a device factory reset.
 *
 * <p>The MCU reset command is deliberately sent before any local state is removed. This prevents
 * the UI from losing the user's stored information if the hardware control path is unavailable.
 * Once that command has been accepted for transmission, Android Bluetooth/Wi-Fi state and
 * Launcher-owned persisted data are cleared as part of the same operation.</p>
 */
public final class FactoryResetManager {
    private static final String TAG = "FactoryResetManager";
    private static final int DEFAULT_SCREEN_BRIGHTNESS = 128;

    public interface Callback {
        void onProgress(int progress, int statusResId);

        void onSuccess();

        void onFailure(Throwable throwable);
    }

    private final Context mApplicationContext;
    private final AudioRouteController mAudioRouteController;
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean mResetInProgress = new AtomicBoolean();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    public FactoryResetManager(Context context) {
        Context applicationContext = context.getApplicationContext();
        mApplicationContext = applicationContext == null ? context : applicationContext;
        mAudioRouteController = AudioRouteController.getInstance();
    }

    public void reset(Callback callback) {
        if (callback == null || !mResetInProgress.compareAndSet(false, true)) {
            return;
        }
        notifyProgress(callback, 0, R.string.factory_reset_preparing);
        stopActivePlayback();
        mAudioRouteController.initialize(mApplicationContext);
        mAudioRouteController.requestFactoryReset(commandSent -> {
            if (!commandSent) {
                finishWithFailure(callback, new IOException(
                        "Unable to send the MCU factory-reset command."));
                return;
            }
            try {
                mExecutor.execute(() -> clearLocalState(callback));
            } catch (Throwable throwable) {
                finishWithFailure(callback, throwable);
            }
        });
    }

    /**
     * Releases the worker after a completed reset attempt. An active reset is intentionally not
     * cancelled because it owns no Activity or Fragment state and must finish clearing data.
     */
    public void destroy() {
        if (!mResetInProgress.get()) {
            mExecutor.shutdownNow();
        }
    }

    private void clearLocalState(Callback callback) {
        try {
            notifyProgress(callback, 20, R.string.factory_reset_resetting_device);
            clearSavedWifiNetworks();
            clearBluetoothPairings();

            notifyProgress(callback, 50, R.string.factory_reset_clearing_network);
            resetSystemDisplaySettings();

            notifyProgress(callback, 70, R.string.factory_reset_clearing_local_data);
            clearLauncherPreferences();

            notifyProgress(callback, 90, R.string.factory_reset_finishing);
            clearLauncherFiles();

            notifyProgress(callback, 100, R.string.factory_reset_finishing);
            finishWithSuccess(callback);
        } catch (Throwable throwable) {
            finishWithFailure(callback, throwable);
        }
    }

    @SuppressWarnings("deprecation")
    private void clearSavedWifiNetworks() {
        WifiManager wifiManager = (WifiManager) mApplicationContext.getSystemService(
                Context.WIFI_SERVICE);
        if (wifiManager == null) {
            AppLog.w(TAG, "Wi-Fi manager is unavailable during factory reset.");
            return;
        }
        try {
            wifiManager.disconnect();
            List<WifiConfiguration> configurations = wifiManager.getConfiguredNetworks();
            if (configurations == null || configurations.isEmpty()) {
                return;
            }
            for (WifiConfiguration configuration : new ArrayList<>(configurations)) {
                if (configuration == null || configuration.networkId < 0) {
                    continue;
                }
                if (!wifiManager.removeNetwork(configuration.networkId)) {
                    AppLog.w(TAG, "Unable to remove saved Wi-Fi network id="
                            + configuration.networkId);
                }
            }
            wifiManager.saveConfiguration();
        } catch (RuntimeException exception) {
            // These APIs are system privileges on the production image. Do not leave an already
            // completed MCU reset in a failed state if a platform implementation rejects one
            // individual network operation.
            AppLog.w(TAG, "Unable to clear all saved Wi-Fi networks.", exception);
        }
    }

    private void clearBluetoothPairings() {
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null) {
                return;
            }
            Set<BluetoothDevice> bondedDevices = adapter.getBondedDevices();
            if (bondedDevices == null || bondedDevices.isEmpty()) {
                return;
            }
            Method removeBond = BluetoothDevice.class.getMethod("removeBond");
            for (BluetoothDevice device : new HashSet<>(bondedDevices)) {
                if (device == null) {
                    continue;
                }
                try {
                    Object result = removeBond.invoke(device);
                    if (result instanceof Boolean && !((Boolean) result)) {
                        AppLog.w(TAG, "Unable to remove Bluetooth pairing: " + device);
                    }
                } catch (ReflectiveOperationException | RuntimeException exception) {
                    AppLog.w(TAG, "Unable to remove Bluetooth pairing: " + device, exception);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            AppLog.w(TAG, "Unable to enumerate Bluetooth pairings.", exception);
        }
    }

    private void resetSystemDisplaySettings() {
        try {
            boolean modeSaved = Settings.System.putInt(mApplicationContext.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            boolean brightnessSaved = Settings.System.putInt(
                    mApplicationContext.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS,
                    DEFAULT_SCREEN_BRIGHTNESS);
            if (!modeSaved || !brightnessSaved) {
                AppLog.w(TAG, "Unable to restore one or more screen brightness defaults.");
            }
        } catch (RuntimeException exception) {
            AppLog.w(TAG, "Unable to restore screen brightness defaults.", exception);
        }
    }

    private void clearLauncherPreferences() throws IOException {
        if (!mAudioRouteController.clearPersistedState(mApplicationContext)) {
            throw new IOException("Unable to clear persisted audio route state.");
        }
        if (!AuthorizationStore.clear(mApplicationContext)) {
            throw new IOException("Unable to clear account authorization state.");
        }
        if (!AiPresenter.clearPersistedSession(mApplicationContext)) {
            throw new IOException("Unable to clear persisted AI session state.");
        }
        if (!LocaleHelper.resetLanguage(mApplicationContext)) {
            throw new IOException("Unable to clear persisted language state.");
        }
        if (!FirstUseGuideStore.clear(mApplicationContext)) {
            throw new IOException("Unable to clear first-use guide state.");
        }
    }

    private void clearLauncherFiles() {
        clearDirectoryContents(mApplicationContext.getFilesDir(), "app files");
        clearDirectoryContents(mApplicationContext.getCacheDir(), "app cache");
        clearDirectoryContents(mApplicationContext.getExternalFilesDir(null), "external app files");
        clearDirectoryContents(mApplicationContext.getExternalCacheDir(), "external app cache");
    }

    private void clearDirectoryContents(File directory, String description) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            return;
        }
        File[] children = directory.listFiles();
        if (children == null) {
            AppLog.w(TAG, "Unable to list " + description + " for factory reset.");
            return;
        }
        for (File child : children) {
            if (!deleteRecursively(child)) {
                AppLog.w(TAG, "Unable to remove " + description + " entry: " + child);
            }
        }
    }

    private boolean deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return true;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) {
                return false;
            }
            for (File child : children) {
                if (!deleteRecursively(child)) {
                    return false;
                }
            }
        }
        return file.delete();
    }

    private void stopActivePlayback() {
        mMainHandler.post(() -> {
            try {
                UpnpPlaybackManager.getInstance().stop();
            } catch (RuntimeException exception) {
                AppLog.w(TAG, "Unable to stop Wi-Fi music before factory reset.", exception);
            }
            try {
                BluetoothMediaController.getInstance().pause();
            } catch (RuntimeException exception) {
                AppLog.w(TAG, "Unable to pause Bluetooth music before factory reset.", exception);
            }
        });
    }

    private void notifyProgress(Callback callback, int progress, int statusResId) {
        callback.onProgress(Math.max(0, Math.min(100, progress)), statusResId);
    }

    private void finishWithSuccess(Callback callback) {
        mResetInProgress.set(false);
        try {
            callback.onSuccess();
        } finally {
            mExecutor.shutdown();
        }
    }

    private void finishWithFailure(Callback callback, Throwable throwable) {
        AppLog.e(TAG, "Factory reset failed.", throwable);
        mResetInProgress.set(false);
        try {
            callback.onFailure(throwable);
        } finally {
            mExecutor.shutdown();
        }
    }
}
