package com.hivi.launcher.music.model;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.Html;
import android.text.TextUtils;
import android.util.Log;

import com.ljm.audiotoollib.AudioToolManager;
import com.ljm.audiotoollib.upnpserver.entity.InfoEx;
import com.ljm.audiotoollib.upnpserver.entity.MediaInfo;
import com.ljm.audiotoollib.upnpserver.entity.MusicDataBean;
import com.ljm.audiotoollib.upnpserver.entity.PlayMusicListType;
import com.ljm.audiotoollib.upnpserver.entity.PlayStatusBean;
import com.ljm.audiotoollib.upnpserver.entity.TrackINFO_Type;
import com.ljm.audiotoollib.upnpserver.httpserver.UpnpHttpServer;
import com.ljm.audiotoollib.upnpserver.listener.OnMediaControlListener;
import com.ljm.audiotoollib.upnpserver.listener.OnPlayMusicByIndexListener;
import com.ljm.audiotoollib.upnpserver.listener.OnRenderControlListener;
import com.ljm.audiotoollib.upnpserver.listener.OnUpnpHttpServerListener;
import com.ljm.audiotoollib.upnpserver.listener.OnVideoControlListener;
import com.ljm.audiotoollib.upnpserver.service.PlayQueueController;
import com.ljm.audiotoollib.upnpserver.utils.UpnpMediaTypeUtil;
import com.ljm.audiotoollib.upnpserver.cling.model.ModelUtil;
import com.ljm.audiotoollib.upnpserver.cling.support.model.TransportState;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UpnpPlaybackManager {
    private static final String TAG = "UpnpPlaybackManager";
    private static final String DEVICE_NAME = "Soundbar";
    private static final long PROGRESS_TICK_MS = 1000L;

    private static final UpnpPlaybackManager INSTANCE = new UpnpPlaybackManager();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<>();
    private final Runnable progressTicker = new Runnable() {
        @Override
        public void run() {
            notifyStateChanged();
            mainHandler.postDelayed(this, PROGRESS_TICK_MS);
        }
    };

    private Context appContext;
    private AudioManager audioManager;
    private MediaPlayer mediaPlayer;
    private boolean started;
    private boolean bound;
    private boolean preparing;
    private boolean mediaPrepared;
    private boolean manualPaused;
    private PlayMusicListType playMusicList;
    private int playIndex;
    private String currentUrl = "";
    private String currentMetaData = "";
    private String title = "Sleep Music";
    private String artist = "WiiM";
    private String album = "";
    private String lyric = "Can you give me that Can";
    private String coverUrl = "";
    private long durationFallbackMs;
    private int loopMode;

    public interface Listener {
        void onPlaybackChanged(UpnpPlaybackState state);
    }

    public static UpnpPlaybackManager getInstance() {
        return INSTANCE;
    }

    private UpnpPlaybackManager() {
    }

    public void start(Context context) {
        if (context == null) {
            return;
        }
        appContext = context.getApplicationContext();
        audioManager = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
        ensureMediaPlayer();
        if (started) {
            return;
        }
        started = true;
        try {
            AudioToolManager.instance().getUpnpServerManager().initUpnpServer(
                    appContext, renderServiceConnection, Context.BIND_AUTO_CREATE, DEVICE_NAME);
        } catch (Throwable e) {
            Log.e(TAG, "start UPnP renderer failed", e);
        }
        mainHandler.removeCallbacks(progressTicker);
        mainHandler.post(progressTicker);
    }

    public void addListener(Listener listener) {
        if (listener == null || listeners.contains(listener)) {
            return;
        }
        listeners.add(listener);
        listener.onPlaybackChanged(getCurrentState());
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public UpnpPlaybackState getCurrentState() {
        if (TextUtils.isEmpty(currentUrl)) {
            return UpnpPlaybackState.empty();
        }
        long position = getCurrentPositionMs();
        long duration = getCurrentDurationMs();
        return new UpnpPlaybackState(title, artist, album, getCurrentLyricLine(position), coverUrl,
                position, duration, isPlaying(), preparing);
    }

    public void playOrPause() {
        if (isPlaying()) {
            pause();
        } else {
            play();
        }
    }

    public void play() {
        ensureMediaPlayer();
        if (mediaPlayer == null) {
            return;
        }
        try {
            if (TextUtils.isEmpty(currentUrl)) {
                return;
            }
            manualPaused = false;
            if (preparing) {
                return;
            }
            mediaPlayer.start();
            notifyStateChanged();
        } catch (IllegalStateException e) {
            playCurrentTrack();
        }
    }

    public void pause() {
        if (mediaPlayer == null) {
            return;
        }
        try {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
            }
            manualPaused = true;
            notifyStateChanged();
        } catch (IllegalStateException e) {
            Log.w(TAG, "pause failed: " + e.getMessage());
        }
    }

    public void seekTo(long positionMs) {
        if (mediaPlayer == null) {
            return;
        }
        try {
            mediaPlayer.seekTo((int) Math.max(0L, Math.min(positionMs, getCurrentDurationMs())));
            notifyStateChanged();
        } catch (IllegalStateException e) {
            Log.w(TAG, "seek failed: " + e.getMessage());
        }
    }

    public void previous() {
        playOffset(-1);
    }

    public void next() {
        playOffset(1);
    }

    public void stop() {
        if (mediaPlayer == null) {
            return;
        }
        try {
            mediaPlayer.stop();
        } catch (IllegalStateException ignored) {
        }
        mediaPrepared = false;
        preparing = false;
        manualPaused = false;
        notifyStateChanged();
    }

    private final ServiceConnection renderServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            bound = true;
            configureUpnpControllers();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bound = false;
        }
    };

    private void configureUpnpControllers() {
        try {
            PlayQueueController playQueueController = AudioToolManager.instance()
                    .getUpnpServerManager().getPlayQueueManager();
            if (playQueueController != null) {
                playQueueController.setOnPlayMusicByIndexListener(playMusicByIndexListener);
            }
            if (AudioToolManager.instance().getUpnpServerManager().getAudioRenderController() != null) {
                AudioToolManager.instance().getUpnpServerManager().getAudioRenderController()
                        .setListener(renderControlListener);
            }
            AudioToolManager.instance().getUpnpServerManager().getAvTransportController()
                    .setOnVideoControlListener(videoControlListener);
            AudioToolManager.instance().getUpnpServerManager().getAvTransportController()
                    .setOnMediaControllerListener(mediaControlListener);
            UpnpHttpServer.getInstance().setGetPlayerStatusListener(upnpHttpServerListener);
        } catch (Throwable e) {
            Log.e(TAG, "configure UPnP controllers failed", e);
        }
    }

    private final OnPlayMusicByIndexListener playMusicByIndexListener = new OnPlayMusicByIndexListener() {
        @Override
        public void onPlay(PlayMusicListType list) {
            mainHandler.post(() -> {
                playMusicList = list;
                playIndex = list == null ? 0 : list.getCurPlayIndex();
                playCurrentTrack();
            });
        }

        @Override
        public int getLoopMode() {
            return loopMode;
        }

        @Override
        public void setLoopMode(int mode) {
            loopMode = mode;
        }
    };

    private final OnMediaControlListener mediaControlListener = new OnMediaControlListener() {
        @Override
        public void play() {
            mainHandler.post(UpnpPlaybackManager.this::play);
        }

        @Override
        public void pause() {
            mainHandler.post(UpnpPlaybackManager.this::pause);
        }

        @Override
        public void previous() {
            mainHandler.post(UpnpPlaybackManager.this::previous);
        }

        @Override
        public void next() {
            mainHandler.post(UpnpPlaybackManager.this::next);
        }

        @Override
        public void seek(long position) {
            mainHandler.post(() -> seekTo(position));
        }

        @Override
        public void onLyricReceived(String text, String terraceType) {
            mainHandler.post(() -> {
                if (TextUtils.isEmpty(text)) {
                    Log.w(TAG, "received empty remote lyric, terraceType=" + terraceType);
                    return;
                }
                lyric = normalizeLyric(text);
                Log.i(TAG, "received remote lyric, terraceType=" + terraceType + ", "
                        + describeLyric(lyric));
                notifyStateChanged();
            });
        }

        @Override
        public InfoEx getInfoEx() {
            InfoEx infoEx = new InfoEx();
            infoEx.setCurrentTransportState(resolveTransportState());
            infoEx.setRelTime(ModelUtil.toTimeString(getCurrentPositionMs() / 1000L));
            infoEx.setTrackDuration(ModelUtil.toTimeString(getCurrentDurationMs() / 1000L));
            infoEx.setTrackURI(currentUrl);
            infoEx.setTrackMetaData(currentMetaData);
            infoEx.setLoopMode(String.valueOf(loopMode));
            infoEx.setCurrentVolume(String.valueOf(getVolumePercent()));
            return infoEx;
        }
    };

    private final OnVideoControlListener videoControlListener = new OnVideoControlListener() {
        @Override
        public void playVideo() {
            mainHandler.post(UpnpPlaybackManager.this::play);
        }

        @Override
        public void pauseVideo() {
            mainHandler.post(UpnpPlaybackManager.this::pause);
        }

        @Override
        public void previousVideo() {
            mainHandler.post(UpnpPlaybackManager.this::previous);
        }

        @Override
        public void nextVideo() {
            mainHandler.post(UpnpPlaybackManager.this::next);
        }

        @Override
        public void seekVideo(long position) {
            mainHandler.post(() -> seekTo(position));
        }

        @Override
        public long getPositionVideo() {
            return getCurrentPositionMs();
        }

        @Override
        public long getDurationVideo() {
            return getCurrentDurationMs();
        }

        @Override
        public void setAVTransportURI(String uri, String metaData) {
            mainHandler.post(() -> {
                if (TextUtils.isEmpty(uri)) {
                    return;
                }
                boolean audio = true;
                try {
                    audio = UpnpMediaTypeUtil.isAudioMedia(uri, metaData);
                } catch (Throwable ignored) {
                }
                if (!audio) {
                    Log.i(TAG, "ignore non-audio AVTransport URI: " + uri);
                    return;
                }
                TrackINFO_Type track = new TrackINFO_Type();
                track.setURL(uri);
                track.setMetaData(metaData);
                playMusicList = null;
                playIndex = 0;
                applyTrack(track);
                playCurrentUrl();
            });
        }

        @Override
        public void stop() {
            mainHandler.post(UpnpPlaybackManager.this::stop);
        }
    };

    private final OnRenderControlListener renderControlListener = new OnRenderControlListener() {
        @Override
        public int onGetVolume() {
            return getVolumePercent();
        }

        @Override
        public void onSetVolume(int value) {
            setVolumePercent(value);
        }

        @Override
        public String onGetAudioMode() {
            return "WIFI";
        }

        @Override
        public void onSetAudioMode(String mode) {
        }

        @Override
        public void onSetRemoteControlMode(int value) {
        }

        @Override
        public void onSetAudioBackground(String value) {
        }

        @Override
        public void onSelectAudioBackground(String AudioContext) {
        }

        @Override
        public void onCancelAudioBackground() {
        }

        @Override
        public void onSetExtraInfo(String info) {
        }
    };

    private final OnUpnpHttpServerListener upnpHttpServerListener = new OnUpnpHttpServerListener() {
        @Override
        public PlayStatusBean getPlayerStatus() {
            PlayStatusBean statusBean = new PlayStatusBean();
            statusBean.setStatus(isPlaying() ? "play" : (preparing ? "load" : "pause"));
            statusBean.setCurpos(String.valueOf(getCurrentPositionMs()));
            statusBean.setOffset_pts(String.valueOf(getCurrentPositionMs()));
            statusBean.setTotlen(String.valueOf(getCurrentDurationMs()));
            statusBean.setTitle(String.valueOf(title));
            statusBean.setArtist(String.valueOf(artist));
            statusBean.setAlbum("");
            statusBean.setLoop(String.valueOf(loopMode));
            statusBean.setVol(String.valueOf(getVolumePercent()));
            return statusBean;
        }

        @Override
        public void setPlayerCmdPlay(String url) {
            mainHandler.post(() -> {
                if (!TextUtils.isEmpty(url)) {
                    currentUrl = url;
                    playCurrentUrl();
                }
            });
        }

        @Override
        public void setPlayerCmdSlaveVol(int vol) {
            setVolumePercent(vol);
        }

        @Override
        public void restoreToDefault() {
            stop();
        }
    };

    private void playCurrentTrack() {
        TrackINFO_Type track = getCurrentTrack();
        if (track == null) {
            return;
        }
        applyTrack(track);
        playCurrentUrl();
    }

    private TrackINFO_Type getCurrentTrack() {
        if (playMusicList == null || playMusicList.getPQMusicList() == null
                || playMusicList.getPQMusicList().isEmpty()) {
            return null;
        }
        if (playIndex < 0 || playIndex >= playMusicList.getPQMusicList().size()) {
            playIndex = Math.max(0, Math.min(playMusicList.getCurPlayIndex(),
                    playMusicList.getPQMusicList().size() - 1));
        }
        return playMusicList.getPQMusicList().get(playIndex);
    }

    private void applyTrack(TrackINFO_Type track) {
        if (track == null) {
            return;
        }
        currentUrl = firstNonEmpty(track.getURL(), currentUrl);
        currentMetaData = firstNonEmpty(track.getMetaData(), currentMetaData);
        MediaInfo mediaInfo = null;
        MusicDataBean bean = null;
        try {
            mediaInfo = track.getMediaInfo();
            bean = track.getMusicDataBean();
        } catch (Throwable e) {
            Log.w(TAG, "parse track metadata failed: " + e.getMessage());
        }
        title = firstNonEmpty(bean != null ? bean.getName() : "",
                mediaInfo != null ? mediaInfo.getTitle() : "",
                extractXmlTagValue(currentMetaData, "dc:title"),
                extractXmlTagValue(currentMetaData, "title"),
                titleFromUrl(currentUrl),
                "Sleep Music");
        artist = firstNonEmpty(bean != null ? bean.getArtist() : "",
                mediaInfo != null ? mediaInfo.getArtist() : "",
                mediaInfo != null ? mediaInfo.getCreator() : "",
                extractXmlTagValue(currentMetaData, "upnp:artist"),
                extractXmlTagValue(currentMetaData, "dc:creator"),
                "");
        album = firstNonEmpty(bean != null ? bean.getAlg() : "",
                mediaInfo != null ? mediaInfo.getAlbum() : "",
                extractXmlTagValue(currentMetaData, "upnp:album"),
                "");
        coverUrl = firstNonEmpty(bean != null ? bean.getCoverUrl() : "",
                mediaInfo != null ? mediaInfo.getAlbumArtURI() : "",
                extractXmlTagValue(currentMetaData, "upnp:albumArtURI"));
        lyric = "";
        String trackLyric = "";
        String lyricSource = "none";
        if (bean != null && !TextUtils.isEmpty(bean.getMatchLyric())) {
            trackLyric = bean.getMatchLyric();
            lyricSource = "MusicDataBean.matchLyric";
        } else if (!TextUtils.isEmpty(extractXmlTagValue(currentMetaData, "qq:matchLyric"))) {
            trackLyric = extractXmlTagValue(currentMetaData, "qq:matchLyric");
            lyricSource = "qq:matchLyric";
        } else if (!TextUtils.isEmpty(extractXmlTagValue(currentMetaData, "song:matchLyric"))) {
            trackLyric = extractXmlTagValue(currentMetaData, "song:matchLyric");
            lyricSource = "song:matchLyric";
        } else if (!TextUtils.isEmpty(extractXmlTagValue(currentMetaData, "song:lyric"))) {
            trackLyric = extractXmlTagValue(currentMetaData, "song:lyric");
            lyricSource = "song:lyric";
        } else if (!TextUtils.isEmpty(extractXmlTagValue(currentMetaData, "lyric"))) {
            trackLyric = extractXmlTagValue(currentMetaData, "lyric");
            lyricSource = "lyric";
        }
        Log.i(TAG, "track lyric metadata source=" + lyricSource + ", "
                + describeLyric(trackLyric));
        if (!TextUtils.isEmpty(trackLyric)) {
            lyric = normalizeLyric(trackLyric);
        }
        durationFallbackMs = parseDurationMs(firstNonEmpty(
                extractXmlTagValue(currentMetaData, "res@duration"),
                extractXmlTagAttribute(currentMetaData, "duration")));
        notifyStateChanged();
    }

    private void playCurrentUrl() {
        if (TextUtils.isEmpty(currentUrl)) {
            return;
        }
        ensureMediaPlayer();
        if (mediaPlayer == null) {
            return;
        }
        try {
            preparing = true;
            mediaPrepared = false;
            manualPaused = false;
            mediaPlayer.reset();
            applyAudioAttributes(mediaPlayer);
            mediaPlayer.setDataSource(currentUrl);
            mediaPlayer.prepareAsync();
            notifyStateChanged();
        } catch (IOException | IllegalStateException e) {
            preparing = false;
            mediaPrepared = false;
            Log.e(TAG, "play URL failed: " + currentUrl, e);
            notifyStateChanged();
        }
    }

    private void playOffset(int offset) {
        if (playMusicList == null || playMusicList.getPQMusicList() == null
                || playMusicList.getPQMusicList().isEmpty()) {
            return;
        }
        int size = playMusicList.getPQMusicList().size();
        playIndex = (playIndex + offset + size) % size;
        playMusicList.setCurPlayIndex(playIndex);
        playCurrentTrack();
    }

    private void ensureMediaPlayer() {
        if (mediaPlayer != null) {
            return;
        }
        mediaPlayer = new MediaPlayer();
        applyAudioAttributes(mediaPlayer);
        mediaPlayer.setOnPreparedListener(mp -> {
            preparing = false;
            mediaPrepared = true;
            try {
                if (!manualPaused) {
                    mp.start();
                }
            } catch (IllegalStateException e) {
                Log.w(TAG, "start on prepared failed: " + e.getMessage());
            }
            notifyStateChanged();
        });
        mediaPlayer.setOnCompletionListener(mp -> {
            preparing = false;
            if (playMusicList != null && playMusicList.getPQMusicList() != null
                    && playMusicList.getPQMusicList().size() > 1) {
                next();
            } else {
                notifyStateChanged();
            }
        });
        mediaPlayer.setOnErrorListener((mp, what, extra) -> {
            preparing = false;
            mediaPrepared = false;
            Log.e(TAG, "MediaPlayer error what=" + what + ", extra=" + extra);
            notifyStateChanged();
            return true;
        });
    }

    private boolean isPlaying() {
        if (mediaPlayer == null) {
            return false;
        }
        try {
            return mediaPlayer.isPlaying();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private long getCurrentPositionMs() {
        if (mediaPlayer == null || preparing || !mediaPrepared) {
            return 0L;
        }
        try {
            return Math.max(0, mediaPlayer.getCurrentPosition());
        } catch (IllegalStateException e) {
            return 0L;
        }
    }

    private long getCurrentDurationMs() {
        if (mediaPlayer == null || !mediaPrepared) {
            return durationFallbackMs;
        }
        try {
            int duration = mediaPlayer.getDuration();
            return duration > 0 ? duration : durationFallbackMs;
        } catch (IllegalStateException e) {
            return durationFallbackMs;
        }
    }

    private TransportState resolveTransportState() {
        if (preparing) {
            return TransportState.TRANSITIONING;
        }
        if (isPlaying()) {
            return TransportState.PLAYING;
        }
        if (TextUtils.isEmpty(currentUrl)) {
            return TransportState.NO_MEDIA_PRESENT;
        }
        return manualPaused ? TransportState.PAUSED_PLAYBACK : TransportState.STOPPED;
    }

    private int getVolumePercent() {
        if (audioManager == null) {
            return 0;
        }
        int max = Math.max(1, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        return Math.round(current * 100f / max);
    }

    private void setVolumePercent(int value) {
        if (audioManager == null) {
            return;
        }
        int max = Math.max(1, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        int streamVolume = Math.max(0, Math.min(max, Math.round(value * max / 100f)));
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, streamVolume, 0);
        notifyStateChanged();
    }

    private void notifyStateChanged() {
        if (Looper.myLooper() != mainHandler.getLooper()) {
            mainHandler.post(this::notifyStateChanged);
            return;
        }
        UpnpPlaybackState state = getCurrentState();
        for (int i = listeners.size() - 1; i >= 0; i--) {
            listeners.get(i).onPlaybackChanged(state);
        }
    }

    private String getCurrentLyricLine(long positionMs) {
        if (TextUtils.isEmpty(lyric)) {
            return "";
        }
        String normalized = normalizeLyric(lyric);
        Matcher matcher = Pattern.compile("\\[(\\d{1,2}):(\\d{1,2})(?:\\.(\\d{1,3}))?\\]([^\\[]*)")
                .matcher(normalized);
        String latest = "";
        while (matcher.find()) {
            long timeMs = (parseLong(matcher.group(1)) * 60L + parseLong(matcher.group(2))) * 1000L;
            String fraction = matcher.group(3);
            if (!TextUtils.isEmpty(fraction)) {
                if (fraction.length() == 1) {
                    timeMs += parseLong(fraction) * 100L;
                } else if (fraction.length() == 2) {
                    timeMs += parseLong(fraction) * 10L;
                } else {
                    timeMs += parseLong(fraction);
                }
            }
            if (positionMs >= timeMs) {
                latest = matcher.group(4).trim();
            } else {
                break;
            }
        }
        if (!TextUtils.isEmpty(latest)) {
            return latest;
        }
        String[] lines = normalized.split("\\r?\\n");
        for (String line : lines) {
            String clean = line.replaceAll("\\[[^]]*]", "").trim();
            if (!TextUtils.isEmpty(clean)) {
                return clean;
            }
        }
        return normalized.trim();
    }

    private static String normalizeLyric(String value) {
        if (value == null) {
            return "";
        }
        return htmlDecode(value.replace("\\n", "\n").replace("\\r", ""));
    }

    private static String describeLyric(String value) {
        if (TextUtils.isEmpty(value)) {
            return "empty";
        }
        boolean timed = Pattern.compile("\\[\\d{1,2}:\\d{1,2}(?:\\.\\d{1,3})?\\]")
                .matcher(value).find();
        return "len=" + value.length() + ", timed=" + timed + ", hash=" + value.hashCode();
    }

    private static String extractXmlTagValue(String xml, String tag) {
        if (TextUtils.isEmpty(xml) || TextUtils.isEmpty(tag)) {
            return "";
        }
        String escapedTag = Pattern.quote(tag);
        String pattern = "<" + escapedTag + "(?:\\s[^>]*)?>(.*?)</" + escapedTag + ">";
        Matcher matcher = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(xml);
        if (matcher.find()) {
            return htmlDecode(matcher.group(1).trim());
        }
        int colon = tag.indexOf(':');
        if (colon > 0) {
            return extractXmlTagValue(xml, tag.substring(colon + 1));
        }
        return "";
    }

    private static String extractXmlTagAttribute(String xml, String attribute) {
        if (TextUtils.isEmpty(xml) || TextUtils.isEmpty(attribute)) {
            return "";
        }
        Matcher matcher = Pattern.compile(attribute + "\\s*=\\s*['\"]([^'\"]+)['\"]",
                Pattern.CASE_INSENSITIVE).matcher(xml);
        return matcher.find() ? htmlDecode(matcher.group(1).trim()) : "";
    }

    private static long parseDurationMs(String value) {
        if (TextUtils.isEmpty(value)) {
            return 0L;
        }
        try {
            String[] parts = value.split(":");
            if (parts.length == 3) {
                return (parseLong(parts[0]) * 3600L + parseLong(parts[1]) * 60L
                        + Math.round(Double.parseDouble(parts[2]))) * 1000L;
            }
        } catch (Exception ignored) {
        }
        return 0L;
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            return 0L;
        }
    }

    @SuppressWarnings("deprecation")
    private static String htmlDecode(String value) {
        if (value == null) {
            return "";
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            return Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString();
        }
        return Html.fromHtml(value).toString();
    }

    private static String titleFromUrl(String url) {
        if (TextUtils.isEmpty(url)) {
            return "";
        }
        int question = url.indexOf('?');
        String clean = question >= 0 ? url.substring(0, question) : url;
        int slash = clean.lastIndexOf('/');
        String name = slash >= 0 ? clean.substring(slash + 1) : clean;
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        return name.replace('_', ' ');
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (!TextUtils.isEmpty(value)) {
                return value;
            }
        }
        return "";
    }

    private static void applyAudioAttributes(MediaPlayer player) {
        player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build());
    }
}
