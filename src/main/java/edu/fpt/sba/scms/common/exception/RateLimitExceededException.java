package edu.fpt.sba.scms.common.exception;

import edu.fpt.sba.scms.common.enums.ErrorCode;
import org.springframework.http.HttpHeaders;

/**
 * 429 TOO_MANY_REQUESTS — gọi quá nhiều trong khoảng thời gian ngắn (rate limit), <b>không phải</b> tài khoản bị khóa.
 * Header {@code Retry-After} = {@link #getRetryAfterSeconds()}.
 * <p>
 * Khác {@link AccountLockedException} (401, sai thông tin đăng nhập quá nhiều lần): rate limit áp cho cả người
 * chưa xác định danh tính (vd. /auth/refresh với token hỏng) nên không thể mang ngữ nghĩa 401.
 */
public class RateLimitExceededException extends BaseException {

    private final long retryAfterSeconds;

    /** @param retryAfterSeconds số giây client phải chờ; {@code Retry-After} = giá trị này */
    public RateLimitExceededException(long retryAfterSeconds) {
        this(ErrorCode.TOO_MANY_REQUESTS, retryAfterSeconds);
    }

    public RateLimitExceededException(ErrorCode errorCode, long retryAfterSeconds) {
        super(errorCode, errorCode.message());
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    /**
     * @param retryAfterSeconds giây thực tế phải chờ ({@code Retry-After})
     * @param blockSeconds      độ dài cửa sổ chặn, chỉ để làm message "…sau N phút"
     */
    public RateLimitExceededException(long retryAfterSeconds, long blockSeconds) {
        this(ErrorCode.TOO_MANY_REQUESTS, retryAfterSeconds, blockSeconds);
    }

    /** Như trên nhưng cho ErrorCode 429 khác (vd. giới hạn riêng của một endpoint). */
    public RateLimitExceededException(ErrorCode errorCode, long retryAfterSeconds, long blockSeconds) {
        super(errorCode, "Bạn thao tác quá nhanh, vui lòng thử lại sau "
                + Math.max(1, Math.ceilDiv(Math.max(1, blockSeconds), 60)) + " phút.");
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
