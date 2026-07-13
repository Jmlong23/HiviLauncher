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

package com.ljm.audiotoollib.upnpserver.cling.support.connectionmanager.callback;

import com.ljm.audiotoollib.upnpserver.cling.controlpoint.ActionCallback;
import com.ljm.audiotoollib.upnpserver.cling.controlpoint.ControlPoint;
import com.ljm.audiotoollib.upnpserver.cling.model.action.ActionArgumentValue;
import com.ljm.audiotoollib.upnpserver.cling.model.action.ActionException;
import com.ljm.audiotoollib.upnpserver.cling.model.action.ActionInvocation;
import com.ljm.audiotoollib.upnpserver.cling.model.meta.Service;
import com.ljm.audiotoollib.upnpserver.cling.model.types.ErrorCode;
import com.ljm.audiotoollib.upnpserver.cling.support.model.ProtocolInfos;

/**
 * @author Christian Bauer
 */
public abstract class GetProtocolInfo extends ActionCallback {

    public GetProtocolInfo(Service service) {
        this(service, null);
    }

    protected GetProtocolInfo(Service service, ControlPoint controlPoint) {
        super(new ActionInvocation(service.getAction("GetProtocolInfo")), controlPoint);
    }

    @Override
    public void success(ActionInvocation invocation) {
        try {
            ActionArgumentValue sink = invocation.getOutput("Sink");
            ActionArgumentValue source = invocation.getOutput("Source");

            received(
                    invocation,
                    sink != null ? new ProtocolInfos(sink.toString()) : null,
                    source != null ? new ProtocolInfos(source.toString()) : null
            );

        } catch (Exception ex) {
            invocation.setFailure(
                    new ActionException(ErrorCode.ACTION_FAILED, "Can't parse ProtocolInfo response: " + ex, ex)
            );
            failure(invocation, null);
        }
    }

    public abstract void received(ActionInvocation actionInvocation, ProtocolInfos sinkProtocolInfos, ProtocolInfos sourceProtocolInfos);

}