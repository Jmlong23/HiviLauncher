package com.ljm.audiotoollib.upnpserver.cling.support.igd.callback;

import com.ljm.audiotoollib.upnpserver.cling.controlpoint.ActionCallback;
import com.ljm.audiotoollib.upnpserver.cling.controlpoint.ControlPoint;
import com.ljm.audiotoollib.upnpserver.cling.model.action.ActionArgumentValue;
import com.ljm.audiotoollib.upnpserver.cling.model.action.ActionInvocation;
import com.ljm.audiotoollib.upnpserver.cling.model.meta.Service;
import com.ljm.audiotoollib.upnpserver.cling.model.types.UnsignedIntegerTwoBytes;
import com.ljm.audiotoollib.upnpserver.cling.support.model.PortMapping;

import java.util.Map;

public abstract class PortMappingEntryGet extends ActionCallback {

    public PortMappingEntryGet(Service service, long index) {
        this(service, null, index);
    }

    protected PortMappingEntryGet(Service service, ControlPoint controlPoint, long index) {
        super(new ActionInvocation(service.getAction("GetGenericPortMappingEntry")), controlPoint);

        getActionInvocation().setInput("NewPortMappingIndex", new UnsignedIntegerTwoBytes(index));
    }

    @Override
    public void success(ActionInvocation invocation) {

        Map<String, ActionArgumentValue<Service>> outputMap = invocation.getOutputMap();
        success(new PortMapping(outputMap));
    }

    protected abstract void success(PortMapping portMapping);
}