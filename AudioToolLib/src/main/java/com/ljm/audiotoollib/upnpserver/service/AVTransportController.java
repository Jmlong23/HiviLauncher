package com.ljm.audiotoollib.upnpserver.service;

import android.util.Log;

import com.ljm.audiotoollib.upnpserver.cling.model.ModelUtil;
import com.ljm.audiotoollib.upnpserver.cling.model.types.ErrorCode;
import com.ljm.audiotoollib.upnpserver.cling.model.types.UnsignedIntegerFourBytes;
import com.ljm.audiotoollib.upnpserver.cling.support.avtransport.AVTransportException;
import com.ljm.audiotoollib.upnpserver.cling.support.avtransport.AVTransportErrorCode;
import com.ljm.audiotoollib.upnpserver.cling.support.model.DeviceCapabilities;
import com.ljm.audiotoollib.upnpserver.cling.support.model.MediaInfo;
import com.ljm.audiotoollib.upnpserver.cling.support.model.PositionInfo;
import com.ljm.audiotoollib.upnpserver.cling.support.model.StorageMedium;
import com.ljm.audiotoollib.upnpserver.cling.support.model.TransportAction;
import com.ljm.audiotoollib.upnpserver.cling.support.model.TransportInfo;
import com.ljm.audiotoollib.upnpserver.cling.support.model.TransportSettings;
import com.ljm.audiotoollib.upnpserver.cling.support.model.TransportState;
import com.ljm.audiotoollib.upnpserver.cling.support.model.TransportStatus;
import com.ljm.audiotoollib.upnpserver.entity.InfoEx;
import com.ljm.audiotoollib.upnpserver.entity.TrackINFO_Type;
import com.ljm.audiotoollib.upnpserver.listener.OnMediaControlListener;
import com.ljm.audiotoollib.upnpserver.listener.OnVideoControlListener;
import com.ljm.audiotoollib.upnpserver.utils.UpnpMetadataDebugUtil;
import com.ljm.audiotoollib.upnpserver.utils.UpnpMediaTypeUtil;

import java.net.URI;

public class AVTransportController implements IRendererInterface.IAVTransportControl {
    private static final String TAG = "AVTransportController";
    private static final TransportAction[] TRANSPORT_ACTION_STOPPED = new TransportAction[]{TransportAction.Play};
    private static final TransportAction[] TRANSPORT_ACTION_PLAYING = new TransportAction[]{TransportAction.Stop, TransportAction.Pause, TransportAction.Seek};
    private static final TransportAction[] TRANSPORT_ACTION_PAUSE_PLAYBACK = new TransportAction[]{TransportAction.Play, TransportAction.Seek, TransportAction.Stop};

    public interface OnTransportStateChangedListener {
        void onTransportStateChanged(TransportState state, TransportStatus status,
                                     TransportAction[] availableActions);
    }

    private enum VideoControlMode {
        UNKNOWN,
        STANDARD_AV_TRANSPORT,
        DEDICATED_VIDEO_ACTIONS
    }

    private final UnsignedIntegerFourBytes mInstanceId;
    private static  AVTransportController instance;
    private final TransportInfo mTransportInfo = new TransportInfo();
    private final TransportSettings mTransportSettings = new TransportSettings();
    private PositionInfo mOriginPositionInfo = new PositionInfo();
    private MediaInfo mMediaInfo = new MediaInfo();
    private OnMediaControlListener mMediaControl;
    private PositionInfo mOriginPositionVideoInfo = new PositionInfo();
    private MediaInfo mMediaVideoInfo = new MediaInfo();
    private OnVideoControlListener mVideoControl;
    private volatile TransportState curTransportState = TransportState.STOPPED;
    private volatile TransportStatus curTransportStatus = TransportStatus.OK;
    private final Object transportStateLock = new Object();
    private volatile boolean isVideoCasting = false;
    private volatile VideoControlMode videoControlMode = VideoControlMode.UNKNOWN;
    private volatile OnTransportStateChangedListener mTransportStateChangedListener;


    public static synchronized AVTransportController getInstance() {
        if(instance == null) {
            instance =  new AVTransportController();
        }
        return instance;
    }


    public void stopVideo() {
        Log.i(TAG,"stopVideo");
        isVideoCasting = false;
        videoControlMode = VideoControlMode.UNKNOWN;
        setTransportState(TransportState.STOPPED);
    }


    public AVTransportController() {
        mInstanceId = new UnsignedIntegerFourBytes(0);
    }

    public void setOnMediaControllerListener(OnMediaControlListener listener) {
        mMediaControl = listener;
    }

    public void setOnVideoControlListener(OnVideoControlListener listener) {
        mVideoControl = listener;
    }

    public void setOnTransportStateChangedListener(OnTransportStateChangedListener listener) {
        synchronized (transportStateLock) {
            mTransportStateChangedListener = listener;
        }
    }

    public void clearOnTransportStateChangedListener(OnTransportStateChangedListener listener) {
        synchronized (transportStateLock) {
            if (mTransportStateChangedListener == listener) {
                mTransportStateChangedListener = null;
            }
        }
    }

    public void reportAudioTransportState(TransportState state, TransportStatus status) {
        synchronized (transportStateLock) {
            if (!isVideoCasting) {
                setTransportState(state, status);
            }
        }
    }

    /**
     * Called by local video playback paths that do not originate from an AVTransport action, such
     * as AI interruption and renderer callbacks. This keeps subscribed DLNA controllers in sync.
     */
    public void reportVideoTransportState(TransportState state) {
        setTransportState(state);
    }

    public UnsignedIntegerFourBytes getInstanceId() {
        return mInstanceId;
    }



    @Override
    public DeviceCapabilities getDeviceCapabilities() {
        Log.i(TAG,"getDeviceCapabilities");
        return new DeviceCapabilities(new StorageMedium[]{StorageMedium.NETWORK});
    }

    @Override
    public MediaInfo getMediaInfo() {
        if (isVideoCasting) {
            /*
             * Standard DLNA controllers such as Bilibili use GetMediaInfo together with
             * GetPositionInfo to decide whether the current item supports a time seek. Returning
             * an empty URI and 00:00:00 here makes a real video session look like NO_MEDIA.
             */
            return buildVideoMediaInfo();
        }
        PlayQueueController.CurrentTrackSnapshot snapshot =
                PlayQueueController.getInstance().getCurrentTrackSnapshot();
        if (!snapshot.isValid()) {
            mMediaInfo = new MediaInfo("", "", new UnsignedIntegerFourBytes(0), "00:00:00", StorageMedium.NONE);
            return mMediaInfo;
        }
        TrackINFO_Type info_type = snapshot.getTrack();

        mMediaInfo = new MediaInfo(info_type.getURL(), info_type.getMetaData(),
                new UnsignedIntegerFourBytes(1), getPositionInfo().getTrackDuration(), StorageMedium.NETWORK);
        return mMediaInfo;
    }

    @Override
    public PositionInfo getPositionInfo() {
//        Log.i(TAG,"getPositionInfo");
        if (isVideoCasting) {
            /*
             * Third-party DLNA controllers normally poll the standard GetPositionInfo action,
             * rather than the legacy GetPositionVideoInfo extension. Return the actual IjkPlayer
             * timing and the current video identity so they can associate the position with the
             * active media item and enable standard REL_TIME seek controls.
             */
            return buildVideoPositionInfo(true);
        }
        PlayQueueController.CurrentTrackSnapshot snapshot =
                PlayQueueController.getInstance().getCurrentTrackSnapshot();
        if (!snapshot.isValid()) {
            mOriginPositionInfo = new PositionInfo(0, "00:00:00", "", "",
                    "00:00:00", "00:00:00", Integer.MAX_VALUE, Integer.MAX_VALUE);
            return mOriginPositionInfo;
        }
        int index = snapshot.getIndex();
        TrackINFO_Type info_type = snapshot.getTrack();

        if (mMediaControl != null) {
            try {
                InfoEx infoEx = mMediaControl.getInfoEx();
                if (infoEx != null) {
                    String trackDuration = normalizePositionTime(infoEx.getTrackDuration());
                    String relTime = normalizePositionTime(infoEx.getRelTime());
                    String absTime = normalizePositionTime(infoEx.getAbsTime());
                    String trackMetaData = isEmpty(infoEx.getTrackMetaData()) ? info_type.getMetaData() : infoEx.getTrackMetaData();
                    String trackUri = isEmpty(infoEx.getTrackURI()) ? info_type.getURL() : infoEx.getTrackURI();
                    mOriginPositionInfo = new PositionInfo(index + 1,
                            trackDuration,
                            trackMetaData,
                            trackUri,
                            relTime,
                            absTime,
                            Integer.MAX_VALUE,
                            Integer.MAX_VALUE);
                    return mOriginPositionInfo;
                }
            } catch (Throwable t) {
                Log.w(TAG, "getPositionInfo from mediaControl failed", t);
            }
        }

        mOriginPositionInfo = new PositionInfo(index + 1, info_type.getMetaData(),info_type.getURL());
        return mOriginPositionInfo;
    }


    @Override
    public TransportSettings getTransportSettings() {
        Log.i(TAG,"getTransportSettings");
        return mTransportSettings;
    }

    @Override
    public InfoEx getInfoEx() {
        // getInfoEx 是自定义 UPnP Action，仅 HiVi 音乐播放器 App 调用。
        // 视频投屏时不应返回视频信息，避免 MV 元数据污染音乐播放器 UI，
        // 同时避免音频的 STOPPED 状态覆盖 curTransportState 导致投屏控制异常。
        if (isVideoCasting) {
            InfoEx infoEx = new InfoEx();
            infoEx.setCurrentTransportState(TransportState.NO_MEDIA_PRESENT);
            return infoEx;
        }
        if(mMediaControl != null) {
            InfoEx infoEx = mMediaControl.getInfoEx();
            updateTransportState(infoEx);
            return infoEx;
        }
        return null;
    }

    private void updateTransportState(InfoEx infoEx) {
        if (infoEx != null && infoEx.getCurrentTransportState() != null) {
            reportAudioTransportState(infoEx.getCurrentTransportState(),
                    infoEx.getCurrentTransportStatus());
        }
    }


    @Override
    public void play(String speed) {
        Log.i(TAG,"play speed: " + speed + " isVideoCasting: " + isVideoCasting
                + " videoControlMode: " + videoControlMode);
        if (shouldRouteStandardActionToVideo("play")) {
            setTransportState(getVideoTransportStateForPlay());
            if (mVideoControl != null) {
                mVideoControl.playVideo();
            }
            return;
        }
        if(mMediaControl != null) {
            mMediaControl.play();
        }
    }

    public void pause() {
        Log.i(TAG,"pause isVideoCasting: " + isVideoCasting
                + " videoControlMode: " + videoControlMode);
        if (shouldRouteStandardActionToVideo("pause")) {
            setTransportState(TransportState.PAUSED_PLAYBACK);
            if (mVideoControl != null) {
                mVideoControl.pauseVideo();
            }
            return;
        }
        if(mMediaControl != null) {
            mMediaControl.pause();
        }
    }

    @Override
    public void seek(String unit, String target) throws AVTransportException {
        if (!"REL_TIME".equals(unit) && !"ABS_TIME".equals(unit)) {
            throw new AVTransportException(AVTransportErrorCode.SEEKMODE_NOT_SUPPORTED);
        }
        long positionMs;
        try {
            if (target == null || !target.matches("[0-9]+:[0-5][0-9]:[0-5][0-9](\\.[0-9]+)?")) {
                throw new IllegalArgumentException("Invalid time");
            }
            String[] time = target.split(":");
            long hours = Math.multiplyExact(Long.parseLong(time[0]), 3600L);
            long seconds = Math.addExact(hours, ModelUtil.fromTimeString("0:" + time[1] + ":" + time[2]));
            positionMs = Math.multiplyExact(seconds, 1000L);
            if (positionMs < 0) throw new IllegalArgumentException("Negative time");
        } catch (RuntimeException invalid) {
            throw new AVTransportException(AVTransportErrorCode.ILLEGAL_SEEK_TARGET);
        }
        Log.i(TAG,"seek unit: " + unit + " target:" + target + " isVideoCasting: "
                + isVideoCasting + " videoControlMode: " + videoControlMode);
        if (shouldRouteStandardActionToVideo("seek")) {
            if(mVideoControl != null) {
                mVideoControl.seekVideo(positionMs);
            } else {
                throw new AVTransportException(AVTransportErrorCode.TRANSITION_NOT_AVAILABLE);
            }
            return;
        }
        if(mMediaControl != null) {
            mMediaControl.seek(positionMs);
        } else {
            throw new AVTransportException(AVTransportErrorCode.TRANSITION_NOT_AVAILABLE);
        }
    }

    synchronized public void stop() {
        Log.i(TAG,"stop isVideoCasting: " + isVideoCasting);
        if (isVideoCasting) {
            isVideoCasting = false;
            videoControlMode = VideoControlMode.UNKNOWN;
            setTransportState(TransportState.STOPPED);
            if(mVideoControl != null) {
                mVideoControl.stop();
            }
            return;
        }

        if (mMediaControl != null) {
            mMediaControl.stop();
        }
    }

    @Override
    public void previous() {
        Log.i(TAG,"previous isVideoCasting: " + isVideoCasting
                + " videoControlMode: " + videoControlMode);
        if (shouldRouteStandardActionToVideo("previous")) {
            if(mVideoControl != null) {
                mVideoControl.previousVideo();
            }
            return;
        }
        if(mMediaControl != null) {
            mMediaControl.previous();
        }
    }

    @Override
    public void next() {
        Log.i(TAG,"next isVideoCasting: " + isVideoCasting
                + " videoControlMode: " + videoControlMode);
        if (shouldRouteStandardActionToVideo("next")) {
            if(mVideoControl != null) {
                mVideoControl.nextVideo();
            }
            return;
        }
        if(mMediaControl != null) {
            mMediaControl.next();
        }
    }

    @Override
    public void record() {
        Log.i(TAG,"record");
    }

    @Override
    public void setCurrentLyric(String lyric, String terraceType) {
        Log.i(TAG, "setCurrentLyric lyric={" + summarizeValue(lyric) + "} terraceType: " + terraceType);

        // 通过MediaControlListener传递歌词数据
        if(mMediaControl != null) {
            Log.i(TAG, "mMediaControl不为空，调用onLyricReceived");
            mMediaControl.onLyricReceived(lyric, terraceType);
        } else {
            Log.e(TAG, "mMediaControl为空，无法传递歌词数据");
        }
    }

    @Override
    public void setPlayMode(String newPlayMode) {
        Log.i(TAG,"setPlayMode");
    }

    @Override
    public void setRecordQualityMode(String newRecordQualityMode) {
        Log.i(TAG,"setRecordQualityMode");
    }

    @Override
    public void setAVTransportURI(String currentURI, String currentURIMetaData) throws AVTransportException {
        Log.i(TAG, "setAVTransportURI uri={" + summarizeValue(currentURI)
                + "} meta={" + summarizeValue(currentURIMetaData) + "}");
        UpnpMetadataDebugUtil.logTransportMetadata(TAG, currentURI, currentURIMetaData);
        try {
            new URI(currentURI);
        } catch (Exception ex) {
            isVideoCasting = false;
            videoControlMode = VideoControlMode.UNKNOWN;
            throw new AVTransportException(ErrorCode.INVALID_ARGS, "CurrentURI can not be null or malformed");
        }
        isVideoCasting = UpnpMediaTypeUtil.determineMediaKind(currentURI, currentURIMetaData)
                != UpnpMediaTypeUtil.MediaKind.AUDIO;
        videoControlMode = VideoControlMode.UNKNOWN;
        setTransportState(TransportState.TRANSITIONING);
        mMediaVideoInfo = new MediaInfo(currentURI, currentURIMetaData, new UnsignedIntegerFourBytes(1), "", StorageMedium.NETWORK);
        mOriginPositionVideoInfo = new PositionInfo(1, currentURIMetaData, currentURI);
        if(mVideoControl != null){
            mVideoControl.setAVTransportURI(currentURI,currentURIMetaData);
        }
    }

    @Override
    public void setNextAVTransportURI(String nextURI, String nextURIMetaData) {
        Log.i(TAG, "setNextAVTransportURI uri={" + summarizeValue(nextURI)
                + "} meta={" + summarizeValue(nextURIMetaData) + "}");
    }

    @Override
    public void playVideo(String speed) throws AVTransportException {
        Log.i(TAG,"playVideo");
        markDedicatedVideoAction("playVideo");
        setTransportState(getVideoTransportStateForPlay());
        if(mVideoControl != null) {
            mVideoControl.playVideo();
        }
    }

    @Override
    public void pauseVideo() throws AVTransportException {
        Log.i(TAG,"pauseVideo");
        markDedicatedVideoAction("pauseVideo");
        setTransportState(TransportState.PAUSED_PLAYBACK);
        if(mVideoControl != null) {
            mVideoControl.pauseVideo();
        }
    }

    @Override
    public void seekVideo(String unit, String target) throws AVTransportException {
        Log.i(TAG,"seekVideo unit: " + unit + " target:" + target);
        markDedicatedVideoAction("seekVideo");
        if(mVideoControl != null) {
            mVideoControl.seekVideo(ModelUtil.fromTimeString(target) * 1000);
        }
    }

    @Override
    public void previousVideo() {
        Log.i(TAG,"previousVideo");
        markDedicatedVideoAction("previousVideo");
        if(mVideoControl != null) {
            mVideoControl.previousVideo();
        }
    }

    @Override
    public void nextVideo() {
        Log.i(TAG,"nextVideo");
        markDedicatedVideoAction("nextVideo");
        if(mVideoControl != null) {
            mVideoControl.nextVideo();
        }
    }

    @Override
    public MediaInfo getMediaVideoInfo() {
        markDedicatedVideoAction("getMediaVideoInfo");
        return buildVideoMediaInfo();
    }

    @Override
    public PositionInfo getPositionVideoInfo() {
        markDedicatedVideoAction("getPositionVideoInfo");
        return buildVideoPositionInfo(true);
    }

    private boolean shouldRouteStandardActionToVideo(String action) {
        if (!isVideoCasting) {
            return false;
        }
        if (videoControlMode == VideoControlMode.DEDICATED_VIDEO_ACTIONS) {
            Log.i(TAG, action + " routed to music control because current video uses dedicated actions");
            return false;
        }
        if (videoControlMode == VideoControlMode.UNKNOWN) {
            videoControlMode = VideoControlMode.STANDARD_AV_TRANSPORT;
            Log.i(TAG, "video control mode -> STANDARD_AV_TRANSPORT, action=" + action);
        }
        return true;
    }

    private void markDedicatedVideoAction(String action) {
        if (!isVideoCasting || videoControlMode == VideoControlMode.DEDICATED_VIDEO_ACTIONS) {
            return;
        }
        videoControlMode = VideoControlMode.DEDICATED_VIDEO_ACTIONS;
        Log.i(TAG, "video control mode -> DEDICATED_VIDEO_ACTIONS, action=" + action);
    }

    @Override
    public TransportInfo getTransportInfo() {
//        Log.i(TAG,"getTransportInfo: " + curTransportState);
        if (isVideoCasting) {
            updateVideoTransportStateFromPlayback();
        } else if (mMediaControl != null) {
            updateTransportState(mMediaControl.getInfoEx());
        }
        synchronized (transportStateLock) {
            return new TransportInfo(curTransportState, curTransportStatus, "1");
        }
    }

    public synchronized TransportAction[] getCurrentTransportActions() {
        if (isVideoCasting) {
            updateVideoTransportStateFromPlayback();
        } else if (mMediaControl != null) {
            updateTransportState(mMediaControl.getInfoEx());
        }
        Log.i(TAG,"getCurrentTransportState: " + curTransportState);
        return getTransportActions(curTransportState);
    }

    private PositionInfo buildVideoPositionInfo(boolean includeVideoUri) {
        if (mVideoControl == null) {
            return mOriginPositionVideoInfo;
        }
        try {
            long durationMs = Math.max(0L, mVideoControl.getDurationVideo());
            long positionMs = Math.max(0L, mVideoControl.getPositionVideo());
            String duration = ModelUtil.toTimeString(durationMs / 1000L);
            String realTime = ModelUtil.toTimeString(positionMs / 1000L);
            String trackUri = includeVideoUri && mMediaVideoInfo != null
                    && !isEmpty(mMediaVideoInfo.getCurrentURI())
                    ? mMediaVideoInfo.getCurrentURI()
                    : "";
            String trackMetaData = includeVideoUri && mMediaVideoInfo != null
                    && !isEmpty(mMediaVideoInfo.getCurrentURIMetaData())
                    ? mMediaVideoInfo.getCurrentURIMetaData()
                    : "";
            return new PositionInfo(1, duration, trackMetaData, trackUri, realTime, realTime,
                    Integer.MAX_VALUE, Integer.MAX_VALUE);
        } catch (Throwable t) {
            Log.w(TAG, "get video position failed", t);
            return mOriginPositionVideoInfo;
        }
    }

    private MediaInfo buildVideoMediaInfo() {
        MediaInfo videoInfo = mMediaVideoInfo;
        String currentUri = videoInfo == null || isEmpty(videoInfo.getCurrentURI())
                ? "" : videoInfo.getCurrentURI();
        String currentMetaData = videoInfo == null || isEmpty(videoInfo.getCurrentURIMetaData())
                ? "" : videoInfo.getCurrentURIMetaData();
        String duration = videoInfo == null
                ? "00:00:00"
                : normalizePositionTime(videoInfo.getMediaDuration());
        if (mVideoControl != null) {
            try {
                long durationMs = Math.max(0L, mVideoControl.getDurationVideo());
                if (durationMs > 0L) {
                    duration = ModelUtil.toTimeString(durationMs / 1000L);
                }
            } catch (Throwable t) {
                Log.w(TAG, "get video media duration failed", t);
            }
        }
        return new MediaInfo(currentUri, currentMetaData, new UnsignedIntegerFourBytes(1),
                duration, StorageMedium.NETWORK);
    }

    private void updateVideoTransportStateFromPlayback() {
        if (mVideoControl == null) {
            return;
        }
        try {
            TransportState actualState = mVideoControl.getVideoTransportState();
            if (actualState != null) {
                setTransportState(actualState);
            }
        } catch (Throwable t) {
            Log.w(TAG, "get video transport state failed", t);
        }
    }

    /**
     * A Play received while a video session is paused is a resume, not a fresh asynchronous load.
     * QQ Music relies on the AVTransport LastChange event for its play button state and does not
     * necessarily poll again after issuing Play, so reporting TRANSITIONING here leaves its UI in
     * the wrong state when IjkPlayer resumes without another first-frame callback.
     */
    private TransportState getVideoTransportStateForPlay() {
        if (!isVideoCasting) {
            return TransportState.TRANSITIONING;
        }
        if (curTransportState == TransportState.PAUSED_PLAYBACK) {
            return TransportState.PLAYING;
        }
        if (mVideoControl != null) {
            try {
                TransportState actualState = mVideoControl.getVideoTransportState();
                if (actualState == TransportState.PAUSED_PLAYBACK
                        || actualState == TransportState.PLAYING) {
                    return TransportState.PLAYING;
                }
            } catch (Throwable t) {
                Log.w(TAG, "get video transport state for play failed", t);
            }
        }
        return TransportState.TRANSITIONING;
    }

    private void setTransportState(TransportState state) {
        setTransportState(state, TransportStatus.OK);
    }

    private void setTransportState(TransportState state, TransportStatus status) {
        synchronized (transportStateLock) {
            TransportStatus resolvedStatus = status == null ? TransportStatus.OK : status;
            if (state == null || (curTransportState == state && curTransportStatus == resolvedStatus)) {
                return;
            }
            TransportState previousState = curTransportState;
            curTransportState = state;
            curTransportStatus = resolvedStatus;
            Log.i(TAG, "transport state " + previousState + " -> " + state
                    + ", status=" + resolvedStatus + ", isVideoCasting=" + isVideoCasting);
            OnTransportStateChangedListener listener = mTransportStateChangedListener;
            if (listener != null) {
                try {
                    listener.onTransportStateChanged(state, resolvedStatus, getTransportActions(state));
                } catch (Throwable t) {
                    Log.w(TAG, "notify transport state changed failed", t);
                }
            }
        }
    }

    private TransportAction[] getTransportActions(TransportState state) {
        switch (state) {
            case NO_MEDIA_PRESENT:
                return new TransportAction[0];
            case PLAYING:
            case TRANSITIONING:
                return TRANSPORT_ACTION_PLAYING.clone();
            case PAUSED_PLAYBACK:
                return TRANSPORT_ACTION_PAUSE_PLAYBACK.clone();
            default:
                return TRANSPORT_ACTION_STOPPED.clone();
        }
    }

    private String normalizePositionTime(String value) {
        return isEmpty(value) ? "00:00:00" : value;
    }

    private boolean isEmpty(String value) {
        return value == null || value.length() == 0;
    }

    private static String summarizeValue(String value) {
        if (value == null) {
            return "null";
        }
        return "len=" + value.length() + ", hash=" + value.hashCode();
    }

}
