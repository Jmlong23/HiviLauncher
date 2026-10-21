package com.ljm.audiotoollib.upnpserver.service;

import android.util.Log;

import com.ljm.audiotoollib.upnpserver.cling.model.types.UnsignedIntegerFourBytes;
import com.ljm.audiotoollib.upnpserver.cling.support.avtransport.AVTransportException;
import com.ljm.audiotoollib.upnpserver.cling.support.avtransport.AVTransportErrorCode;
import com.ljm.audiotoollib.upnpserver.cling.support.avtransport.lastchange.AVTransportVariable;
import com.ljm.audiotoollib.upnpserver.cling.support.avtransport.AbstractAVTransportService;
import com.ljm.audiotoollib.upnpserver.cling.support.lastchange.LastChange;
import com.ljm.audiotoollib.upnpserver.cling.support.lastchange.EventedValue;
import com.ljm.audiotoollib.upnpserver.cling.support.model.DeviceCapabilities;
import com.ljm.audiotoollib.upnpserver.cling.support.model.MediaInfo;
import com.ljm.audiotoollib.upnpserver.cling.support.model.PositionInfo;
import com.ljm.audiotoollib.upnpserver.cling.support.model.TransportAction;
import com.ljm.audiotoollib.upnpserver.cling.support.model.TransportInfo;
import com.ljm.audiotoollib.upnpserver.cling.support.model.TransportSettings;
import com.ljm.audiotoollib.upnpserver.cling.support.model.TransportState;
import com.ljm.audiotoollib.upnpserver.cling.support.model.TransportStatus;
import com.ljm.audiotoollib.upnpserver.entity.InfoEx;

import java.util.function.Consumer;
import java.net.URI;

public class AVTransportServiceImpl extends AbstractAVTransportService {
    private static final String TAG = "AVTransportServiceImpl";

    private final RenderControlManager mRenderControlManager;
    private final Consumer<Runnable> eventDispatcher;
    private AVTransportController transportController;
    private AVTransportController.OnTransportStateChangedListener transportStateChangedListener;
    private volatile boolean closed;
    private final StateEventPublisher mediaPublisher = new StateEventPublisher();

    private EventedValue[] mediaValues(UnsignedIntegerFourBytes instanceId) throws AVTransportException {
        PositionInfo position = getPositionInfo(instanceId);
        MediaInfo media = getMediaInfo(instanceId);
        InfoEx info = getInfoEx(instanceId);
        String source = media.getNumberOfTracks().getValue() == 0L || info == null
                || info.getTrackSource() == null ? "NONE" : info.getTrackSource();
        return new EventedValue[]{
                new AVTransportVariable.CurrentTrack(position.getTrack()),
                new AVTransportVariable.CurrentTrackDuration(position.getTrackDuration()),
                new AVTransportVariable.CurrentMediaDuration(media.getMediaDuration()),
                new AVTransportVariable.RelativeTimePosition(position.getRelTime()),
                new AVTransportVariable.CurrentTrackURI(URI.create(position.getTrackURI())),
                new AVTransportVariable.CurrentTrackMetaData(position.getTrackMetaData()),
                new AVTransportVariable.AVTransportURI(URI.create(media.getCurrentURI())),
                new AVTransportVariable.AVTransportURIMetaData(media.getCurrentURIMetaData()),
                new AVTransportVariable.PlaybackStorageMedium(media.getPlayMedium()),
                new AVTransportVariable.TrackSource(source)};
    }

    @Override
    public void appendCurrentState(LastChange lastChange, UnsignedIntegerFourBytes instanceId) throws Exception {
        super.appendCurrentState(lastChange, instanceId);
        lastChange.setEventedValue(instanceId, mediaValues(instanceId));
    }

    public void refreshState() throws AVTransportException {
        if (closed) return;
        getTransportInfo(getDefaultInstanceID());
        EventedValue[] values = mediaValues(getDefaultInstanceID());
        eventDispatcher.accept(() -> {
            if (!closed) mediaPublisher.publish(getLastChange(), getPropertyChangeSupport(), values);
        });
    }

    public AVTransportServiceImpl(LastChange lastChange, RenderControlManager renderControlManager,
                                  Consumer<Runnable> eventDispatcher) {
        super(lastChange);
        mRenderControlManager = renderControlManager;
        this.eventDispatcher = eventDispatcher;
        AVTransportController controller = null;
        try {
            controller = (AVTransportController) mRenderControlManager.getAvTransportControl(
                    new UnsignedIntegerFourBytes(0));
        } catch (Throwable t) {
            Log.w(TAG, "get AVTransport controller for state events failed", t);
        }
        if (controller != null) {
            transportController = controller;
            transportStateChangedListener = new AVTransportController.OnTransportStateChangedListener() {
                @Override
                public void onTransportStateChanged(TransportState state, TransportStatus status,
                                                    TransportAction[] availableActions) {
                    AVTransportServiceImpl.this.eventDispatcher.accept(
                            () -> publishTransportStateChange(state, status, availableActions));
                }
            };
            controller.setOnTransportStateChangedListener(transportStateChangedListener);
        }
    }

    public void close() {
        closed = true;
        if (transportController != null) {
            transportController.clearOnTransportStateChangedListener(transportStateChangedListener);
        }
    }

    private void publishTransportStateChange(TransportState state,
                                             TransportStatus status,
                                             TransportAction[] availableActions) {
        if (closed) {
            return;
        }
        try {
            Log.i(TAG, "publish AVTransport state event: state=" + state
                    + ", status=" + status
                    + ", actions=" + java.util.Arrays.toString(availableActions));
            getLastChange().setEventedValue(new UnsignedIntegerFourBytes(0),
                    new AVTransportVariable.TransportState(state),
                    new AVTransportVariable.TransportStatus(status),
                    new AVTransportVariable.CurrentTransportActions(availableActions));
            getLastChange().fire(getPropertyChangeSupport());
        } catch (Throwable t) {
            Log.w(TAG, "publish AVTransport state event failed", t);
        }
    }

    @Override
    public UnsignedIntegerFourBytes[] getCurrentInstanceIds() {
        return mRenderControlManager.getAvTransportCurrentInstanceIds();
    }

    @Override
    protected TransportAction[] getCurrentTransportActions(UnsignedIntegerFourBytes instanceId) throws Exception {
        return mRenderControlManager.getAvTransportControl(instanceId).getCurrentTransportActions();
    }

    @Override
    public void playVideo(UnsignedIntegerFourBytes instanceId, String speed) throws AVTransportException {
        mRenderControlManager.getAvTransportControl(instanceId).playVideo(speed);
    }

    @Override
    public void pauseVideo(UnsignedIntegerFourBytes instanceId) throws AVTransportException {
        mRenderControlManager.getAvTransportControl(instanceId).pauseVideo();
    }

    @Override
    public void seekVideo(UnsignedIntegerFourBytes instanceId, String unit, String target) throws AVTransportException {
        mRenderControlManager.getAvTransportControl(instanceId).seekVideo(unit,target);
    }

    @Override
    public void nextVideo(UnsignedIntegerFourBytes instanceId) throws AVTransportException {
        mRenderControlManager.getAvTransportControl(instanceId).nextVideo();
    }

    @Override
    public void previousVideo(UnsignedIntegerFourBytes instanceId) throws AVTransportException {
        mRenderControlManager.getAvTransportControl(instanceId).previousVideo();
    }

    @Override
    public MediaInfo getMediaVideoInfo(UnsignedIntegerFourBytes instanceId) throws AVTransportException {
        return mRenderControlManager.getAvTransportControl(instanceId).getMediaVideoInfo();
    }

    @Override
    public PositionInfo getPositionVideoInfo(UnsignedIntegerFourBytes instanceId) throws AVTransportException {
        return mRenderControlManager.getAvTransportControl(instanceId).getPositionVideoInfo();
    }

    public TransportInfo getTransportInfo(UnsignedIntegerFourBytes instanceId) {
        return mRenderControlManager.getAvTransportControl(instanceId).getTransportInfo();
    }


    public DeviceCapabilities getDeviceCapabilities(UnsignedIntegerFourBytes instanceId) {
        return mRenderControlManager.getAvTransportControl(instanceId).getDeviceCapabilities();
    }
    @Override
    public MediaInfo getMediaInfo(UnsignedIntegerFourBytes instanceId) {
        return mRenderControlManager.getAvTransportControl(instanceId).getMediaInfo();
    }

    @Override
    public InfoEx getInfoEx(UnsignedIntegerFourBytes instanceId) throws AVTransportException {
        return mRenderControlManager.getAvTransportControl(instanceId).getInfoEx();
    }

    public PositionInfo getPositionInfo(UnsignedIntegerFourBytes instanceId) {
        return mRenderControlManager.getAvTransportControl(instanceId).getPositionInfo();
    }

    public TransportSettings getTransportSettings(UnsignedIntegerFourBytes instanceId) {
        return mRenderControlManager.getAvTransportControl(instanceId).getTransportSettings();
    }

    @Override
    public void next(UnsignedIntegerFourBytes instanceId) {
        mRenderControlManager.getAvTransportControl(instanceId).next();
    }

    @Override
    public void pause(UnsignedIntegerFourBytes instanceId) throws AVTransportException {
        mRenderControlManager.getAvTransportControl(instanceId).pause();
    }

    @Override
    public void play(UnsignedIntegerFourBytes instanceId, String arg1) throws AVTransportException {
        mRenderControlManager.getAvTransportControl(instanceId).play(arg1);
    }

    @Override
    public void previous(UnsignedIntegerFourBytes instanceId) {
        mRenderControlManager.getAvTransportControl(instanceId).previous();
    }

    @Override
    public void record(UnsignedIntegerFourBytes instanceId) {
        getLastChange();
        mRenderControlManager.getAvTransportControl(instanceId).record();
    }

    @Override
    public void seek(UnsignedIntegerFourBytes instanceId, String arg1, String arg2) throws AVTransportException {
        if (instanceId == null || mRenderControlManager.getAvTransportControl(instanceId) == null) {
            throw new AVTransportException(AVTransportErrorCode.INVALID_INSTANCE_ID);
        }
        mRenderControlManager.getAvTransportControl(instanceId).seek(arg1, arg2);
        // SeekTarget is an accepted command event, never replayed as current playback progress.
        eventDispatcher.accept(() -> {
            if (closed) return;
            getLastChange().setEventedValue(instanceId, new AVTransportVariable.A_ARG_TYPE_SeekTarget(arg2));
            getLastChange().fire(getPropertyChangeSupport());
        });
    }

    @Override
    public void setAVTransportURI(UnsignedIntegerFourBytes instanceId, String arg1, String arg2) throws AVTransportException {
        mRenderControlManager.getAvTransportControl(instanceId).setAVTransportURI(arg1, arg2);
    }

    @Override
    public void setNextAVTransportURI(UnsignedIntegerFourBytes instanceId, String arg1, String arg2) {
        mRenderControlManager.getAvTransportControl(instanceId).setNextAVTransportURI(arg1, arg2);
    }

    @Override
    public void setPlayMode(UnsignedIntegerFourBytes instanceId, String arg1) {
        mRenderControlManager.getAvTransportControl(instanceId).setPlayMode(arg1);
    }

    @Override
    public void setRecordQualityMode(UnsignedIntegerFourBytes instanceId, String arg1) {
        mRenderControlManager.getAvTransportControl(instanceId).setRecordQualityMode(arg1);
    }

    @Override
    public void setCurrentLyric(UnsignedIntegerFourBytes instanceId, String lyric, String terraceType) {
        Log.e("test setCurrentLyric", "terraceType" + terraceType + "//////////lyric: " + lyric);
        Log.i("AVTransportServiceImpl", "setCurrentLyric called with lyric length: " + (lyric != null ? lyric.length() : 0));
        mRenderControlManager.getAvTransportControl(instanceId).setCurrentLyric(lyric,terraceType);
    }

    @Override
    public void stop(UnsignedIntegerFourBytes instanceId) throws AVTransportException {
        mRenderControlManager.getAvTransportControl(instanceId).stop();
    }

}
