package edu.fpt.sba.scms.common.exception;

import edu.fpt.sba.scms.common.enums.ErrorCode;

/** 403 — đã xác thực nhưng không đủ quyền / khác chi nhánh. */
public class ForbiddenException extends BaseException {

    public ForbiddenException() {
        this(ErrorCode.ACCESS_DENIED);
    }

    public ForbiddenException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ForbiddenException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
