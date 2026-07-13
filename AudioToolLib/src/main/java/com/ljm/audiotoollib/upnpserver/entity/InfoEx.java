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

package com.ljm.audiotoollib.upnpserver.entity;

import com.ljm.audiotoollib.upnpserver.cling.model.action.ActionArgumentValue;
import com.ljm.audiotoollib.upnpserver.cling.model.types.UnsignedIntegerFourBytes;
import com.ljm.audiotoollib.upnpserver.cling.support.model.RecordMediumWriteStatus;
import com.ljm.audiotoollib.upnpserver.cling.support.model.StorageMedium;
import com.ljm.audiotoollib.upnpserver.cling.support.model.TransportState;
import com.ljm.audiotoollib.upnpserver.cling.support.model.TransportStatus;

import java.util.Map;

/**
 *
 */
public class InfoEx {
    private TransportState currentTransportState = TransportState.STOPPED;
    private TransportStatus currentTransportStatus = TransportStatus.OK;
    private UnsignedIntegerFourBytes track = new UnsignedIntegerFourBytes(0);
    private String currentSpeed = "NOT_IMPLEMENTED";
    private String trackDuration = "00:00:00";
    private String trackMetaData = "";
    private String trackURI = "";
    private String relTime = "00:00:00";
    private String absTime = "";
    private String relCount = "";
    private String absCount = "";
    private String loopMode = "";
    private String currentVolume = "";
    private String currentChannel = "";
    private String slaveList = "";
    private StorageMedium playMedium = StorageMedium.NOT_IMPLEMENTED;
    private String trackSource = "NONE";
    private String internetAccess = "";
    private String verUpdateFlag = "";
    private String verUpdateStatus = "";
    private String batteryFlag = "0";
    private String batteryPercent = "";
    private String alarmFlag = "";
    private String timeStamp = "";
    private String subNum = "";

    public InfoEx() {

    }

    // Getter和Setter方法
    public TransportState getCurrentTransportState() {
        return currentTransportState;
    }

    public void setCurrentTransportState(TransportState currentTransportState) {
        this.currentTransportState = currentTransportState;
    }

    public TransportStatus getCurrentTransportStatus() {
        return currentTransportStatus;
    }

    public void setCurrentTransportStatus(TransportStatus currentTransportStatus) {
        this.currentTransportStatus = currentTransportStatus;
    }

    public UnsignedIntegerFourBytes getTrack() {
        return track;
    }

    public void setTrack(UnsignedIntegerFourBytes track) {
        this.track = track;
    }

    public String getCurrentSpeed() {
        return currentSpeed;
    }

    public void setCurrentSpeed(String currentSpeed) {
        this.currentSpeed = currentSpeed;
    }

    public String getTrackDuration() {
        return trackDuration;
    }

    public void setTrackDuration(String trackDuration) {
        this.trackDuration = trackDuration;
    }

    public String getTrackMetaData() {
        return trackMetaData;
    }

    public void setTrackMetaData(String trackMetaData) {
        this.trackMetaData = trackMetaData;
    }

    public String getTrackURI() {
        return trackURI;
    }

    public void setTrackURI(String trackURI) {
        this.trackURI = trackURI;
    }

    public String getRelTime() {
        return relTime;
    }

    public void setRelTime(String relTime) {
        this.relTime = relTime;
    }

    public String getAbsTime() {
        return absTime;
    }

    public void setAbsTime(String absTime) {
        this.absTime = absTime;
    }

    public String getRelCount() {
        return relCount;
    }

    public void setRelCount(String relCount) {
        this.relCount = relCount;
    }

    public String getAbsCount() {
        return absCount;
    }

    public void setAbsCount(String absCount) {
        this.absCount = absCount;
    }

    public String getLoopMode() {
        return loopMode;
    }

    public void setLoopMode(String loopMode) {
        this.loopMode = loopMode;
    }

    public String getCurrentVolume() {
        return currentVolume;
    }

    public void setCurrentVolume(String currentVolume) {
        this.currentVolume = currentVolume;
    }

    public String getCurrentChannel() {
        return currentChannel;
    }

    public void setCurrentChannel(String currentChannel) {
        this.currentChannel = currentChannel;
    }

    public String getSlaveList() {
        return slaveList;
    }

    public void setSlaveList(String slaveList) {
        this.slaveList = slaveList;
    }

    public StorageMedium getPlayMedium() {
        return playMedium;
    }

    public void setPlayMedium(StorageMedium playMedium) {
        this.playMedium = playMedium;
    }

    public String getTrackSource() {
        return trackSource;
    }

    public void setTrackSource(String trackSource) {
        this.trackSource = trackSource;
    }

    public String getInternetAccess() {
        return internetAccess;
    }

    public void setInternetAccess(String internetAccess) {
        this.internetAccess = internetAccess;
    }

    public String getVerUpdateFlag() {
        return verUpdateFlag;
    }

    public void setVerUpdateFlag(String verUpdateFlag) {
        this.verUpdateFlag = verUpdateFlag;
    }

    public String getVerUpdateStatus() {
        return verUpdateStatus;
    }

    public void setVerUpdateStatus(String verUpdateStatus) {
        this.verUpdateStatus = verUpdateStatus;
    }

    public String getBatteryFlag() {
        return batteryFlag;
    }

    public void setBatteryFlag(String batteryFlag) {
        this.batteryFlag = batteryFlag;
    }

    public String getBatteryPercent() {
        return batteryPercent;
    }

    public void setBatteryPercent(String batteryPercent) {
        this.batteryPercent = batteryPercent;
    }

    public String getAlarmFlag() {
        return alarmFlag;
    }

    public void setAlarmFlag(String alarmFlag) {
        this.alarmFlag = alarmFlag;
    }

    public String getTimeStamp() {
        return timeStamp;
    }

    public void setTimeStamp(String timeStamp) {
        this.timeStamp = timeStamp;
    }

    public String getSubNum() {
        return subNum;
    }

    public void setSubNum(String subNum) {
        this.subNum = subNum;
    }
}
