package com.ljm.audiotoollib.upnpserver.service;

import android.content.Context;
import android.util.Log;
import android.util.Xml;

import com.ljm.audiotoollib.upnpserver.cling.model.types.UnsignedIntegerFourBytes;
import com.ljm.audiotoollib.upnpserver.entity.PlayMusicListType;
import com.ljm.audiotoollib.upnpserver.entity.TrackINFO_Type;
import com.ljm.audiotoollib.upnpserver.listener.OnPlayMusicByIndexListener;
import com.ljm.audiotoollib.upnpserver.utils.XmlUtil;

import org.xmlpull.v1.XmlPullParser;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 *
 */
public final class PlayQueueController implements IRendererInterface.IPlayQueueControl {

    private static final String TAG = "PlayQueueController";
    private static final boolean ENABLE_APP_PUSH_FORMAT_LOG = false;
    private static final int LOG_CHUNK_SIZE = 3000;
    private final UnsignedIntegerFourBytes mInstanceId;
    private PlayMusicListType playMusicList;
    private OnPlayMusicByIndexListener playMusicByIndexListener;
    private static PlayQueueController instance;
    private static Context mContext;
    private UnsignedIntegerFourBytes[] pqControlUnsignedIntegerFourBytes = null;
    private final static Map<UnsignedIntegerFourBytes, IRendererInterface.IPlayQueueControl> avControlMap = new LinkedHashMap<>();

    private final Object queueLock = new Object();
    private int lastAppendQueueHash;
    private int lastAppendQueueLength;
    private long lastAppendQueueAtMs;

    public static PlayQueueController getInstance(Context context) {
        if (instance == null) {
            mContext = context;
            instance = new PlayQueueController(context);
            addControl(instance);
        }
        return instance;
    }

    public static void addControl(IRendererInterface.IControl control) {
        avControlMap.put(control.getInstanceId(), (IRendererInterface.IPlayQueueControl) control);
    }

    public static PlayQueueController getInstance() {
        return getInstance(mContext);
    }


    public PlayMusicListType getPlayMusicList() {
        return playMusicList;
    }

    /**
     * Gets the queue item and index from the same queue snapshot. Queue payloads are received on
     * Cling worker threads while AVTransport status is queried concurrently, so callers must not
     * compose these values through separate list/index reads.
     */
    public CurrentTrackSnapshot getCurrentTrackSnapshot() {
        synchronized (queueLock) {
            List<TrackINFO_Type> tracks = playMusicList.getPQMusicList();
            int index = playMusicList.getCurPlayIndex();
            if (index < 0 || index >= tracks.size()) {
                return CurrentTrackSnapshot.empty();
            }
            return new CurrentTrackSnapshot(index, tracks.get(index));
        }
    }

    public PlayQueueController(Context context) {
        this(context, new UnsignedIntegerFourBytes(0));
    }

    public PlayQueueController(Context context, UnsignedIntegerFourBytes instanceId) {
        mInstanceId = instanceId;
        playMusicList = new PlayMusicListType();
    }


    @Override
    public void createQueue(String queueContext) {
        Log.i(TAG, "createQueue " + summarizeQueueContext(queueContext));
        logLongString("APP_PUSH_FORMAT createQueue queueContext", queueContext);
        QueueParseResult parseResult = parseQueueContext(queueContext);
        synchronized (queueLock) {
            playMusicList.setCurPlayListName(parseResult.listName);
            playMusicList.replacePQMusicList(parseResult.tracks);
            normalizeCurrentIndexLocked();
            Log.i(TAG, "createQueue committed listName=" + parseResult.listName
                    + ", tracks=" + parseResult.tracks.size()
                    + ", currentIndex=" + playMusicList.getCurPlayIndex());
        }
    }

    @Override
    public void AppendTracksInQueue(String queueContext) {
        String queueSummary = summarizeQueueContext(queueContext);
        Log.i(TAG, "AppendTracksInQueue " + queueSummary);
        if (isDuplicateAppend(queueContext)) {
            Log.i(TAG, "AppendTracksInQueue ignored duplicate payload");
            return;
        }
        logLongString("APP_PUSH_FORMAT AppendTracksInQueue queueContext", queueContext);
        QueueParseResult parseResult = parseQueueContext(queueContext);
        synchronized (queueLock) {
            List<TrackINFO_Type> updatedTracks = new ArrayList<>(playMusicList.getPQMusicList());
            updatedTracks.addAll(parseResult.tracks);
            if (!parseResult.listName.isEmpty()) {
                playMusicList.setCurPlayListName(parseResult.listName);
            }
            playMusicList.replacePQMusicList(updatedTracks);
            normalizeCurrentIndexLocked();
            Log.i(TAG, "AppendTracksInQueue committed appended=" + parseResult.tracks.size()
                    + ", total=" + updatedTracks.size()
                    + ", currentIndex=" + playMusicList.getCurPlayIndex());
        }
    }

    @Override
    public void PlayQueueWithIndex(String queueName, int i) {
        Log.e(TAG, "PlayQueueWithIndex queueName: " + queueName + " index: " + i);

        synchronized (queueLock) {
            int size = playMusicList.getPQMusicList().size();
            if (size == 0) {
                Log.w(TAG, "PlayQueueWithIndex ignored empty queueName=" + queueName);
                return;
            }
            int normalizedIndex = Math.max(0, Math.min(i, size - 1));
            if (normalizedIndex != i) {
                Log.w(TAG, "PlayQueueWithIndex normalized index=" + i + " to "
                        + normalizedIndex + ", size=" + size);
            }
            playMusicList.setCurPlayIndex(normalizedIndex);
        }
        if (playMusicByIndexListener != null) {
            playMusicByIndexListener.onPlay(playMusicList);
        }

    }

    @Override
    public void setQueueLoopMode(int loopMode) {
        if (playMusicByIndexListener != null) {
            playMusicByIndexListener.setLoopMode(loopMode);
        }
    }

    @Override
    public int getQueueLoopMode() {
        if (playMusicByIndexListener != null) {
            return playMusicByIndexListener.getLoopMode();
        }
        return 0;
    }

    @Override
    public String browseQueue(String queueName) {
        return XmlUtil.createXmlString(playMusicList);
    }

    @Override
    public void removeTracksInQueue(String queueName, int start, int end) {
        int index = start - 1;
        playMusicList.removeListByIndex(index);
        if(playMusicList.getCurPlayIndex() == index && playMusicByIndexListener != null) {
            playMusicByIndexListener.onPlay(playMusicList);
            return;
        }
        if(playMusicList.getCurPlayIndex() > index) playMusicList.setCurPlayIndex(playMusicList.getCurPlayIndex() - 1);
    }

    public void setOnPlayMusicByIndexListener(OnPlayMusicByIndexListener listener) {
        playMusicByIndexListener = listener;
    }


    public UnsignedIntegerFourBytes getInstanceId() {
        return mInstanceId;
    }

    public UnsignedIntegerFourBytes[] getPqTransportCurrentInstanceIds() {
        if (pqControlUnsignedIntegerFourBytes == null) {
            pqControlUnsignedIntegerFourBytes = new UnsignedIntegerFourBytes[avControlMap.size()];
            pqControlUnsignedIntegerFourBytes = avControlMap.keySet().toArray(new UnsignedIntegerFourBytes[0]);
        }
        return pqControlUnsignedIntegerFourBytes;
    }

    private QueueParseResult parseQueueContext(String queueContext) {
        QueueParseResult result = new QueueParseResult();
        if (queueContext == null || queueContext.trim().isEmpty()) {
            return result;
        }
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(new StringReader(queueContext));
            TrackINFO_Type currentTrack = null;
            int declaredTrackNumber = 0;

            for (int eventType = parser.getEventType();
                 eventType != XmlPullParser.END_DOCUMENT;
                 eventType = parser.next()) {
                if (eventType == XmlPullParser.START_TAG) {
                    String name = parser.getName();
                    if (parser.getDepth() == 3 && isTrackElement(name)) {
                        currentTrack = new TrackINFO_Type();
                    } else if (currentTrack == null && "ListName".equalsIgnoreCase(name)) {
                        result.listName = readText(parser);
                    } else if (currentTrack == null && "TrackNumber".equalsIgnoreCase(name)) {
                        declaredTrackNumber = safeParseInt(readText(parser));
                    } else if (currentTrack != null) {
                        if ("URL".equalsIgnoreCase(name)) {
                            currentTrack.setURL(readText(parser));
                        } else if ("Metadata".equalsIgnoreCase(name)) {
                            currentTrack.setMetaData(readText(parser));
                        } else if ("Id".equalsIgnoreCase(name)) {
                            currentTrack.setId(readText(parser));
                        } else if ("Key".equalsIgnoreCase(name)) {
                            currentTrack.setKey(readText(parser));
                        } else if ("Source".equalsIgnoreCase(name)) {
                            currentTrack.setSource(readText(parser));
                        }
                    }
                } else if (eventType == XmlPullParser.END_TAG
                        && currentTrack != null
                        && parser.getDepth() == 3
                        && isTrackElement(parser.getName())) {
                    result.tracks.add(currentTrack);
                    currentTrack = null;
                }
            }
            if (declaredTrackNumber > 0 && result.tracks.size() != declaredTrackNumber) {
                Log.w(TAG, "queue track count mismatch declared=" + declaredTrackNumber
                        + ", parsed=" + result.tracks.size());
            }
            if (ENABLE_APP_PUSH_FORMAT_LOG) {
                Log.i(TAG, "APP_PUSH_FORMAT parsed listName=" + result.listName
                        + ", declaredTrackNumber=" + declaredTrackNumber
                        + ", parsedTracks=" + result.tracks.size());
            }
        } catch (Exception e) {
            Log.e(TAG, "parseQueueContext failed", e);
        }
        return result;
    }

    private void normalizeCurrentIndexLocked() {
        int size = playMusicList.getPQMusicList().size();
        int index = playMusicList.getCurPlayIndex();
        if (size == 0) {
            playMusicList.setCurPlayIndex(0);
        } else if (index < 0 || index >= size) {
            playMusicList.setCurPlayIndex(0);
        }
    }

    private static boolean isTrackElement(String name) {
        if (name == null || name.length() <= 5 || !name.startsWith("Track")) {
            return false;
        }
        for (int i = 5; i < name.length(); i++) {
            if (!Character.isDigit(name.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static String readText(XmlPullParser parser) throws Exception {
        String value = parser.nextText();
        return value == null ? "" : value;
    }

    private static int safeParseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignore) {
            return 0;
        }
    }

    private static String summarizeQueueContext(String queueContext) {
        if (queueContext == null) {
            return "queueContext=null";
        }
        return "queueContextLen=" + queueContext.length()
                + ", hash=" + Integer.toHexString(queueContext.hashCode());
    }

    private boolean isDuplicateAppend(String queueContext) {
        if (queueContext == null) {
            return false;
        }
        int hash = queueContext.hashCode();
        int length = queueContext.length();
        long now = android.os.SystemClock.uptimeMillis();
        synchronized (queueLock) {
            boolean duplicate = hash == lastAppendQueueHash
                    && length == lastAppendQueueLength
                    && now - lastAppendQueueAtMs < 2000L;
            lastAppendQueueHash = hash;
            lastAppendQueueLength = length;
            lastAppendQueueAtMs = now;
            return duplicate;
        }
    }

    private static void logLongString(String label, String value) {
        if (!ENABLE_APP_PUSH_FORMAT_LOG) {
            return;
        }
        if (value == null) {
            Log.i(TAG, label + "=null");
            return;
        }
        value = sanitizeForLog(value);
        int length = value.length();
        if (length == 0) {
            Log.i(TAG, label + " is empty");
            return;
        }
        int partCount = (length + LOG_CHUNK_SIZE - 1) / LOG_CHUNK_SIZE;
        for (int start = 0, part = 1; start < length; start += LOG_CHUNK_SIZE, part++) {
            int end = Math.min(length, start + LOG_CHUNK_SIZE);
            Log.i(TAG, label + " part " + part + "/" + partCount + ": " + value.substring(start, end));
        }
    }

    private static String sanitizeForLog(String value) {
        return value
                .replaceAll("(?i)(\"authorization\"\\s*:\\s*\")[^\"]*(\")", "$1***$2")
                .replaceAll("(?i)(&quot;authorization&quot;\\s*:\\s*&quot;).*?(&quot;)", "$1***$2")
                .replaceAll("(?i)(&amp;quot;authorization&amp;quot;\\s*:\\s*&amp;quot;).*?(&amp;quot;)", "$1***$2");
    }

    private static final class QueueParseResult {
        String listName = "";
        List<TrackINFO_Type> tracks = new ArrayList<>();
    }

    public static final class CurrentTrackSnapshot {
        private static final CurrentTrackSnapshot EMPTY = new CurrentTrackSnapshot(-1, null);
        private final int index;
        private final TrackINFO_Type track;

        private CurrentTrackSnapshot(int index, TrackINFO_Type track) {
            this.index = index;
            this.track = track;
        }

        private static CurrentTrackSnapshot empty() {
            return EMPTY;
        }

        public int getIndex() {
            return index;
        }

        public TrackINFO_Type getTrack() {
            return track;
        }

        public boolean isValid() {
            return track != null;
        }
    }
}
