package edu.fpt.sba.scms.common.exception;

import edu.fpt.sba.scms.common.enums.ErrorCode;

/** 401 ACCOUNT_LOCKED — sai quá số lần cho phép, tạm khóa. */
public class AccountLockedException extends UnauthorizedException {

    private final long retryAfterSeconds;

    public AccountLockedException(long retryAfterSeconds) {
        super(ErrorCode.ACCOUNT_LOCKED,
                "Tài khoản tạm khóa, vui lòng thử lại sau " + Math.max(1, retryAfterSeconds / 60) + " phút.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
