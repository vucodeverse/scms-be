package edu.fpt.sba.scms.common.exception;

import edu.fpt.sba.scms.common.enums.ErrorCode;

/** 401 INVALID_PIN. */
public class InvalidPinException extends UnauthorizedException {

    public InvalidPinException() {
        super(ErrorCode.INVALID_PIN);
    }
}
