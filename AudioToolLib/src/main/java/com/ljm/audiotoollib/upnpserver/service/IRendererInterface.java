package com.ljm.audiotoollib.upnpserver.service;

import com.ljm.audiotoollib.upnpserver.cling.model.types.UnsignedIntegerFourBytes;
import com.ljm.audiotoollib.upnpserver.cling.model.types.UnsignedIntegerTwoBytes;
import com.ljm.audiotoollib.upnpserver.cling.support.avtransport.AVTransportException;
import com.ljm.audiotoollib.upnpserver.cling.support.model.DeviceCapabilities;
import com.ljm.audiotoollib.upnpserver.cling.support.model.MediaInfo;
import com.ljm.audiotoollib.upnpserver.cling.support.model.PositionInfo;
import com.ljm.audiotoollib.upnpserver.cling.support.model.TransportAction;
import com.ljm.audiotoollib.upnpserver.cling.support.model.TransportInfo;
import com.ljm.audiotoollib.upnpserver.cling.support.model.TransportSettings;
import com.ljm.audiotoollib.upnpserver.entity.InfoEx;

/**
 *
 */
public interface IRendererInterface {

    interface IControl {
        UnsignedIntegerFourBytes getInstanceId();
    }

    // -------------------------------------------------------------------------------------------
    // - AvTransport
    // -------------------------------------------------------------------------------------------
    interface IAVTransportControl extends IControl {
        void setAVTransportURI(String currentURI, String currentURIMetaData) throws AVTransportException;

        void setNextAVTransportURI(String nextURI, String nextURIMetaData);

        void setPlayMode(String newPlayMode);

        void setRecordQualityMode(String newRecordQualityMode);

        void play(String speed) throws AVTransportException;

        void pause() throws AVTransportException;

        void seek(String unit, String target) throws AVTransportException;

        void previous();

        void next();

        void stop() throws AVTransportException;

        void record();

        void setCurrentLyric(String lyric, String terraceType);

        TransportAction[] getCurrentTransportActions() throws Exception;

        DeviceCapabilities getDeviceCapabilities();

        MediaInfo getMediaInfo();

        PositionInfo getPositionInfo();

        TransportInfo getTransportInfo();

        TransportSettings getTransportSettings();

        InfoEx getInfoEx();


        void playVideo(String speed) throws AVTransportException;

        void pauseVideo() throws AVTransportException;

        void seekVideo(String unit, String target) throws AVTransportException;

        void previousVideo();

        void nextVideo();

        MediaInfo getMediaVideoInfo();

        PositionInfo getPositionVideoInfo();

    }

    // -------------------------------------------------------------------------------------------
    // - Audio
    // -------------------------------------------------------------------------------------------
    interface IAudioControl extends IControl {
        void setMute(String channelName, boolean desiredMute);

        boolean getMute(String channelName);

        void setVolume(String channelName, UnsignedIntegerTwoBytes desiredVolume);

        UnsignedIntegerTwoBytes getVolume(String channelName);

        void setAudioMode(String mode);

        String getAudioMode();

        void setRemoteControlMode(UnsignedIntegerTwoBytes mode);

        void setAudioBackground(String AudioContext);

        void selectAudioBackground(String AudioContext);

        void cancelAudioBackground();

        void setExtraInfo(String info);
    }


    // -------------------------------------------------------------------------------------------
    // - playQueue
    // -------------------------------------------------------------------------------------------
    interface IPlayQueueControl extends IControl {

        void createQueue(String queueContext);

        void AppendTracksInQueue(String queueContext);

        void PlayQueueWithIndex(String queueName, int index);

        void setQueueLoopMode(int loopMode);

        int getQueueLoopMode();

        String browseQueue(String queueName);

        void removeTracksInQueue(String queueName, int start, int end);
    }
}
