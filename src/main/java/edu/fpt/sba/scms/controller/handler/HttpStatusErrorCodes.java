package edu.fpt.sba.scms.controller.handler;

import edu.fpt.sba.scms.common.enums.ErrorCode;
import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * Lỗi 4xx mà Spring MVC tự ném (không có exception nghiệp vụ) -> {@link ErrorCode} <b>có thật trong enum</b>.
 * Trước đây handler trả {@code status.name()} (vd. {@code METHOD_NOT_ALLOWED}) — mã không tồn tại trong enum,
 * làm FE lệch khỏi hợp đồng {@code code} của các lỗi khác.
 */
final class HttpStatusErrorCodes {

    private static final Map<HttpStatus, ErrorCode> CODES = Map.of(
            HttpStatus.BAD_REQUEST, ErrorCode.INVALID_INPUT_PAYLOAD,
            HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_REQUIRED,
            HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED,
            HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
            HttpStatus.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED,
            HttpStatus.NOT_ACCEPTABLE, ErrorCode.NOT_ACCEPTABLE,
            HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA_TYPE);

    private HttpStatusErrorCodes() {
    }

    static ErrorCode of(HttpStatus status) {
        return CODES.getOrDefault(status, ErrorCode.INVALID_INPUT_PAYLOAD);
    }
}
