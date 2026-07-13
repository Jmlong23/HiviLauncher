
package com.ljm.audiotoollib.upnpserver.entity;

import com.ljm.audiotoollib.upnpserver.cling.model.action.ActionException;
import com.ljm.audiotoollib.upnpserver.cling.model.types.ErrorCode;
import com.ljm.audiotoollib.upnpserver.cling.support.renderingcontrol.RenderingControlErrorCode;

/**
 *
 */
public class SWActionException extends ActionException {

    public SWActionException(int errorCode, String message) {
        super(errorCode, message);
    }

    public SWActionException(int errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    public SWActionException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public SWActionException(ErrorCode errorCode) {
        super(errorCode);
    }

    public SWActionException(RenderingControlErrorCode errorCode, String message) {
        super(errorCode.getCode(), errorCode.getDescription() + ". " + message + ".");
    }

    public SWActionException(RenderingControlErrorCode errorCode) {
        super(errorCode.getCode(), errorCode.getDescription());
    }
}