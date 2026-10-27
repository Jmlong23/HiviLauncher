package com.hivi.launcher.bluetooth.model;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.hivi.launcher.audio.AudioRouteController;

import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Authoritative Bluetooth state keeper backed by the MCU Bluetooth module.
 *
 * <p>The phone connects to the speaker through the MCU, so every connection transition,
 * device name, and playback state arrives as a serial report. The launcher only sends
 * requests (disconnect/connect/clear/transport) and mirrors what the MCU confirms, the
 * same way the Hivi-Audio launcher does. At startup no state is cached: the phone that
 * was connected before a reboot is gone, so the real state is queried from the MCU,
 * with brief retries in case it cannot answer right away.</p>
 */
public final class McuBluetoothController implements AudioRouteController.McuBluetoothListener {
    private static final int CONNECTION_STATE_QUERY_RETRY_COUNT = 5;
    private static final long CONNECTION_STATE_QUERY_RETRY_INTERVAL_MS = 2000L;

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
    private String mTrackTitle = "";
    private String mTrackLyric = "";
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    // Accessed on the main thread only: reports arrive via the dispatch main handler and
    // start()/queries are issued from UI code.
    private boolean mReceivedConnectionStateReport;
    private int mRemainingConnectionStateQueries;

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
        // The MCU is the source of truth: a phone connected before a reboot is gone, so
        // start disconnected and let the connection-state query report the real state.
        // The query is retried briefly because the MCU may not answer right at boot.
        mRemainingConnectionStateQueries = CONNECTION_STATE_QUERY_RETRY_COUNT;
        queryConnectionStateUntilReported();
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

    /** Current track's song title as parsed from the MCU now-playing reports, or empty. */
    public synchronized String getTrackTitle() {
        return mTrackTitle;
    }

    /** Latest lyrics line the MCU pushed for the current track, or empty. */
    public synchronized String getTrackLyric() {
        return mTrackLyric;
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
        mReceivedConnectionStateReport = true;
        boolean queryPhoneName;
        synchronized (this) {
            mConnected = connected;
            queryPhoneName = connected;
            // Reset so a different phone reconnecting cannot briefly show the old name.
            mPhoneName = "";
            if (!connected) {
                mPlaying = false;
                clearTrackInfoLocked();
            }
        }
        if (queryPhoneName) {
            // The connection report carries no device name; ask the MCU for it so the
            // home bar and the Bluetooth page can show which phone just connected.
            mAudioRouteController.queryBluetoothPhoneName();
        }
        notifyStateChanged();
    }

    @Override
    public void onBluetoothPairingsCleared() {
        synchronized (this) {
            mConnected = false;
            mPlaying = false;
            mPhoneName = "";
            clearTrackInfoLocked();
        }
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

    @Override
    public void onBluetoothTrackInfoChanged(
            AudioRouteController.McuBluetoothListener.BluetoothTrackField field, String value) {
        boolean changed;
        boolean stateFixed = false;
        synchronized (this) {
            if (!mConnected) {
                // Metadata only arrives while a phone streams; self-heal a missed
                // connection report, same as the HiviAudio project does.
                mConnected = true;
                stateFixed = true;
            }
            if (!mPlaying) {
                // A metadata push implies audible playback.
                mPlaying = true;
                stateFixed = true;
            }
            switch (field) {
                case ARTIST:
                    // ARTIST arrives as "song title-artists"; some updates carry only the
                    // artist name, in which case the current song title is preserved.
                    int separator = findArtistSplitIndex(value);
                    if (separator > 0 && separator < value.length() - 1) {
                        String title = value.substring(0, separator).trim();
                        changed = !title.equals(mTrackTitle);
                        mTrackTitle = title;
                        // A new track started; stale lyrics from the previous one must go.
                        mTrackLyric = "";
                    } else {
                        changed = false;
                    }
                    break;
                case ALBUM:
                    // Parsed for completeness; the album name is not displayed.
                    changed = false;
                    break;
                case LYRICS:
                    // The first line of a track's lyrics can be the "title-singer" pair.
                    if (isLikelyTitleLyric(value)) {
                        int splitIndex = findArtistSplitIndex(value);
                        String title = value.substring(0, splitIndex).trim();
                        if (!title.isEmpty() && !title.equals(mTrackTitle)) {
                            mTrackTitle = title;
                            changed = true;
                        } else {
                            changed = false;
                        }
                    } else {
                        changed = false;
                    }
                    if (!value.equals(mTrackLyric)) {
                        mTrackLyric = value;
                        changed = true;
                    }
                    break;
                default:
                    changed = false;
                    break;
            }
        }
        if (changed || stateFixed) {
            notifyStateChanged();
        }
    }

    /**
     * Sends the connection-state query until the MCU answers once or the retries run out;
     * the first report (connected or not) stops the retries.
     */
    private void queryConnectionStateUntilReported() {
        if (!mStarted || mReceivedConnectionStateReport
                || mRemainingConnectionStateQueries <= 0) {
            return;
        }
        mRemainingConnectionStateQueries--;
        mAudioRouteController.queryBluetoothConnectionState();
        mMainHandler.postDelayed(this::queryConnectionStateUntilReported,
                CONNECTION_STATE_QUERY_RETRY_INTERVAL_MS);
    }

    private void clearTrackInfoLocked() {
        mTrackTitle = "";
        mTrackLyric = "";
    }

    /** Song/singer separator: '-' plus the full-width and en/em dash variants. */
    private static int findArtistSplitIndex(String payload) {
        int splitIndex = payload.lastIndexOf('-');
        if (splitIndex < 0) {
            splitIndex = payload.lastIndexOf('－');
        }
        if (splitIndex < 0) {
            splitIndex = payload.lastIndexOf('–');
        }
        if (splitIndex < 0) {
            splitIndex = payload.lastIndexOf('—');
        }
        return splitIndex;
    }

    /** A lyrics line counts as the "title-singer" pair when both sides look like names. */
    private static boolean isLikelyTitleLyric(String lyricPayload) {
        int splitIndex = findArtistSplitIndex(lyricPayload);
        if (splitIndex <= 0 || splitIndex >= lyricPayload.length() - 1) {
            return false;
        }
        String leftPart = lyricPayload.substring(0, splitIndex).trim();
        String rightPart = lyricPayload.substring(splitIndex + 1).trim();
        if (leftPart.isEmpty() || rightPart.isEmpty()) {
            return false;
        }
        return !looksLikeLyricCredit(leftPart) && !looksLikeLyricCredit(rightPart);
    }

    private static boolean looksLikeLyricCredit(String value) {
        String normalized = value.trim();
        return normalized.startsWith("作词")
                || normalized.startsWith("作曲")
                || normalized.startsWith("编曲")
                || normalized.startsWith("词曲")
                || normalized.startsWith("歌词")
                || normalized.contains("：")
                || normalized.contains(":");
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
}
