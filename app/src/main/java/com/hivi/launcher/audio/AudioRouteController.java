package com.hivi.launcher.audio;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.hivi.audionativelib.AudioNativeManager;
import com.hivi.audionativelib.manager.serialport.SerialDevice;
import com.hivi.audionativelib.manager.serialport.SerialPortManager;
import com.hivi.audionativelib.manager.serialport.listener.OnOpenSerialPortListener;
import com.hivi.audionativelib.manager.serialport.listener.OnSerialPortDataListener;
import com.hivi.launcher.main.model.MainPage;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AudioRouteController {
    public static final String SERIAL_PORT_CMD_MUSIC_SELECT_KEY = "AXX_IPT_KEY";
    public static final String SERIAL_PORT_CMD_MUSIC_SELECT_HDMI = "AXX+IPT+HDMI\n";
    public static final String SERIAL_PORT_CMD_MUSIC_SELECT_AUX = "AXX+IPT+AUX\n";
    public static final String SERIAL_PORT_CMD_MUSIC_SELECT_BT = "AXX+IPT+BT\n";
    public static final String SERIAL_PORT_CMD_MUSIC_SELECT_WIFI = "AXX+IPT+WIFI\n";
    public static final String SERIAL_PORT_CMD_MUSIC_SELECT_COAX = "AXX+IPT+COAX\n";
    public static final String SERIAL_PORT_CMD_MUSIC_SELECT_LINE = "AXX+IPT+LINE\n";
    public static final String SERIAL_PORT_CMD_MUSIC_SELECT_KARAOK_MICON = "AXX+IPT+MICON\n";
    public static final String SERIAL_PORT_CMD_VOLUME_KEY = "AXX_VOL_KEY";
    public static final String SERIAL_PORT_CMD_VOLUME = "AXX+VOL+@param\n";
    public static final String SERIAL_PORT_MIC_VOLUME_KEY = "AXX_MIC_KEY";
    public static final String SERIAL_PORT_MIC_VOLUME = "AXX+MIC+@param\n";
    public static final String SERIAL_PORT_CMD_SFX_KEY = "AXX_SFX_KEY";
    public static final String SERIAL_PORT_CMD_SFX = "AXX+SFX+@param\n";

    private static final String TAG = "AudioRouteController";
    private static final String PREFERENCES_NAME = "audio_route";
    private static final String PREFERENCE_SELECTED_MODE = "selected_mode";
    private static final String SERIAL_PORT_NAME = "ttyS9";
    private static final int SERIAL_PORT_BAUD_RATE = 9600;
    private static final int DEFAULT_AMPLIFIER_VOLUME = 50;
    private static final AudioRouteController INSTANCE = new AudioRouteController();

    private final ExecutorService mSerialExecutor = Executors.newSingleThreadExecutor();
    private final Object mLock = new Object();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<AmplifierVolumeListener> mAmplifierVolumeListeners =
            new CopyOnWriteArrayList<>();

    private Context mContext;
    private boolean mSerialPortOpened;
    private int mAmplifierVolumePercent = DEFAULT_AMPLIFIER_VOLUME;
    private int mLastAmplifierVolumePercent = DEFAULT_AMPLIFIER_VOLUME;
    private boolean mAmplifierMuted;

    public interface AmplifierVolumeListener {
        void onAmplifierVolumeChanged(int volumePercent, boolean muted);
    }

    private AudioRouteController() {
    }

    public static AudioRouteController getInstance() {
        return INSTANCE;
    }

    public void initialize(Context context) {
        if (context == null) {
            return;
        }
        Context appContext = context.getApplicationContext();
        synchronized (mLock) {
            if (mContext != null) {
                return;
            }
            mContext = appContext;
            mAmplifierVolumePercent = getStoredVolume(appContext,
                    SERIAL_PORT_CMD_VOLUME_KEY, DEFAULT_AMPLIFIER_VOLUME);
            mLastAmplifierVolumePercent = mAmplifierVolumePercent;
        }
        dispatchAmplifierVolumeChanged();
        mSerialExecutor.execute(this::openSerialPortIfNeeded);
    }

    public void selectMode(MainPage page) {
        String command = getCommand(page);
        Context context = getContext();
        if (command == null || context == null) {
            Log.w(TAG, "Ignore input mode selection: " + page);
            return;
        }
        Log.i(TAG, "Selected input mode=" + page + ", command=" + formatCommand(command));
        mSerialExecutor.execute(() -> sendCommand(page, command));
    }

    public MainPage getSelectedMode(Context context) {
        if (context == null) {
            return null;
        }
        String storedPage = getPreferences(context).getString(PREFERENCE_SELECTED_MODE, null);
        if (storedPage == null) {
            return null;
        }
        try {
            return MainPage.valueOf(storedPage);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public void setAmplifierVolume(int volumePercent) {
        int volume = clampVolume(volumePercent);
        synchronized (mLock) {
            mAmplifierVolumePercent = volume;
            if (volume > 0) {
                mLastAmplifierVolumePercent = volume;
            }
            mAmplifierMuted = false;
        }
        dispatchAmplifierVolumeChanged();
        sendVolumeCommand(SERIAL_PORT_CMD_VOLUME_KEY, SERIAL_PORT_CMD_VOLUME, volume);
    }

    public void adjustAmplifierVolume(int direction) {
        int currentVolume;
        synchronized (mLock) {
            currentVolume = mAmplifierVolumePercent;
        }
        if (direction > 0) {
            setAmplifierVolume(currentVolume + 1);
        } else if (direction < 0) {
            setAmplifierVolume(currentVolume - 1);
        }
    }

    public void toggleAmplifierMute() {
        int commandVolume;
        synchronized (mLock) {
            if (mAmplifierMuted) {
                mAmplifierMuted = false;
                mAmplifierVolumePercent = mLastAmplifierVolumePercent > 0
                        ? mLastAmplifierVolumePercent : DEFAULT_AMPLIFIER_VOLUME;
                commandVolume = mAmplifierVolumePercent;
            } else {
                if (mAmplifierVolumePercent > 0) {
                    mLastAmplifierVolumePercent = mAmplifierVolumePercent;
                }
                mAmplifierMuted = true;
                commandVolume = 0;
            }
        }
        dispatchAmplifierVolumeChanged();
        sendVolumeCommand(SERIAL_PORT_CMD_VOLUME_KEY, SERIAL_PORT_CMD_VOLUME, commandVolume);
    }

    public int getAmplifierVolumePercent() {
        synchronized (mLock) {
            return mAmplifierVolumePercent;
        }
    }

    public boolean isAmplifierMuted() {
        synchronized (mLock) {
            return mAmplifierMuted;
        }
    }

    public void addAmplifierVolumeListener(AmplifierVolumeListener listener) {
        if (listener == null) {
            return;
        }
        mAmplifierVolumeListeners.addIfAbsent(listener);
        dispatchAmplifierVolumeChanged(listener);
    }

    public void removeAmplifierVolumeListener(AmplifierVolumeListener listener) {
        if (listener != null) {
            mAmplifierVolumeListeners.remove(listener);
        }
    }

    public void setMicrophoneVolume(int volumePercent) {
        sendVolumeCommand(SERIAL_PORT_MIC_VOLUME_KEY, SERIAL_PORT_MIC_VOLUME, volumePercent);
    }

    public void setEffectVolume(int volumePercent) {
        sendVolumeCommand(SERIAL_PORT_CMD_SFX_KEY, SERIAL_PORT_CMD_SFX, volumePercent);
    }

    public int getStoredVolume(String key, int defaultValue) {
        return getStoredVolume(getContext(), key, defaultValue);
    }

    public int getStoredVolume(Context context, String key, int defaultValue) {
        if (context == null) {
            return defaultValue;
        }
        String command = getPreferences(context).getString(key, "");
        int valueStart = command.lastIndexOf('+') + 1;
        if (valueStart <= 0 || valueStart >= command.length()) {
            return defaultValue;
        }
        try {
            return clampVolume(Integer.parseInt(command.substring(valueStart).trim()));
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private void sendCommand(MainPage page, String command) {
        if (!openSerialPortIfNeeded()) {
            Log.w(TAG, "Skip command because serial port is unavailable: " + formatCommand(command));
            return;
        }
        Log.i(TAG, "Sending serial command: " + formatCommand(command));
        boolean sent = AudioNativeManager.instance().getSerialPortManager().sendBytes(
                command.getBytes());
        if (sent) {
            Log.i(TAG, "sendCmd: sendBytes=true, content=" + formatCommand(command));
            persistSelectedMode(page);
        } else {
            Log.w(TAG, "sendCmd: sendBytes=false, content=" + formatCommand(command));
        }
    }

    private void sendVolumeCommand(String key, String commandTemplate, int volumePercent) {
        if (getContext() == null) {
            Log.w(TAG, "Ignore volume command before serial port initialization: " + key);
            return;
        }
        int volume = clampVolume(volumePercent);
        String command = commandTemplate.replace("@param", String.valueOf(volume));
        Log.i(TAG, "Selected volume command=" + key + ", command=" + formatCommand(command));
        mSerialExecutor.execute(() -> sendCommand(key, command));
    }

    private void sendCommand(String key, String command) {
        if (!openSerialPortIfNeeded()) {
            Log.w(TAG, "Skip command because serial port is unavailable: " + formatCommand(command));
            return;
        }
        boolean sent = AudioNativeManager.instance().getSerialPortManager().sendBytes(
                command.getBytes());
        if (sent) {
            Log.i(TAG, "sendCmd: sendBytes=true, content=" + formatCommand(command));
            persistCommand(key, command);
        } else {
            Log.w(TAG, "sendCmd: sendBytes=false, content=" + formatCommand(command));
        }
    }

    private boolean openSerialPortIfNeeded() {
        if (mSerialPortOpened) {
            return true;
        }
        try {
            SerialPortManager serialPortManager = AudioNativeManager.instance()
                    .getSerialPortManager();
            File device = findSerialPort();
            if (device == null) {
                Log.w(TAG, "Serial port " + SERIAL_PORT_NAME + " was not found");
                return false;
            }
            Log.i(TAG, "Opening serial port " + device.getPath()
                    + " at " + SERIAL_PORT_BAUD_RATE + " baud");
            serialPortManager.setOnOpenSerialPortListener(new OnOpenSerialPortListener() {
                @Override
                public void onSuccess(File openedDevice) {
                    Log.i(TAG, "Serial port opened: " + openedDevice.getPath());
                }

                @Override
                public void onFail(File failedDevice, Status status) {
                    Log.e(TAG, "Unable to open serial port " + failedDevice + ": " + status);
                }
            });
            serialPortManager.setOnSerialPortDataListener(new OnSerialPortDataListener() {
                @Override
                public void onDataReceived(String data) {
                    Log.d(TAG, "Serial data received: " + data);
                }

                @Override
                public void onDataSent(byte[] data) {
                    Log.i(TAG, "Serial command sent: "
                            + formatCommand(new String(data, StandardCharsets.UTF_8)));
                }
            });
            mSerialPortOpened = serialPortManager.openSerialPort(device, SERIAL_PORT_BAUD_RATE);
            return mSerialPortOpened;
        } catch (Throwable throwable) {
            Log.e(TAG, "Unable to initialize serial port", throwable);
            return false;
        }
    }

    private File findSerialPort() {
        ArrayList<SerialDevice> devices = AudioNativeManager.instance().getSerialPortFinder()
                .getDevices();
        for (SerialDevice device : devices) {
            if (SERIAL_PORT_NAME.equals(device.getName())) {
                return device.getFile();
            }
        }
        File fallback = new File("/dev/" + SERIAL_PORT_NAME);
        return fallback.exists() ? fallback : null;
    }

    private Context getContext() {
        synchronized (mLock) {
            return mContext;
        }
    }

    private SharedPreferences getPreferences(Context context) {
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    private void persistSelectedMode(MainPage page) {
        Context context = getContext();
        if (context == null) {
            return;
        }
        getPreferences(context).edit()
                .putString(PREFERENCE_SELECTED_MODE, page.name())
                .apply();
        Log.i(TAG, "Persisted input mode=" + page);
    }

    private void persistCommand(String key, String command) {
        Context context = getContext();
        if (context == null || key.isEmpty()) {
            return;
        }
        getPreferences(context).edit().putString(key, command).apply();
        Log.i(TAG, "Persisted serial command key=" + key);
    }

    private int clampVolume(int volumePercent) {
        return Math.max(0, Math.min(100, volumePercent));
    }

    private void dispatchAmplifierVolumeChanged() {
        for (AmplifierVolumeListener listener : mAmplifierVolumeListeners) {
            dispatchAmplifierVolumeChanged(listener);
        }
    }

    private void dispatchAmplifierVolumeChanged(AmplifierVolumeListener listener) {
        int volumePercent = getAmplifierVolumePercent();
        boolean muted = isAmplifierMuted();
        mMainHandler.post(() -> {
            if (mAmplifierVolumeListeners.contains(listener)) {
                listener.onAmplifierVolumeChanged(volumePercent, muted);
            }
        });
    }

    private String formatCommand(String command) {
        return command.replace("\r", "\\r").replace("\n", "\\n");
    }

    private String getCommand(MainPage page) {
        if (page == null) {
            return null;
        }
        switch (page) {
            case HDMI:
                return SERIAL_PORT_CMD_MUSIC_SELECT_HDMI;
            case OPTICAL:
                return SERIAL_PORT_CMD_MUSIC_SELECT_AUX;
            case BLUETOOTH:
                return SERIAL_PORT_CMD_MUSIC_SELECT_BT;
            case WIFI:
                return SERIAL_PORT_CMD_MUSIC_SELECT_WIFI;
            case COAX:
                return SERIAL_PORT_CMD_MUSIC_SELECT_COAX;
            case LINE:
                return SERIAL_PORT_CMD_MUSIC_SELECT_LINE;
            case MICROPHONE:
                return SERIAL_PORT_CMD_MUSIC_SELECT_KARAOK_MICON;
            default:
                return null;
        }
    }
}
