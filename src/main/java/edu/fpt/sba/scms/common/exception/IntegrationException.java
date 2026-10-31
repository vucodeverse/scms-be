package edu.fpt.sba.scms.common.exception;

import edu.fpt.sba.scms.common.enums.ErrorCode;

/** 503 — SePay / VietQR / printer không phản hồi. */
public class IntegrationException extends BaseException {

    public IntegrationException(String message) {
        super(ErrorCode.INTEGRATION_UNAVAILABLE, message);
    }

    public IntegrationException(String message, Throwable cause) {
        super(ErrorCode.INTEGRATION_UNAVAILABLE, message, cause);
    }
}
