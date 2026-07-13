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
import com.ljm.audiotoollib.upnpserver.cling.model.ServiceReference;
import com.ljm.audiotoollib.upnpserver.cling.model.action.ActionException;
import com.ljm.audiotoollib.upnpserver.cling.model.action.ActionInvocation;
import com.ljm.audiotoollib.upnpserver.cling.model.meta.Service;
import com.ljm.audiotoollib.upnpserver.cling.model.types.ErrorCode;
import com.ljm.audiotoollib.upnpserver.cling.support.model.ConnectionInfo;
import com.ljm.audiotoollib.upnpserver.cling.support.model.ProtocolInfo;

/**
 * @author Alessio Gaeta
 * @author Christian Bauer
 */
public abstract class GetCurrentConnectionInfo extends ActionCallback {

    public GetCurrentConnectionInfo(Service service, int connectionID) {
        this(service, null, connectionID);
    }

    protected GetCurrentConnectionInfo(Service service, ControlPoint controlPoint, int connectionID) {
        super(new ActionInvocation(service.getAction("GetCurrentConnectionInfo")), controlPoint);
		getActionInvocation().setInput("ConnectionID", connectionID);
	}

    @Override
    public void success(ActionInvocation invocation) {

        try {
            ConnectionInfo info = new ConnectionInfo(
                    (Integer)invocation.getInput("ConnectionID").getValue(),
                    (Integer)invocation.getOutput("RcsID").getValue(),
                    (Integer)invocation.getOutput("AVTransportID").getValue(),
                    new ProtocolInfo(invocation.getOutput("ProtocolInfo").toString()),
                    new ServiceReference(invocation.getOutput("PeerConnectionManager").toString()),
                    (Integer)invocation.getOutput("PeerConnectionID").getValue(),
                    ConnectionInfo.Direction.valueOf(invocation.getOutput("Direction").toString()),
                    ConnectionInfo.Status.valueOf(invocation.getOutput("Status").toString())
            );

            received(invocation, info);

        } catch (Exception ex) {
            invocation.setFailure(
                    new ActionException(ErrorCode.ACTION_FAILED, "Can't parse ConnectionInfo response: " + ex, ex)
            );
            failure(invocation, null);
        }
    }

    public abstract void received(ActionInvocation invocation, ConnectionInfo connectionInfo);

}
