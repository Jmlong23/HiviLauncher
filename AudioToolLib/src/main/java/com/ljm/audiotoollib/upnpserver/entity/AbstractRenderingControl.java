package com.ljm.audiotoollib.upnpserver.entity;


import android.util.Log;

import com.ljm.audiotoollib.upnpserver.cling.binding.annotations.UpnpAction;
import com.ljm.audiotoollib.upnpserver.cling.binding.annotations.UpnpInputArgument;
import com.ljm.audiotoollib.upnpserver.cling.binding.annotations.UpnpOutputArgument;
import com.ljm.audiotoollib.upnpserver.cling.binding.annotations.UpnpService;
import com.ljm.audiotoollib.upnpserver.cling.binding.annotations.UpnpServiceId;
import com.ljm.audiotoollib.upnpserver.cling.binding.annotations.UpnpServiceType;
import com.ljm.audiotoollib.upnpserver.cling.binding.annotations.UpnpStateVariable;
import com.ljm.audiotoollib.upnpserver.cling.binding.annotations.UpnpStateVariables;
import com.ljm.audiotoollib.upnpserver.cling.model.types.ErrorCode;
import com.ljm.audiotoollib.upnpserver.cling.model.types.UnsignedIntegerFourBytes;
import com.ljm.audiotoollib.upnpserver.cling.model.types.UnsignedIntegerTwoBytes;
import com.ljm.audiotoollib.upnpserver.cling.support.lastchange.LastChange;
import com.ljm.audiotoollib.upnpserver.cling.support.lastchange.LastChangeDelegator;
import com.ljm.audiotoollib.upnpserver.cling.support.model.Channel;
import com.ljm.audiotoollib.upnpserver.cling.support.model.PresetName;
import com.ljm.audiotoollib.upnpserver.cling.support.model.VolumeDBRange;
import com.ljm.audiotoollib.upnpserver.cling.support.renderingcontrol.RenderingControlException;
import com.ljm.audiotoollib.upnpserver.cling.support.renderingcontrol.lastchange.ChannelLoudness;
import com.ljm.audiotoollib.upnpserver.cling.support.renderingcontrol.lastchange.ChannelMute;
import com.ljm.audiotoollib.upnpserver.cling.support.renderingcontrol.lastchange.ChannelVolume;
import com.ljm.audiotoollib.upnpserver.cling.support.renderingcontrol.lastchange.ChannelVolumeDB;
import com.ljm.audiotoollib.upnpserver.cling.support.renderingcontrol.lastchange.RenderingControlLastChangeParser;
import com.ljm.audiotoollib.upnpserver.cling.support.renderingcontrol.lastchange.RenderingControlVariable;

import java.beans.PropertyChangeSupport;

/**
 *
 */
@UpnpService(
        serviceId = @UpnpServiceId("RenderingControl"),
        serviceType = @UpnpServiceType(value = "RenderingControl", version = 1),
        stringConvertibleTypes = LastChange.class
)
@UpnpStateVariables({
        @UpnpStateVariable(
                name = "PresetNameList",
                sendEvents = false,
                datatype = "string"),
        @UpnpStateVariable(
                name = "Mute",
                sendEvents = false,
                datatype = "boolean"),
        @UpnpStateVariable(
                name = "Volume",
                sendEvents = false,
                datatype = "ui2",
                allowedValueMinimum = 0,
                allowedValueMaximum = 100),
        @UpnpStateVariable(
                name = "VolumeDB",
                sendEvents = false,
                datatype = "i2",
                allowedValueMinimum = -36864,
                allowedValueMaximum = 32767),
        @UpnpStateVariable(
                name = "Loudness",
                sendEvents = false,
                datatype = "boolean"),
        @UpnpStateVariable(
                name = "A_ARG_TYPE_Channel",
                sendEvents = false,
                allowedValuesEnum = Channel.class),
        @UpnpStateVariable(
                name = "A_ARG_TYPE_PresetName",
                sendEvents = false,
                allowedValuesEnum = PresetName.class),
        @UpnpStateVariable(
                name = "A_ARG_TYPE_InstanceID",
                sendEvents = false,
                datatype = "ui4"),
        @UpnpStateVariable(
                name = "MultiRoomType",
                sendEvents = false,
                datatype = "string"),
        @UpnpStateVariable(
                name = "Router",
                sendEvents = false,
                datatype = "string"),
        @UpnpStateVariable(
                name = "Ssid",
                sendEvents = false,
                datatype = "string"),
        @UpnpStateVariable(
                name = "SlaveMask",
                sendEvents = false,
                datatype = "string"),
        @UpnpStateVariable(
                name = "CurrentVolume",
                sendEvents = false,
                datatype = "string"),
        @UpnpStateVariable(
                name = "CurrentMute",
                sendEvents = false,
                datatype = "string"),
        @UpnpStateVariable(
                name = "Channel",
                sendEvents = false,
                datatype = "string"),
        @UpnpStateVariable(
                name = "SlaveList",
                sendEvents = false,
                datatype = "string"),
        @UpnpStateVariable(
                name = "Status",
                sendEvents = false,
                datatype = "string"),
        @UpnpStateVariable(
                name = "AudioContext",
                sendEvents = false,
                datatype = "string"),
        @UpnpStateVariable(
                name = "ControlMode",
                sendEvents = false,
                datatype = "ui2"),
        @UpnpStateVariable(
                name = "ExtraInfo",
                sendEvents = false,
                datatype = "string"),
        @UpnpStateVariable(
                name = "AudioMode",
                sendEvents = false,
                datatype = "string"),

})
public abstract class AbstractRenderingControl implements LastChangeDelegator {

    @UpnpStateVariable(eventMaximumRateMilliseconds = 200)
    final private LastChange lastChange;

    final protected PropertyChangeSupport propertyChangeSupport;

    protected AbstractRenderingControl() {
        this.propertyChangeSupport = new PropertyChangeSupport(this);
        this.lastChange = new LastChange(new RenderingControlLastChangeParser());
    }

    protected AbstractRenderingControl(LastChange lastChange) {
        this.propertyChangeSupport = new PropertyChangeSupport(this);
        this.lastChange = lastChange;
    }

    protected AbstractRenderingControl(PropertyChangeSupport propertyChangeSupport) {
        this.propertyChangeSupport = propertyChangeSupport;
        this.lastChange = new LastChange(new RenderingControlLastChangeParser());
    }

    protected AbstractRenderingControl(PropertyChangeSupport propertyChangeSupport, LastChange lastChange) {
        this.propertyChangeSupport = propertyChangeSupport;
        this.lastChange = lastChange;
    }

    @Override
    public LastChange getLastChange() {
        return lastChange;
    }

    @Override
    public void appendCurrentState(LastChange lc, UnsignedIntegerFourBytes instanceId) throws Exception {
        for (Channel channel : getCurrentChannels()) {
            String channelString = channel.name();
            lc.setEventedValue(
                    instanceId,
                    new RenderingControlVariable.Mute(new ChannelMute(channel, getMute(instanceId, channelString))),
                    new RenderingControlVariable.Loudness(new ChannelLoudness(channel, getLoudness(instanceId, channelString))),
                    new RenderingControlVariable.Volume(new ChannelVolume(channel, getVolume(instanceId, channelString).getValue().intValue())),
                    new RenderingControlVariable.VolumeDB(new ChannelVolumeDB(channel, getVolumeDB(instanceId, channelString))),
                    new RenderingControlVariable.PresetNameList(PresetName.FactoryDefaults.name())
            );
        }
    }

    public PropertyChangeSupport getPropertyChangeSupport() {
        return propertyChangeSupport;
    }

    public static UnsignedIntegerFourBytes getDefaultInstanceID() {
        return new UnsignedIntegerFourBytes(0);
    }

    @UpnpAction(out = @UpnpOutputArgument(name = "CurrentPresetNameList", stateVariable = "PresetNameList"))
    public String listPresets(@UpnpInputArgument(name = "InstanceID") UnsignedIntegerFourBytes instanceId) throws RenderingControlException {
        return PresetName.FactoryDefaults.toString();
    }

    @UpnpAction
    public void selectPreset(@UpnpInputArgument(name = "InstanceID") UnsignedIntegerFourBytes instanceId,
                             @UpnpInputArgument(name = "PresetName") String presetName) throws RenderingControlException {
    }

    @UpnpAction(out = @UpnpOutputArgument(name = "CurrentMute", stateVariable = "Mute"))
    public abstract boolean getMute(@UpnpInputArgument(name = "InstanceID") UnsignedIntegerFourBytes instanceId,
                                    @UpnpInputArgument(name = "Channel") String channelName) throws RenderingControlException;

    @UpnpAction
    public abstract void setMute(@UpnpInputArgument(name = "InstanceID") UnsignedIntegerFourBytes instanceId,
                                 @UpnpInputArgument(name = "Channel") String channelName,
                                 @UpnpInputArgument(name = "DesiredMute", stateVariable = "Mute") boolean desiredMute) throws RenderingControlException;

    @UpnpAction(out = @UpnpOutputArgument(name = "CurrentVolume", stateVariable = "Volume"))
    public abstract UnsignedIntegerTwoBytes getVolume(@UpnpInputArgument(name = "InstanceID") UnsignedIntegerFourBytes instanceId,
                                                      @UpnpInputArgument(name = "Channel") String channelName) throws RenderingControlException;

    @UpnpAction
    public abstract void setVolume(@UpnpInputArgument(name = "InstanceID") UnsignedIntegerFourBytes instanceId,
                                   @UpnpInputArgument(name = "Channel") String channelName,
                                   @UpnpInputArgument(name = "DesiredVolume", stateVariable = "Volume") UnsignedIntegerTwoBytes desiredVolume) throws RenderingControlException;

    @UpnpAction(out = @UpnpOutputArgument(name = "CurrentAudioMode", stateVariable = "AudioMode"))
    public abstract String getAudioMode(@UpnpInputArgument(name = "InstanceID") UnsignedIntegerFourBytes instanceId)
            throws RenderingControlException;

    @UpnpAction
    public abstract void setAudioMode(@UpnpInputArgument(name = "InstanceID") UnsignedIntegerFourBytes instanceId,
                                      @UpnpInputArgument(name = "DesiredAudioMode", stateVariable = "AudioMode") String desiredAudioMode)
            throws RenderingControlException;

    @UpnpAction(out = @UpnpOutputArgument(name = "CurrentVolume", stateVariable = "VolumeDB"))
    public Integer getVolumeDB(@UpnpInputArgument(name = "InstanceID") UnsignedIntegerFourBytes instanceId,
                               @UpnpInputArgument(name = "Channel") String channelName) throws RenderingControlException {
        return 0;
    }

    @UpnpAction
    public void setVolumeDB(@UpnpInputArgument(name = "InstanceID") UnsignedIntegerFourBytes instanceId,
                            @UpnpInputArgument(name = "Channel") String channelName,
                            @UpnpInputArgument(name = "DesiredVolume", stateVariable = "VolumeDB") Integer desiredVolumeDB) throws RenderingControlException {
        /*
        VolumeDB volumeDB = new VolumeDB();
        volumeDB.setChannel(channelName);
        volumeDB.setVal(new BigInteger(desiredVolumeDB.toString()));
        */
    }



    @UpnpAction(out = {
            @UpnpOutputArgument(name = "MinValue", stateVariable = "VolumeDB", getterName = "getMinValue"),
            @UpnpOutputArgument(name = "MaxValue", stateVariable = "VolumeDB", getterName = "getMaxValue")
    })
    public VolumeDBRange getVolumeDBRange(@UpnpInputArgument(name = "InstanceID") UnsignedIntegerFourBytes instanceId,
                                          @UpnpInputArgument(name = "Channel") String channelName) throws RenderingControlException {
        return new VolumeDBRange(0, 0);
    }

    @UpnpAction(out = @UpnpOutputArgument(name = "CurrentLoudness", stateVariable = "Loudness"))
    public boolean getLoudness(@UpnpInputArgument(name = "InstanceID") UnsignedIntegerFourBytes instanceId,
                               @UpnpInputArgument(name = "Channel") String channelName) throws RenderingControlException {
        return false;
    }

    @UpnpAction
    public void setLoudness(@UpnpInputArgument(name = "InstanceID") UnsignedIntegerFourBytes instanceId,
                            @UpnpInputArgument(name = "Channel") String channelName,
                            @UpnpInputArgument(name = "DesiredLoudness", stateVariable = "Loudness") boolean desiredLoudness) throws RenderingControlException {
/*
        Loudness loudness = new Loudness();
        loudness.setChannel(channelName);
        loudness.setVal(desiredLoudness);
*/
    }

    @UpnpAction(out = {
            @UpnpOutputArgument(name = "MultiType", stateVariable = "MultiRoomType", getterName = "getMultiType"),
            @UpnpOutputArgument(name = "Router", stateVariable = "Router", getterName = "getRouter"),
            @UpnpOutputArgument(name = "Ssid", stateVariable = "Ssid", getterName = "getSsid"),
            @UpnpOutputArgument(name = "SlaveMask", stateVariable = "SlaveMask", getterName = "getSlaveMask"),
            @UpnpOutputArgument(name = "CurrentVolume", stateVariable = "CurrentVolume", getterName = "getVolume"),
            @UpnpOutputArgument(name = "CurrentMute", stateVariable = "CurrentMute", getterName = "getMute"),
            @UpnpOutputArgument(name = "CurrentChannel", stateVariable = "Channel", getterName = "getChannel"),
            @UpnpOutputArgument(name = "SlaveList", stateVariable = "SlaveList", getterName = "getSlaveList"),
            @UpnpOutputArgument(name = "Status", stateVariable = "Status", getterName = "getStatus"),
    })
    public GetControlDeviceInfoEntity GetControlDeviceInfo(@UpnpInputArgument(name = "InstanceID") UnsignedIntegerFourBytes instanceId)
            throws RenderingControlException {
        return new GetControlDeviceInfoEntity();
    }

    @UpnpAction
    public abstract void setRemoteControlMode(@UpnpInputArgument(name = "InstanceID") UnsignedIntegerFourBytes instanceId,
                                              @UpnpInputArgument(name = "ControlMode") UnsignedIntegerTwoBytes controlMode) throws RenderingControlException;

    @UpnpAction
    public abstract void setAudioBackground(@UpnpInputArgument(name = "InstanceID") UnsignedIntegerFourBytes instanceId,
                                            @UpnpInputArgument(name = "AudioContext") String audioContext) throws RenderingControlException;

    @UpnpAction
    public abstract void selectAudioBackground(@UpnpInputArgument(name = "InstanceID") UnsignedIntegerFourBytes instanceId,
                                               @UpnpInputArgument(name = "AudioContext") String audioContext) throws RenderingControlException;

    @UpnpAction
    public abstract void cancelAudioBackground(@UpnpInputArgument(name = "InstanceID") UnsignedIntegerFourBytes instanceId) throws RenderingControlException;

    protected abstract Channel[] getCurrentChannels();

    protected Channel getChannel(String channelName) throws RenderingControlException {
        try {
            return Channel.valueOf(channelName);
        } catch (IllegalArgumentException ex) {
            throw new RenderingControlException(ErrorCode.ARGUMENT_VALUE_INVALID, "Unsupported audio channel: " + channelName);
        }
    }

    @UpnpAction
    public abstract void setExtraInfo(@UpnpInputArgument(name = "InstanceID") UnsignedIntegerFourBytes instanceId,
                                      @UpnpInputArgument(name = "ExtraInfo") String extraInfo) throws RenderingControlException;

}
