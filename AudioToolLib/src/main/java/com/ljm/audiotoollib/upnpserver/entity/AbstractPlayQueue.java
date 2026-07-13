package com.ljm.audiotoollib.upnpserver.entity;


import com.ljm.audiotoollib.upnpserver.cling.binding.annotations.UpnpAction;
import com.ljm.audiotoollib.upnpserver.cling.binding.annotations.UpnpInputArgument;
import com.ljm.audiotoollib.upnpserver.cling.binding.annotations.UpnpOutputArgument;
import com.ljm.audiotoollib.upnpserver.cling.binding.annotations.UpnpService;
import com.ljm.audiotoollib.upnpserver.cling.binding.annotations.UpnpServiceId;
import com.ljm.audiotoollib.upnpserver.cling.binding.annotations.UpnpServiceType;
import com.ljm.audiotoollib.upnpserver.cling.binding.annotations.UpnpStateVariable;
import com.ljm.audiotoollib.upnpserver.cling.binding.annotations.UpnpStateVariables;
import com.ljm.audiotoollib.upnpserver.cling.model.types.UnsignedIntegerFourBytes;
import com.ljm.audiotoollib.upnpserver.cling.support.lastchange.LastChange;
import com.ljm.audiotoollib.upnpserver.cling.support.lastchange.LastChangeDelegator;
import com.ljm.audiotoollib.upnpserver.cling.support.renderingcontrol.RenderingControlException;
import com.ljm.audiotoollib.upnpserver.cling.support.renderingcontrol.lastchange.RenderingControlLastChangeParser;

import java.beans.PropertyChangeSupport;

/**
 *
 */
@UpnpService(
        serviceId = @UpnpServiceId(namespace = "wiimu-com", value = "PlayQueue"),
        serviceType = @UpnpServiceType(value = "PlayQueue", version = 1, namespace = "schemas-wiimu-com"),
        stringConvertibleTypes = LastChange.class
)

@UpnpStateVariables({
        @UpnpStateVariable(
                name = "PresetNameList",
                sendEvents = false,
                datatype = "string"),
        @UpnpStateVariable(
                name = "QueueContext",
                sendEvents = false,
                datatype = "string"),
        @UpnpStateVariable(
                name = "QueueName",
                sendEvents = false,
                datatype = "string"),
        @UpnpStateVariable(
                name = "LoopMode",
                sendEvents = false,
                datatype = "ui4"),
        @UpnpStateVariable(
                name = "CurrentIndex",
                sendEvents = false,
                datatype = "ui4"),
})

public abstract class AbstractPlayQueue implements LastChangeDelegator {

    @UpnpStateVariable(eventMaximumRateMilliseconds = 200)
    final private LastChange lastChange;

    final protected PropertyChangeSupport propertyChangeSupport;

    protected AbstractPlayQueue() {
        this.propertyChangeSupport = new PropertyChangeSupport(this);
        this.lastChange = new LastChange(new RenderingControlLastChangeParser());
    }

    protected AbstractPlayQueue(LastChange lastChange) {
        this.propertyChangeSupport = new PropertyChangeSupport(this);
        this.lastChange = lastChange;
    }

    protected AbstractPlayQueue(PropertyChangeSupport propertyChangeSupport) {
        this.propertyChangeSupport = propertyChangeSupport;
        this.lastChange = new LastChange(new RenderingControlLastChangeParser());
    }

    protected AbstractPlayQueue(PropertyChangeSupport propertyChangeSupport, LastChange lastChange) {
        this.propertyChangeSupport = propertyChangeSupport;
        this.lastChange = lastChange;
    }

    @Override
    public LastChange getLastChange() {
        return lastChange;
    }

    @Override
    public void appendCurrentState(LastChange lc, UnsignedIntegerFourBytes instanceId) throws Exception {

    }

    public PropertyChangeSupport getPropertyChangeSupport() {
        return propertyChangeSupport;
    }

    public static UnsignedIntegerFourBytes getDefaultInstanceID() {
        return new UnsignedIntegerFourBytes(0);
    }

    @UpnpAction
    public abstract void CreateQueue(@UpnpInputArgument(name = "QueueContext") String queueContext) throws SWActionException;

    @UpnpAction
    public abstract void AppendTracksInQueue(@UpnpInputArgument(name = "QueueContext") String queueContext) throws SWActionException;

    @UpnpAction
    public abstract void SetQueueLoopMode(@UpnpInputArgument(name = "LoopMode") UnsignedIntegerFourBytes loopMode) throws SWActionException;


    @UpnpAction(out = @UpnpOutputArgument(name = "LoopMode", stateVariable = "LoopMode"))
    public abstract UnsignedIntegerFourBytes GetQueueLoopMode() throws SWActionException;

    @UpnpAction(out = @UpnpOutputArgument(name = "QueueContext", stateVariable = "QueueContext"))
    public abstract String BrowseQueue(@UpnpInputArgument(name = "QueueName") String queueName) throws SWActionException;

    @UpnpAction
    public abstract void RemoveTracksInQueue(@UpnpInputArgument(name = "QueueName") String queueName,
                                             @UpnpInputArgument(name = "RangStart", stateVariable = "CurrentIndex") UnsignedIntegerFourBytes start,
                                             @UpnpInputArgument(name = "RangEnd", stateVariable = "CurrentIndex") UnsignedIntegerFourBytes end) throws SWActionException;

    @UpnpAction
    public abstract void PlayQueueWithIndex(@UpnpInputArgument(name = "QueueName") String queueName,
                                            @UpnpInputArgument(name = "Index", stateVariable = "CurrentIndex") UnsignedIntegerFourBytes index) throws SWActionException;
}
