package edu.fpt.sba.scms.common.exception;

import edu.fpt.sba.scms.common.enums.ErrorCode;
import org.springframework.http.HttpHeaders;

/**
 * 401 PIN_LOCKED — nhập sai mã PIN vượt ngưỡng cho phép, tạm khóa theo thời gian cấu hình.
 */
public class PinLockedException extends UnauthorizedException {

    private final long retryAfterSeconds;

    public PinLockedException(long retryAfterSeconds, String message) {
        super(ErrorCode.PIN_LOCKED, message);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    @Override
    public HttpHeaders httpHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        return headers;
    }
}
