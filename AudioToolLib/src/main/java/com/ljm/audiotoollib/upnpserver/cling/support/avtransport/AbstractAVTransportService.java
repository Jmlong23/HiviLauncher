/*
 * Copyright (C) 2013 4th Line GmbH, Switzerland
 *
 * The contents of this file are subject to the terms of either the GNU
 * Lesser General Public License Version 2 or later ("LGPL") or the
 * Common Development and Distribution License Version 1 or later
 * ("CDDL") (collectively, the "License"). You may not use this file
 * except in compliance with the License. See LICENSE.txt for more
 * information.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.ljm.audiotoollib.upnpserver.cling.support.avtransport;

import com.ljm.audiotoollib.upnpserver.cling.binding.annotations.UpnpAction;
import com.ljm.audiotoollib.upnpserver.cling.binding.annotations.UpnpInputArgument;
import com.ljm.audiotoollib.upnpserver.cling.binding.annotations.UpnpOutputArgument;
import com.ljm.audiotoollib.upnpserver.cling.binding.annotations.UpnpService;
import com.ljm.audiotoollib.upnpserver.cling.binding.annotations.UpnpServiceId;
import com.ljm.audiotoollib.upnpserver.cling.binding.annotations.UpnpServiceType;
import com.ljm.audiotoollib.upnpserver.cling.binding.annotations.UpnpStateVariable;
import com.ljm.audiotoollib.upnpserver.cling.binding.annotations.UpnpStateVariables;
import com.ljm.audiotoollib.upnpserver.cling.model.ModelUtil;
import com.ljm.audiotoollib.upnpserver.cling.model.types.UnsignedIntegerFourBytes;
import com.ljm.audiotoollib.upnpserver.cling.support.avtransport.lastchange.AVTransportLastChangeParser;
import com.ljm.audiotoollib.upnpserver.cling.support.avtransport.lastchange.AVTransportVariable;
import com.ljm.audiotoollib.upnpserver.cling.support.lastchange.LastChange;
import com.ljm.audiotoollib.upnpserver.cling.support.lastchange.LastChangeDelegator;
import com.ljm.audiotoollib.upnpserver.cling.support.model.DeviceCapabilities;
import com.ljm.audiotoollib.upnpserver.cling.support.model.MediaInfo;
import com.ljm.audiotoollib.upnpserver.cling.support.model.PlayMode;
import com.ljm.audiotoollib.upnpserver.cling.support.model.PositionInfo;
import com.ljm.audiotoollib.upnpserver.cling.support.model.RecordMediumWriteStatus;
import com.ljm.audiotoollib.upnpserver.cling.support.model.RecordQualityMode;
import com.ljm.audiotoollib.upnpserver.cling.support.model.SeekMode;
import com.ljm.audiotoollib.upnpserver.cling.support.model.StorageMedium;
import com.ljm.audiotoollib.upnpserver.cling.support.model.TransportAction;
import com.ljm.audiotoollib.upnpserver.cling.support.model.TransportInfo;
import com.ljm.audiotoollib.upnpserver.cling.support.model.TransportSettings;
import com.ljm.audiotoollib.upnpserver.cling.support.model.TransportState;
import com.ljm.audiotoollib.upnpserver.cling.support.model.TransportStatus;
import com.ljm.audiotoollib.upnpserver.entity.InfoEx;

import java.beans.PropertyChangeSupport;
import java.net.URI;

/**
 * Skeleton of service with "LastChange" eventing support.
 *
 * @author Christian Bauer
 */
@UpnpService(
        serviceId = @UpnpServiceId("AVTransport"),
        serviceType = @UpnpServiceType(value = "AVTransport", version = 1),
        stringConvertibleTypes = LastChange.class
)
@UpnpStateVariables({
        @UpnpStateVariable(
                name = "TransportState",
                sendEvents = false,
                allowedValuesEnum = TransportState.class),
        @UpnpStateVariable(
                name = "TransportStatus",
                sendEvents = false,
                allowedValuesEnum = TransportStatus.class),
        @UpnpStateVariable(
                name = "PlaybackStorageMedium",
                sendEvents = false,
                defaultValue = "NONE",
                allowedValuesEnum = StorageMedium.class),
        @UpnpStateVariable(
                name = "RecordStorageMedium",
                sendEvents = false,
                defaultValue = "NOT_IMPLEMENTED",
                allowedValuesEnum = StorageMedium.class),
        @UpnpStateVariable(
                name = "PossiblePlaybackStorageMedia",
                sendEvents = false,
                datatype = "string",
                defaultValue = "NETWORK"),
        @UpnpStateVariable(
                name = "PossibleRecordStorageMedia",
                sendEvents = false,
                datatype = "string",
                defaultValue = "NOT_IMPLEMENTED"),
        @UpnpStateVariable( // TODO
                name = "CurrentPlayMode",
                sendEvents = false,
                defaultValue = "NORMAL",
                allowedValuesEnum = PlayMode.class),
        @UpnpStateVariable( // TODO
                name = "TransportPlaySpeed",
                sendEvents = false,
                datatype = "string",
                defaultValue = "1"), // 1, 1/2, 2, -1, 1/10, etc.
        @UpnpStateVariable(
                name = "RecordMediumWriteStatus",
                sendEvents = false,
                defaultValue = "NOT_IMPLEMENTED",
                allowedValuesEnum = RecordMediumWriteStatus.class),
        @UpnpStateVariable(
                name = "CurrentRecordQualityMode",
                sendEvents = false,
                defaultValue = "NOT_IMPLEMENTED",
                allowedValuesEnum = RecordQualityMode.class),
        @UpnpStateVariable(
                name = "PossibleRecordQualityModes",
                sendEvents = false,
                datatype = "string",
                defaultValue = "NOT_IMPLEMENTED"),
        @UpnpStateVariable(
                name = "NumberOfTracks",
                sendEvents = false,
                datatype = "ui4",
                defaultValue = "0"),
        @UpnpStateVariable(
                name = "CurrentTrack",
                sendEvents = false,
                datatype = "ui4",
                defaultValue = "0"),
        @UpnpStateVariable(
                name = "CurrentTrackDuration",
                sendEvents = false,
                datatype = "string"), // H+:MM:SS[.F+] or H+:MM:SS[.F0/F1]
        @UpnpStateVariable(
                name = "CurrentMediaDuration",
                sendEvents = false,
                datatype = "string",
                defaultValue = "00:00:00"),
        @UpnpStateVariable(
                name = "CurrentTrackMetaData",
                sendEvents = false,
                datatype = "string",
                defaultValue = "NOT_IMPLEMENTED"),
        @UpnpStateVariable(
                name = "CurrentTrackURI",
                sendEvents = false,
                datatype = "string"),
        @UpnpStateVariable(
                name = "AVTransportURI",
                sendEvents = false,
                datatype = "string"),
        @UpnpStateVariable(
                name = "AVTransportURIMetaData",
                sendEvents = false,
                datatype = "string",
                defaultValue = "NOT_IMPLEMENTED"),
        @UpnpStateVariable(
                name = "NextAVTransportURI",
                sendEvents = false,
                datatype = "string",
                defaultValue = "NOT_IMPLEMENTED"),
        @UpnpStateVariable(
                name = "NextAVTransportURIMetaData",
                sendEvents = false,
                datatype = "string",
                defaultValue = "NOT_IMPLEMENTED"),
        @UpnpStateVariable(
                name = "RelativeTimePosition",
                sendEvents = false,
                datatype = "string"), // H+:MM:SS[.F+] or H+:MM:SS[.F0/F1] (in track)
        @UpnpStateVariable(
                name = "AbsoluteTimePosition",
                sendEvents = false,
                datatype = "string"), // H+:MM:SS[.F+] or H+:MM:SS[.F0/F1] (in media)
        @UpnpStateVariable(
                name = "RelativeCounterPosition",
                sendEvents = false,
                datatype = "i4",
                defaultValue = "2147483647"), // Max value means not implemented
        @UpnpStateVariable(
                name = "AbsoluteCounterPosition",
                sendEvents = false,
                datatype = "i4",
                defaultValue = "2147483647"), // Max value means not implemented
        @UpnpStateVariable(
                name = "CurrentTransportActions",
                sendEvents = false,
                datatype = "string"), // Play, Stop, Pause, Seek, Next, Previous and Record
        @UpnpStateVariable(
                name = "A_ARG_TYPE_SeekMode",
                sendEvents = false,
                allowedValuesEnum = SeekMode.class), // The 'type' of seek we can perform (or should perform)
        @UpnpStateVariable(
                name = "A_ARG_TYPE_SeekTarget",
                sendEvents = false,
                datatype = "string"), // The actual seek (offset or whatever) value
        @UpnpStateVariable(
                name = "A_ARG_TYPE_InstanceID",
                sendEvents = false,
                datatype = "ui4"),
        @UpnpStateVariable(
                name = "Lyric",
                sendEvents = false,
                datatype = "string"),
        @UpnpStateVariable(
                name = "TerraceType",
                sendEvents = false,
                datatype = "string"),
        @UpnpStateVariable(
                name = "LoopMode",
                sendEvents = false,
                datatype = "string"),
        @UpnpStateVariable(
                name = "CurrentVolume",
                sendEvents = false,
                datatype = "string"),
        @UpnpStateVariable(
                name = "SlaveList",
                sendEvents = false,
                datatype = "string"),
        @UpnpStateVariable(
                name = "TrackSource",
                sendEvents = false,
                datatype = "string"),
        @UpnpStateVariable(
                name = "InternetAccess",
                sendEvents = false,
                datatype = "string"),
        @UpnpStateVariable(
                name = "VerUpdateFlag",
                sendEvents = false,
                datatype = "string"),
        @UpnpStateVariable(
                name = "VerUpdateStatus",
                sendEvents = false,
                datatype = "string"),
        @UpnpStateVariable(
                name = "BatteryFlag",
                sendEvents = false,
                datatype = "string"),
        @UpnpStateVariable(
                name = "BatteryPercent",
                sendEvents = false,
                datatype = "string"),
        @UpnpStateVariable(
                name = "AlarmFlag",
                sendEvents = false,
                datatype = "string"),
        @UpnpStateVariable(
                name = "TimeStamp",
                sendEvents = false,
                datatype = "string"),
        @UpnpStateVariable(
                name = "SubNum",
                sendEvents = false,
                datatype = "string"),
})
public abstract class AbstractAVTransportService implements LastChangeDelegator {

    @UpnpStateVariable(eventMaximumRateMilliseconds = 200)
    final private LastChange lastChange;
    final protected PropertyChangeSupport propertyChangeSupport;

    protected AbstractAVTransportService() {
        this.propertyChangeSupport = new PropertyChangeSupport(this);
        this.lastChange = new LastChange(new AVTransportLastChangeParser());
    }

    protected AbstractAVTransportService(LastChange lastChange) {
        this.propertyChangeSupport = new PropertyChangeSupport(this);
        this.lastChange = lastChange;
    }

    protected AbstractAVTransportService(PropertyChangeSupport propertyChangeSupport) {
        this.propertyChangeSupport = propertyChangeSupport;
        this.lastChange = new LastChange(new AVTransportLastChangeParser());
    }

    protected AbstractAVTransportService(PropertyChangeSupport propertyChangeSupport, LastChange lastChange) {
        this.propertyChangeSupport = propertyChangeSupport;
        this.lastChange = lastChange;
    }

    @Override
    public LastChange getLastChange() {
        return lastChange;
    }

    @Override
    public void appendCurrentState(LastChange lc, UnsignedIntegerFourBytes instanceId) throws Exception {

        MediaInfo mediaInfo = getMediaInfo(instanceId);
        TransportInfo transportInfo = getTransportInfo(instanceId);
        TransportSettings transportSettings = getTransportSettings(instanceId);
        PositionInfo positionInfo = getPositionInfo(instanceId);
        DeviceCapabilities deviceCaps = getDeviceCapabilities(instanceId);

        lc.setEventedValue(
                instanceId,
                new AVTransportVariable.AVTransportURI(URI.create(mediaInfo.getCurrentURI())),
                new AVTransportVariable.AVTransportURIMetaData(mediaInfo.getCurrentURIMetaData()),
                new AVTransportVariable.CurrentMediaDuration(mediaInfo.getMediaDuration()),
                new AVTransportVariable.CurrentPlayMode(transportSettings.getPlayMode()),
                new AVTransportVariable.CurrentRecordQualityMode(transportSettings.getRecQualityMode()),
                new AVTransportVariable.CurrentTrack(positionInfo.getTrack()),
                new AVTransportVariable.CurrentTrackDuration(positionInfo.getTrackDuration()),
                new AVTransportVariable.CurrentTrackMetaData(positionInfo.getTrackMetaData()),
                new AVTransportVariable.CurrentTrackURI(URI.create(positionInfo.getTrackURI())),
                new AVTransportVariable.CurrentTransportActions(getCurrentTransportActions(instanceId)),
                new AVTransportVariable.NextAVTransportURI(URI.create(mediaInfo.getNextURI())),
                new AVTransportVariable.NextAVTransportURIMetaData(mediaInfo.getNextURIMetaData()),
                new AVTransportVariable.NumberOfTracks(mediaInfo.getNumberOfTracks()),
                new AVTransportVariable.PossiblePlaybackStorageMedia(deviceCaps.getPlayMedia()),
                new AVTransportVariable.PossibleRecordQualityModes(deviceCaps.getRecQualityModes()),
                new AVTransportVariable.PossibleRecordStorageMedia(deviceCaps.getRecMedia()),
                new AVTransportVariable.RecordMediumWriteStatus(mediaInfo.getWriteStatus()),
                new AVTransportVariable.RecordStorageMedium(mediaInfo.getRecordMedium()),
                new AVTransportVariable.TransportPlaySpeed(transportInfo.getCurrentSpeed()),
                new AVTransportVariable.TransportState(transportInfo.getCurrentTransportState()),
                new AVTransportVariable.TransportStatus(transportInfo.getCurrentTransportStatus())
        );
    }

    public PropertyChangeSupport getPropertyChangeSupport() {
        return propertyChangeSupport;
    }

    public static UnsignedIntegerFourBytes getDefaultInstanceID() {
        return new UnsignedIntegerFourBytes(0);
    }

    @UpnpAction
    public abstract void setAVTransportURI(@UpnpInputArgument(name = "InstanceID") UnsignedIntegerFourBytes instanceId,
                                           @UpnpInputArgument(name = "CurrentURI", stateVariable = "AVTransportURI") String currentURI,
                                           @UpnpInputArgument(name = "CurrentURIMetaData", stateVariable = "AVTransportURIMetaData") String currentURIMetaData)
            throws AVTransportException;

    @UpnpAction
    public abstract void setNextAVTransportURI(@UpnpInputArgument(name = "InstanceID") UnsignedIntegerFourBytes instanceId,
                                               @UpnpInputArgument(name = "NextURI", stateVariable = "AVTransportURI") String nextURI,
                                               @UpnpInputArgument(name = "NextURIMetaData", stateVariable = "AVTransportURIMetaData") String nextURIMetaData)
            throws AVTransportException;

    @UpnpAction(out = {
            @UpnpOutputArgument(name = "NrTracks", stateVariable = "NumberOfTracks", getterName = "getNumberOfTracks"),
            @UpnpOutputArgument(name = "MediaDuration", stateVariable = "CurrentMediaDuration", getterName = "getMediaDuration"),
            @UpnpOutputArgument(name = "CurrentURI", stateVariable = "AVTransportURI", getterName = "getCurrentURI"),
            @UpnpOutputArgument(name = "CurrentURIMetaData", stateVariable = "AVTransportURIMetaData", getterName = "getCurrentURIMetaData"),
            @UpnpOutputArgument(name = "NextURI", stateVariable = "NextAVTransportURI", getterName = "getNextURI"),
            @UpnpOutputArgument(name = "NextURIMetaData", stateVariable = "NextAVTransportURIMetaData", getterName = "getNextURIMetaData"),
            @UpnpOutputArgument(name = "PlayMedium", stateVariable = "PlaybackStorageMedium", getterName = "getPlayMedium"),
            @UpnpOutputArgument(name = "RecordMedium", stateVariable = "RecordStorageMedium", getterName = "getRecordMedium"),
            @UpnpOutputArgument(name = "WriteStatus", stateVariable = "RecordMediumWriteStatus", getterName = "getWriteStatus")
    })
    public abstract MediaInfo getMediaInfo(@UpnpInputArgument(name = "InstanceID") UnsignedIntegerFourBytes instanceId)
            throws AVTransportException;


    @UpnpAction(out = {
            @UpnpOutputArgument(name = "CurrentTransportState", stateVariable = "TransportState", getterName = "getCurrentTransportState"),
            @UpnpOutputArgument(name = "CurrentTransportStatus", stateVariable = "TransportStatus", getterName = "getCurrentTransportStatus"),
            @UpnpOutputArgument(name = "CurrentSpeed", stateVariable = "TransportPlaySpeed", getterName = "getCurrentSpeed"),
            @UpnpOutputArgument(name = "Track", stateVariable = "CurrentTrack", getterName = "getTrack"),
            @UpnpOutputArgument(name = "TrackDuration", stateVariable = "CurrentTrackDuration", getterName = "getTrackDuration"),
            @UpnpOutputArgument(name = "TrackMetaData", stateVariable = "CurrentTrackMetaData", getterName = "getTrackMetaData"),
            @UpnpOutputArgument(name = "TrackURI", stateVariable = "CurrentTrackURI", getterName = "getTrackURI"),
            @UpnpOutputArgument(name = "RelTime", stateVariable = "RelativeTimePosition", getterName = "getRelTime"),
            @UpnpOutputArgument(name = "AbsTime", stateVariable = "AbsoluteTimePosition", getterName = "getAbsTime"),
            @UpnpOutputArgument(name = "RelCount", stateVariable = "TimeStamp", getterName = "getRelCount"),
            @UpnpOutputArgument(name = "AbsCount", stateVariable = "TimeStamp", getterName = "getAbsCount"),
            @UpnpOutputArgument(name = "LoopMode", stateVariable = "LoopMode", getterName = "getLoopMode"),
            @UpnpOutputArgument(name = "CurrentVolume", stateVariable = "CurrentVolume", getterName = "getCurrentVolume"),
            @UpnpOutputArgument(name = "CurrentChannel", stateVariable = "CurrentVolume", getterName = "getCurrentChannel"),
            @UpnpOutputArgument(name = "SlaveList", stateVariable = "SlaveList", getterName = "getSlaveList"),
            @UpnpOutputArgument(name = "PlayMedium", stateVariable = "PlaybackStorageMedium", getterName = "getPlayMedium"),
            @UpnpOutputArgument(name = "TrackSource", stateVariable = "TrackSource", getterName = "getTrackSource"),
            @UpnpOutputArgument(name = "InternetAccess", stateVariable = "InternetAccess", getterName = "getInternetAccess"),
            @UpnpOutputArgument(name = "VerUpdateFlag", stateVariable = "VerUpdateFlag", getterName = "getVerUpdateFlag"),
            @UpnpOutputArgument(name = "VerUpdateStatus", stateVariable = "VerUpdateStatus", getterName = "getVerUpdateStatus"),
            @UpnpOutputArgument(name = "BatteryFlag", stateVariable = "BatteryFlag", getterName = "getBatteryFlag"),
            @UpnpOutputArgument(name = "BatteryPercent", stateVariable = "BatteryPercent", getterName = "getBatteryPercent"),
            @UpnpOutputArgument(name = "AlarmFlag", stateVariable = "AlarmFlag", getterName = "getAlarmFlag"),
            @UpnpOutputArgument(name = "TimeStamp", stateVariable = "TimeStamp", getterName = "getTimeStamp"),
            @UpnpOutputArgument(name = "SubNum", stateVariable = "SubNum", getterName = "getSubNum"),
    })
    public abstract InfoEx getInfoEx(@UpnpInputArgument(name = "InstanceID") UnsignedIntegerFourBytes instanceId)
            throws AVTransportException;

    @UpnpAction(out = {
            @UpnpOutputArgument(name = "CurrentTransportState", stateVariable = "TransportState", getterName = "getCurrentTransportState"),
            @UpnpOutputArgument(name = "CurrentTransportStatus", stateVariable = "TransportStatus", getterName = "getCurrentTransportStatus"),
            @UpnpOutputArgument(name = "CurrentSpeed", stateVariable = "TransportPlaySpeed", getterName = "getCurrentSpeed")
    })
    public abstract TransportInfo getTransportInfo(@UpnpInputArgument(name = "InstanceID") UnsignedIntegerFourBytes instanceId)
            throws AVTransportException;

    @UpnpAction(out = {
            @UpnpOutputArgument(name = "Track", stateVariable = "CurrentTrack", getterName = "getTrack"),
            @UpnpOutputArgument(name = "TrackDuration", stateVariable = "CurrentTrackDuration", getterName = "getTrackDuration"),
            @UpnpOutputArgument(name = "TrackMetaData", stateVariable = "CurrentTrackMetaData", getterName = "getTrackMetaData"),
            @UpnpOutputArgument(name = "TrackURI", stateVariable = "CurrentTrackURI", getterName = "getTrackURI"),
            @UpnpOutputArgument(name = "RelTime", stateVariable = "RelativeTimePosition", getterName = "getRelTime"),
            @UpnpOutputArgument(name = "AbsTime", stateVariable = "AbsoluteTimePosition", getterName = "getAbsTime"),
            @UpnpOutputArgument(name = "RelCount", stateVariable = "RelativeCounterPosition", getterName = "getRelCount"),
            @UpnpOutputArgument(name = "AbsCount", stateVariable = "AbsoluteCounterPosition", getterName = "getAbsCount")
    })
    public abstract PositionInfo getPositionInfo(@UpnpInputArgument(name = "InstanceID") UnsignedIntegerFourBytes instanceId)
            throws AVTransportException;

    @UpnpAction(out = {
            @UpnpOutputArgument(name = "PlayMedia", stateVariable = "PossiblePlaybackStorageMedia", getterName = "getPlayMediaString"),
            @UpnpOutputArgument(name = "RecMedia", stateVariable = "PossibleRecordStorageMedia", getterName = "getRecMediaString"),
            @UpnpOutputArgument(name = "RecQualityModes", stateVariable = "PossibleRecordQualityModes", getterName = "getRecQualityModesString")
    })
    public abstract DeviceCapabilities getDeviceCapabilities(@UpnpInputArgument(name = "InstanceID") UnsignedIntegerFourBytes instanceId)
            throws AVTransportException;

    @UpnpAction(out = {
            @UpnpOutputArgument(name = "PlayMode", stateVariable = "CurrentPlayMode", getterName = "getPlayMode"),
            @UpnpOutputArgument(name = "RecQualityMode", stateVariable = "CurrentRecordQualityMode", getterName = "getRecQualityMode")
    })
    public abstract TransportSettings getTransportSettings(@UpnpInputArgument(name = "InstanceID") UnsignedIntegerFourBytes instanceId)
            throws AVTransportException;

    @UpnpAction
    public abstract void stop(@UpnpInputArgument(name = "InstanceID") UnsignedIntegerFourBytes instanceId)
            throws AVTransportException;

    @UpnpAction
    public abstract void play(@UpnpInputArgument(name = "InstanceID") UnsignedIntegerFourBytes instanceId,
                              @UpnpInputArgument(name = "Speed", stateVariable = "TransportPlaySpeed") String speed)
            throws AVTransportException;

    @UpnpAction
    public abstract void pause(@UpnpInputArgument(name = "InstanceID") UnsignedIntegerFourBytes instanceId)
            throws AVTransportException;

    @UpnpAction
    public abstract void record(@UpnpInputArgument(name = "InstanceID") UnsignedIntegerFourBytes instanceId)
            throws AVTransportException;

    @UpnpAction
    public abstract void seek(@UpnpInputArgument(name = "InstanceID") UnsignedIntegerFourBytes instanceId,
                              @UpnpInputArgument(name = "Unit", stateVariable = "A_ARG_TYPE_SeekMode") String unit,
                              @UpnpInputArgument(name = "Target", stateVariable = "A_ARG_TYPE_SeekTarget") String target)
            throws AVTransportException;

    @UpnpAction
    public abstract void next(@UpnpInputArgument(name = "InstanceID") UnsignedIntegerFourBytes instanceId)
            throws AVTransportException;

    @UpnpAction
    public abstract void previous(@UpnpInputArgument(name = "InstanceID") UnsignedIntegerFourBytes instanceId)
            throws AVTransportException;

    @UpnpAction
    public abstract void setPlayMode(@UpnpInputArgument(name = "InstanceID") UnsignedIntegerFourBytes instanceId,
                                     @UpnpInputArgument(name = "NewPlayMode", stateVariable = "CurrentPlayMode") String newPlayMode)
            throws AVTransportException;

    @UpnpAction
    public abstract void setRecordQualityMode(@UpnpInputArgument(name = "InstanceID") UnsignedIntegerFourBytes instanceId,
                                              @UpnpInputArgument(name = "NewRecordQualityMode", stateVariable = "CurrentRecordQualityMode") String newRecordQualityMode)
            throws AVTransportException;

    @UpnpAction
    public abstract void setCurrentLyric(@UpnpInputArgument(name = "InstanceID") UnsignedIntegerFourBytes instanceId,
                                         @UpnpInputArgument(name = "Lyric") String lyric,
                                         @UpnpInputArgument(name = "TerraceType") String terraceType)
            throws AVTransportException;

    @UpnpAction(name = "GetCurrentTransportActions", out = @UpnpOutputArgument(name = "Actions", stateVariable = "CurrentTransportActions"))
    public String getCurrentTransportActionsString(@UpnpInputArgument(name = "InstanceID") UnsignedIntegerFourBytes instanceId)
            throws AVTransportException {
        try {
            return ModelUtil.toCommaSeparatedList(getCurrentTransportActions(instanceId));
        } catch (Exception ex) {
            return ""; // TODO: Empty string is not defined in spec but seems reasonable for no available action?
        }
    }

    protected abstract TransportAction[] getCurrentTransportActions(UnsignedIntegerFourBytes instanceId) throws Exception;

    @UpnpAction
    public abstract void playVideo(@UpnpInputArgument(name = "InstanceID") UnsignedIntegerFourBytes instanceId,
                              @UpnpInputArgument(name = "Speed", stateVariable = "TransportPlaySpeed") String speed)
            throws AVTransportException;

    @UpnpAction
    public abstract void pauseVideo(@UpnpInputArgument(name = "InstanceID") UnsignedIntegerFourBytes instanceId)
            throws AVTransportException;


    @UpnpAction
    public abstract void seekVideo(@UpnpInputArgument(name = "InstanceID") UnsignedIntegerFourBytes instanceId,
                              @UpnpInputArgument(name = "Unit", stateVariable = "A_ARG_TYPE_SeekMode") String unit,
                              @UpnpInputArgument(name = "Target", stateVariable = "A_ARG_TYPE_SeekTarget") String target)
            throws AVTransportException;

    @UpnpAction
    public abstract void nextVideo(@UpnpInputArgument(name = "InstanceID") UnsignedIntegerFourBytes instanceId)
            throws AVTransportException;

    @UpnpAction
    public abstract void previousVideo(@UpnpInputArgument(name = "InstanceID") UnsignedIntegerFourBytes instanceId)
            throws AVTransportException;

    @UpnpAction(out = {
            @UpnpOutputArgument(name = "NrTracks", stateVariable = "NumberOfTracks", getterName = "getNumberOfTracks"),
            @UpnpOutputArgument(name = "MediaDuration", stateVariable = "CurrentMediaDuration", getterName = "getMediaDuration"),
            @UpnpOutputArgument(name = "CurrentURI", stateVariable = "AVTransportURI", getterName = "getCurrentURI"),
            @UpnpOutputArgument(name = "CurrentURIMetaData", stateVariable = "AVTransportURIMetaData", getterName = "getCurrentURIMetaData"),
            @UpnpOutputArgument(name = "NextURI", stateVariable = "NextAVTransportURI", getterName = "getNextURI"),
            @UpnpOutputArgument(name = "NextURIMetaData", stateVariable = "NextAVTransportURIMetaData", getterName = "getNextURIMetaData"),
            @UpnpOutputArgument(name = "PlayMedium", stateVariable = "PlaybackStorageMedium", getterName = "getPlayMedium"),
            @UpnpOutputArgument(name = "RecordMedium", stateVariable = "RecordStorageMedium", getterName = "getRecordMedium"),
            @UpnpOutputArgument(name = "WriteStatus", stateVariable = "RecordMediumWriteStatus", getterName = "getWriteStatus")
    })
    public abstract MediaInfo getMediaVideoInfo(@UpnpInputArgument(name = "InstanceID") UnsignedIntegerFourBytes instanceId)
            throws AVTransportException;

    @UpnpAction(out = {
            @UpnpOutputArgument(name = "Track", stateVariable = "CurrentTrack", getterName = "getTrack"),
            @UpnpOutputArgument(name = "TrackDuration", stateVariable = "CurrentTrackDuration", getterName = "getTrackDuration"),
            @UpnpOutputArgument(name = "TrackMetaData", stateVariable = "CurrentTrackMetaData", getterName = "getTrackMetaData"),
            @UpnpOutputArgument(name = "TrackURI", stateVariable = "CurrentTrackURI", getterName = "getTrackURI"),
            @UpnpOutputArgument(name = "RelTime", stateVariable = "RelativeTimePosition", getterName = "getRelTime"),
            @UpnpOutputArgument(name = "AbsTime", stateVariable = "AbsoluteTimePosition", getterName = "getAbsTime"),
            @UpnpOutputArgument(name = "RelCount", stateVariable = "RelativeCounterPosition", getterName = "getRelCount"),
            @UpnpOutputArgument(name = "AbsCount", stateVariable = "AbsoluteCounterPosition", getterName = "getAbsCount")
    })
    public abstract PositionInfo getPositionVideoInfo(@UpnpInputArgument(name = "InstanceID") UnsignedIntegerFourBytes instanceId)
            throws AVTransportException;

}
