package com.hivi.launcher.bluetooth.model;

import android.content.Context;
import android.content.SharedPreferences;

import com.hivi.launcher.audio.AudioRouteController;
import com.hivi.launcher.utils.log.AppLog;

import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Authoritative Bluetooth state keeper backed by the MCU Bluetooth module.
 *
 * <p>The phone connects to the speaker through the MCU, so every connection transition,
 * device name, and playback state arrives as a serial report. The launcher only sends
 * requests (disconnect/connect/clear/transport) and mirrors what the MCU confirms, the
 * same way the Hivi-Audio launcher does. The last connection state is persisted so UI
 * can restore instantly after a reboot or locale change; the MCU re-sync corrects it via
 * the connection-state query sent at startup.</p>
 */
public final class McuBluetoothController implements AudioRouteController.McuBluetoothListener {
    private static final String TAG = "McuBluetoothController";
    private static final String PREFERENCES_NAME = "mcu_bluetooth";
    private static final String PREFERENCE_CONNECTED = "bt_connected";

    private static final McuBluetoothController INSTANCE = new McuBluetoothController();

    public interface Listener {
        /** Connection, device-name, or play state changed; re-read the getters. */
        void onBluetoothStateChanged();

        /** The MCU confirmed the pairing list was cleared. */
        void onBluetoothPairingsCleared();
    }

    private final AudioRouteController mAudioRouteController = AudioRouteController.getInstance();

    private Context mAppContext;
    private boolean mStarted;
    private boolean mConnected;
    private boolean mPlaying;
    private String mBtName = "";
    private String mPhoneName = "";

    private McuBluetoothController() {
    }

    public static McuBluetoothController getInstance() {
        return INSTANCE;
    }

    public synchronized void start(Context context) {
        if (context == null) {
            return;
        }
        mAppContext = context.getApplicationContext();
        if (mStarted) {
            return;
        }
        mStarted = true;
        mAudioRouteController.initialize(mAppContext);
        mAudioRouteController.addMcuBluetoothListener(this);
        mConnected = readPersistedConnected(mAppContext);
        // The MCU is the source of truth: ask it for the live connection state and the
        // local/phone Bluetooth names so a stale cached flag gets corrected right away.
        mAudioRouteController.queryBluetoothConnectionState();
        mAudioRouteController.queryBluetoothName();
        mAudioRouteController.queryBluetoothPhoneName();
    }

    public synchronized boolean isConnected() {
        return mConnected;
    }

    public synchronized boolean isPlaying() {
        return mConnected && mPlaying;
    }

    /** Local speaker Bluetooth name as reported by the MCU. */
    public synchronized String getOwnName() {
        return mBtName;
    }

    /** Currently connected phone name, or empty when nothing is connected. */
    public synchronized String getPhoneName() {
        return mConnected ? mPhoneName : "";
    }

    public void disconnect() {
        mAudioRouteController.sendBluetoothDisconnect();
    }

    public void connect() {
        mAudioRouteController.sendBluetoothConnect();
    }

    public void clearPairings() {
        mAudioRouteController.sendBluetoothClearPairings();
    }

    public void togglePlayback() {
        boolean play = !isPlaying();
        mAudioRouteController.sendBluetoothPlayback(play);
        // Mirror the Wi-Fi music page: reflect the requested state right away instead of
        // waiting for the MCU report, which corrects the icon if the command fails.
        synchronized (this) {
            mPlaying = play;
        }
        notifyStateChanged();
    }

    public void next() {
        mAudioRouteController.sendBluetoothNext();
    }

    public void previous() {
        mAudioRouteController.sendBluetoothPrevious();
    }

    @Override
    public void onBluetoothConnectionChanged(boolean connected) {
        synchronized (this) {
            mConnected = connected;
            if (!connected) {
                mPlaying = false;
                mPhoneName = "";
            }
        }
        persistConnected(connected);
        notifyStateChanged();
    }

    @Override
    public void onBluetoothPairingsCleared() {
        synchronized (this) {
            mConnected = false;
            mPlaying = false;
            mPhoneName = "";
        }
        persistConnected(false);
        for (Listener listener : copyListeners()) {
            listener.onBluetoothPairingsCleared();
        }
        notifyStateChanged();
    }

    @Override
    public void onBluetoothPhoneNameChanged(String phoneName) {
        synchronized (this) {
            mPhoneName = phoneName == null ? "" : phoneName;
        }
        notifyStateChanged();
    }

    @Override
    public void onBluetoothNameChanged(String btName) {
        synchronized (this) {
            mBtName = btName == null ? "" : btName;
        }
        notifyStateChanged();
    }

    @Override
    public void onBluetoothPlayStateChanged(boolean playing) {
        synchronized (this) {
            mPlaying = playing;
        }
        notifyStateChanged();
    }

    private final CopyOnWriteArrayList<Listener> mListeners = new CopyOnWriteArrayList<>();

    public void addListener(Listener listener) {
        if (listener != null) {
            mListeners.addIfAbsent(listener);
        }
    }

    public void removeListener(Listener listener) {
        if (listener != null) {
            mListeners.remove(listener);
        }
    }

    private Iterable<Listener> copyListeners() {
        return new ArrayList<>(mListeners);
    }

    private void notifyStateChanged() {
        for (Listener listener : copyListeners()) {
            listener.onBluetoothStateChanged();
        }
    }

    private boolean readPersistedConnected(Context context) {
        return getPreferences(context).getBoolean(PREFERENCE_CONNECTED, false);
    }

    private void persistConnected(boolean connected) {
        Context context = mAppContext;
        if (context == null) {
            return;
        }
        getPreferences(context).edit().putBoolean(PREFERENCE_CONNECTED, connected).apply();
        AppLog.i(TAG, "Persisted MCU bluetooth connected=" + connected);
    }

    private SharedPreferences getPreferences(Context context) {
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }
}
