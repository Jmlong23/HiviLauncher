package com.ljm.audiotoollib.upnpserver.service;

import android.content.Context;
import android.media.AudioManager;
import android.util.Log;

import com.ljm.audiotoollib.upnpserver.cling.model.types.UnsignedIntegerFourBytes;
import com.ljm.audiotoollib.upnpserver.cling.model.types.UnsignedIntegerTwoBytes;
import com.ljm.audiotoollib.upnpserver.entity.PlayMusicListType;
import com.ljm.audiotoollib.upnpserver.entity.TrackINFO_Type;
import com.ljm.audiotoollib.upnpserver.listener.OnPlayMusicByIndexListener;
import com.ljm.audiotoollib.upnpserver.utils.XmlUtil;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 *
 */
public final class PlayQueueController implements IRendererInterface.IPlayQueueControl {

    private static final String TAG = "PlayQueueController";
    private static final boolean ENABLE_APP_PUSH_FORMAT_LOG = false;
    private static final int LOG_CHUNK_SIZE = 3000;
    private static final UnsignedIntegerTwoBytes VOLUME_MUTE = new UnsignedIntegerTwoBytes(0);
    private final UnsignedIntegerFourBytes mInstanceId;
    private final AudioManager mAudioManager;
    private UnsignedIntegerTwoBytes mLastVolume;
    private UnsignedIntegerTwoBytes mCurrentVolume;
    private PlayMusicListType playMusicList;
    private OnPlayMusicByIndexListener playMusicByIndexListener;
    private static PlayQueueController instance;
    private static Context mContext;
    private UnsignedIntegerFourBytes[] pqControlUnsignedIntegerFourBytes = null;
    private final static Map<UnsignedIntegerFourBytes, IRendererInterface.IPlayQueueControl> avControlMap = new LinkedHashMap<>();

    private String queueContext;

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

    public PlayQueueController(Context context) {
        this(context, new UnsignedIntegerFourBytes(0));
    }

    public PlayQueueController(Context context, UnsignedIntegerFourBytes instanceId) {
        mInstanceId = instanceId;
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        final int MAX_MUSIC = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        final int volume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        mCurrentVolume = new UnsignedIntegerTwoBytes(volume * 100 / MAX_MUSIC);
        mLastVolume = new UnsignedIntegerTwoBytes(volume * 100 / MAX_MUSIC);
        playMusicList = new PlayMusicListType();
    }


    @Override
    public synchronized void createQueue(String queueContext) {
        Log.i(TAG, "createQueue queueContextLen=" + (queueContext == null ? 0 : queueContext.length()));
        logLongString("APP_PUSH_FORMAT createQueue queueContext", queueContext);
        QueueParseResult parseResult = parseQueueContext(queueContext);
        String listName = parseResult.listName;
        playMusicList.clearPQMusicList();
        playMusicList.setCurPlayIndex(0);
        playMusicList.setCurPlayListName(listName);
        int trackNum = parseResult.tracks.size();
        Log.i(TAG, "list len: " + trackNum);
        playMusicList.getPQMusicList().addAll(parseResult.tracks);
    }

    @Override
    public synchronized void AppendTracksInQueue(String queueContext) {
        this.queueContext = queueContext;
        Log.i(TAG, "AppendTracksInQueue queueContextLen=" + (this.queueContext == null ? 0 : this.queueContext.length()));
        logLongString("APP_PUSH_FORMAT AppendTracksInQueue queueContext", this.queueContext);
        QueueParseResult parseResult = parseQueueContext(queueContext);
        String listName = parseResult.listName;
        playMusicList.setCurPlayListName(listName);
        int trackNum = parseResult.tracks.size();
        Log.i(TAG, "list len: " + trackNum);
        playMusicList.getPQMusicList().addAll(parseResult.tracks);
    }

    @Override
    public synchronized void PlayQueueWithIndex(String queueName, int i) {
        Log.e(TAG, "PlayQueueWithIndex queueName: " + queueName + " index: " + i);

        int trackCount = playMusicList.getPQMusicList().size();
        if (trackCount == 0) {
            Log.w(TAG, "ignore PlayQueueWithIndex for an empty queue");
            return;
        }
        int index = Math.max(0, Math.min(i - 1, trackCount - 1));
        playMusicList.setCurPlayIndex(index);
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
    public synchronized void removeTracksInQueue(String queueName, int start, int end) {
        int index = start - 1;
        int trackCount = playMusicList.getPQMusicList().size();
        if (index < 0 || index >= trackCount) {
            Log.w(TAG, "ignore RemoveTracksInQueue with invalid start: " + start);
            return;
        }
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

    public synchronized TrackINFO_Type getCurrentTrack() {
        List<TrackINFO_Type> tracks = playMusicList.getPQMusicList();
        if (tracks.isEmpty()) {
            return null;
        }
        int index = Math.max(0, Math.min(playMusicList.getCurPlayIndex(), tracks.size() - 1));
        if (index != playMusicList.getCurPlayIndex()) {
            playMusicList.setCurPlayIndex(index);
        }
        return tracks.get(index);
    }

    public synchronized int getCurrentTrackIndex() {
        TrackINFO_Type track = getCurrentTrack();
        return track == null ? -1 : playMusicList.getCurPlayIndex();
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
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(queueContext.trim())));

            result.listName = getNodeValue(doc.getElementsByTagName("listName"), 0);
            int trackNum = safeParseInt(getNodeValue(doc.getElementsByTagName("TrackNumber"), 0));

            NodeList urlNodes = doc.getElementsByTagName("URL");
            NodeList metadataNodes = doc.getElementsByTagName("Metadata");
            NodeList idNodes = doc.getElementsByTagName("Id");
            NodeList keyNodes = doc.getElementsByTagName("Key");

            int nodeBasedCount = Math.max(Math.max(urlNodes.getLength(), metadataNodes.getLength()),
                    Math.max(idNodes.getLength(), keyNodes.getLength()));
            int count = trackNum > 0 ? Math.min(trackNum, nodeBasedCount) : nodeBasedCount;
            if (ENABLE_APP_PUSH_FORMAT_LOG) {
                Log.i(TAG, "APP_PUSH_FORMAT parsed listName=" + result.listName
                        + ", declaredTrackNumber=" + trackNum
                        + ", urlNodes=" + urlNodes.getLength()
                        + ", metadataNodes=" + metadataNodes.getLength()
                        + ", idNodes=" + idNodes.getLength()
                        + ", keyNodes=" + keyNodes.getLength()
                        + ", parseCount=" + count);
            }

            for (int i = 0; i < count; i++) {
                TrackINFO_Type track = new TrackINFO_Type();
                String url = getNodeValue(urlNodes, i);
                String metadata = getNodeValue(metadataNodes, i);
                String id = getNodeValue(idNodes, i);
                String key = getNodeValue(keyNodes, i);
                if (ENABLE_APP_PUSH_FORMAT_LOG) {
                    Log.i(TAG, "APP_PUSH_FORMAT track[" + i + "] id=" + id
                            + ", key=" + key
                            + ", urlLen=" + (url == null ? 0 : url.length())
                            + ", metadataLen=" + (metadata == null ? 0 : metadata.length()));
                    logLongString("APP_PUSH_FORMAT track[" + i + "] URL", url);
                    logLongString("APP_PUSH_FORMAT track[" + i + "] Metadata", metadata);
                }
                track.setURL(url);
                track.setMetaData(metadata);
                track.setId(id);
                track.setKey(key);
                result.tracks.add(track);
            }
        } catch (Exception e) {
            Log.e(TAG, "parseQueueContext failed", e);
        }
        return result;
    }

    private static String getNodeValue(NodeList nodes, int index) {
        if (nodes == null || index < 0 || index >= nodes.getLength()) {
            return "";
        }
        Node node = nodes.item(index);
        if (node == null) {
            return "";
        }
        Node firstChild = node.getFirstChild();
        return firstChild == null ? "" : firstChild.getNodeValue();
    }

    private static int safeParseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignore) {
            return 0;
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
}
