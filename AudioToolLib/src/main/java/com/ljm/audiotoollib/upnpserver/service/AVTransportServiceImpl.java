package com.ljm.audiotoollib.upnpserver.service;

import android.util.Log;

import com.ljm.audiotoollib.upnpserver.cling.model.types.UnsignedIntegerFourBytes;
import com.ljm.audiotoollib.upnpserver.cling.support.avtransport.AVTransportException;
import com.ljm.audiotoollib.upnpserver.cling.support.avtransport.AbstractAVTransportService;
import com.ljm.audiotoollib.upnpserver.cling.support.lastchange.LastChange;
import com.ljm.audiotoollib.upnpserver.cling.support.model.DeviceCapabilities;
import com.ljm.audiotoollib.upnpserver.cling.support.model.MediaInfo;
import com.ljm.audiotoollib.upnpserver.cling.support.model.PositionInfo;
import com.ljm.audiotoollib.upnpserver.cling.support.model.TransportAction;
import com.ljm.audiotoollib.upnpserver.cling.support.model.TransportInfo;
import com.ljm.audiotoollib.upnpserver.cling.support.model.TransportSettings;
import com.ljm.audiotoollib.upnpserver.entity.InfoEx;

public class AVTransportServiceImpl extends AbstractAVTransportService {
    private static final String TAG = "AVTransportServiceImpl";

    private final RenderControlManager mRenderControlManager;

    public AVTransportServiceImpl(LastChange lastChange, RenderControlManager renderControlManager) {
        super(lastChange);
        mRenderControlManager = renderControlManager;
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
        mRenderControlManager.getAvTransportControl(instanceId).seek(arg1, arg2);
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
