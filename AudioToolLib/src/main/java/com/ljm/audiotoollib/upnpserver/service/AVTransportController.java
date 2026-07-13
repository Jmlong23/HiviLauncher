package com.ljm.audiotoollib.upnpserver.service;

import android.util.Log;

import com.ljm.audiotoollib.upnpserver.cling.model.ModelUtil;
import com.ljm.audiotoollib.upnpserver.cling.model.types.ErrorCode;
import com.ljm.audiotoollib.upnpserver.cling.model.types.UnsignedIntegerFourBytes;
import com.ljm.audiotoollib.upnpserver.cling.support.avtransport.AVTransportException;
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
    private TransportState curTransportState = TransportState.STOPPED;
    private boolean isVideoCasting = false;


    public static AVTransportController getInstance() {
        if(instance == null) {
            instance =  new AVTransportController();
        }
        return instance;
    }


    public void stopVideo() {
        Log.i(TAG,"stopVideo");
        curTransportState = TransportState.STOPPED;
        isVideoCasting = false;
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
            // 标准音频 MediaInfo 查询在视频投放期间不应暴露 MV 元数据；
            // 否则手机端音乐播放器会把当前投放的 MV 当成音乐曲目展示。
            // 视频详情仍然通过 getMediaVideoInfo()/getPositionVideoInfo() 提供。
            return new MediaInfo("", "", new UnsignedIntegerFourBytes(0), "00:00:00", StorageMedium.NONE);
        }
        if(PlayQueueController.getInstance().getPlayMusicList().getPQMusicList().isEmpty()){
            mMediaInfo = new MediaInfo("", "", new UnsignedIntegerFourBytes(1), "", StorageMedium.NETWORK);
            return mMediaInfo;
        }
        int index = PlayQueueController.getInstance().getPlayMusicList().getCurPlayIndex();
        TrackINFO_Type info_type = PlayQueueController.getInstance().getPlayMusicList().getPQMusicList().get(index);

        mMediaInfo = new MediaInfo(info_type.getURL(), info_type.getMetaData(), new UnsignedIntegerFourBytes(1), "", StorageMedium.NETWORK);
        return mMediaInfo;
    }

    @Override
    public PositionInfo getPositionInfo() {
//        Log.i(TAG,"getPositionInfo");
        if (isVideoCasting) {
            // 与 getMediaInfo() 一致：标准音频 PositionInfo 查询在视频投放期间返回空轨道，
            // 避免手机端音乐播放器继续显示 MV 标题、进度等信息。
            return new PositionInfo(0, "", "");
        }
        if(PlayQueueController.getInstance().getPlayMusicList().getPQMusicList().isEmpty()){
            return mOriginPositionInfo;
        }
        int index = PlayQueueController.getInstance().getPlayMusicList().getCurPlayIndex();
        TrackINFO_Type info_type = PlayQueueController.getInstance().getPlayMusicList().getPQMusicList().get(index);

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

        mOriginPositionInfo = new PositionInfo(1000, info_type.getMetaData(),info_type.getURL());
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
            curTransportState = infoEx.getCurrentTransportState();
        }
    }


    @Override
    public void play(String speed) {
        Log.i(TAG,"play speed: " + speed + " isVideoCasting: " + isVideoCasting);
        curTransportState = TransportState.TRANSITIONING;
        if (isVideoCasting) {
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
        Log.i(TAG,"pause isVideoCasting: " + isVideoCasting);
        curTransportState = TransportState.PAUSED_PLAYBACK;
        if (isVideoCasting) {
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
        Log.i(TAG,"seek unit: " + unit + " target:" + target + " isVideoCasting: " + isVideoCasting);
        if (isVideoCasting) {
            if(mVideoControl != null) {
                mVideoControl.seekVideo(ModelUtil.fromTimeString(target) * 1000);
            }
            return;
        }
        if(mMediaControl != null) {
            mMediaControl.seek(ModelUtil.fromTimeString(target) * 1000);
        }
    }

    synchronized public void stop() {
        Log.i(TAG,"stop isVideoCasting: " + isVideoCasting);
        curTransportState = TransportState.STOPPED;
        if (isVideoCasting) {
            isVideoCasting = false;
            if(mVideoControl != null) {
                mVideoControl.stop();
            }
            return;
        }

        if (mVideoControl != null) {
            mVideoControl.stop();
        }
    }

    @Override
    public void previous() {
        Log.i(TAG,"previous isVideoCasting: " + isVideoCasting);
        if (isVideoCasting) {
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
        Log.i(TAG,"next isVideoCasting: " + isVideoCasting);
        if (isVideoCasting) {
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
        curTransportState = TransportState.PLAYING;
        isVideoCasting = UpnpMediaTypeUtil.determineMediaKind(currentURI, currentURIMetaData)
                != UpnpMediaTypeUtil.MediaKind.AUDIO;
        try {
            new URI(currentURI);
        } catch (Exception ex) {
            isVideoCasting = false;
            throw new AVTransportException(ErrorCode.INVALID_ARGS, "CurrentURI can not be null or malformed");
        }
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
        curTransportState = TransportState.PLAYING;
        if(mVideoControl != null) {
            mVideoControl.playVideo();
        }
    }

    @Override
    public void pauseVideo() throws AVTransportException {
        Log.i(TAG,"pauseVideo");
        curTransportState = TransportState.PAUSED_PLAYBACK;
        if(mVideoControl != null) {
            mVideoControl.pauseVideo();
        }
    }

    @Override
    public void seekVideo(String unit, String target) throws AVTransportException {
        Log.i(TAG,"seekVideo unit: " + unit + " target:" + target);
        if(mVideoControl != null) {
            mVideoControl.seekVideo(ModelUtil.fromTimeString(target) * 1000);
        }
    }

    @Override
    public void previousVideo() {
        Log.i(TAG,"previousVideo");
        if(mVideoControl != null) {
            mVideoControl.previousVideo();
        }
    }

    @Override
    public void nextVideo() {
        Log.i(TAG,"nextVideo");
        if(mVideoControl != null) {
            mVideoControl.nextVideo();
        }
    }

    @Override
    public MediaInfo getMediaVideoInfo() {
        if(mMediaVideoInfo == null){
            mMediaVideoInfo = new MediaInfo("", "", new UnsignedIntegerFourBytes(1), "", StorageMedium.NETWORK);
            return mMediaVideoInfo;
        }
        return mMediaVideoInfo;
    }

    @Override
    public PositionInfo getPositionVideoInfo() {
        if(mVideoControl != null){
            String duration = ModelUtil.toTimeString(mVideoControl.getDurationVideo() / 1000);
            String realTime = ModelUtil.toTimeString(mVideoControl.getPositionVideo() / 1000);

            return new PositionInfo(0,  duration, mMediaVideoInfo.getCurrentURI() ,realTime,realTime );
        }
        return mOriginPositionVideoInfo;
    }

    @Override
    public TransportInfo getTransportInfo() {
//        Log.i(TAG,"getTransportInfo: " + curTransportState);
        if (!isVideoCasting && mMediaControl != null) {
            updateTransportState(mMediaControl.getInfoEx());
        }
        return new TransportInfo(curTransportState, TransportStatus.OK, "1");
    }

    public synchronized TransportAction[] getCurrentTransportActions() {
        if (!isVideoCasting && mMediaControl != null) {
            updateTransportState(mMediaControl.getInfoEx());
        }
        Log.i(TAG,"getCurrentTransportState: " + curTransportState);
        switch (curTransportState) {
            case PLAYING:
            case TRANSITIONING:
                return TRANSPORT_ACTION_PLAYING;
            case PAUSED_PLAYBACK:
                return TRANSPORT_ACTION_PAUSE_PLAYBACK;
            default:
                return TRANSPORT_ACTION_STOPPED;
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
