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
package com.ljm.audiotoollib.upnpserver.cling.support.model.dlna.message.header;

import com.ljm.audiotoollib.upnpserver.cling.model.message.header.InvalidHeaderException;
import com.ljm.audiotoollib.upnpserver.cling.model.types.InvalidValueException;
import com.ljm.audiotoollib.upnpserver.cling.support.avtransport.lastchange.AVTransportVariable;

/**
 * @author Mario Franco
 */
public class PlaySpeedHeader extends DLNAHeader<AVTransportVariable.TransportPlaySpeed> {

    public PlaySpeedHeader() {
    }

    public PlaySpeedHeader(AVTransportVariable.TransportPlaySpeed speed) {
        setValue(speed);
    }

    @Override
    public void setString(String s) throws InvalidHeaderException {
        if (s.length() != 0) {
            try {
                AVTransportVariable.TransportPlaySpeed t = new AVTransportVariable.TransportPlaySpeed(s);
                setValue(t);
                return;
            } catch (InvalidValueException invalidValueException) {}
        }
        throw new InvalidHeaderException("Invalid PlaySpeed header value: " + s);
    }

    @Override
    public String getString() {
        return getValue().getValue();
    }
}
