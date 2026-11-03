package edu.fpt.sba.scms.dto;

import edu.fpt.sba.scms.config.TraceContextFilter;
import edu.fpt.sba.scms.common.enums.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

/**
 * Điểm duy nhất dựng {@link ApiErrorResponse} từ handler exception / security handler.
 * Thay đổi cách dựng envelope lỗi (thêm trường, đổi traceId...) chỉ sửa ở đây.
 */
public final class ApiExceptionFactory {

    private ApiExceptionFactory() {
    }

    public static ResponseEntity<ApiErrorResponse> build(HttpStatus status, String code, String detailCode,
                                                        String message, HttpServletRequest request,
                                                        List<FieldErrorItem> fieldErrors) {
        return build(status, code, detailCode, message, request, fieldErrors, null);
    }

    public static ResponseEntity<ApiErrorResponse> build(HttpStatus status, String code, String detailCode,
                                                        String message, HttpServletRequest request,
                                                        List<FieldErrorItem> fieldErrors, HttpHeaders headers) {
        ApiErrorResponse body = ApiErrorResponse.of(status.value(), code, detailCode, message,
                request.getRequestURI(), traceIdOf(request), fieldErrors);
        return new ResponseEntity<>(body, headers == null ? new HttpHeaders() : headers, status);
    }

    public static ResponseEntity<ApiErrorResponse> of(HttpStatus status, ErrorCode errorCode,
                                                      HttpServletRequest request) {
        return build(status, errorCode.name(), null, errorCode.message(), request, null);
    }

    public static HttpHeaders retryAfter(long seconds) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(seconds));
        return headers;
    }

    /**
     * Trace id từ MDC; nếu lỗi xảy ra ngoài {@code TraceContextFilter} (vd. tầng filter container) thì lấy lại từ
     * header do filter ghi ra — để response lỗi <b>luôn</b> có mã đối soát với log.
     */
    private static String traceIdOf(HttpServletRequest request) {
        String traceId = TraceContextFilter.currentTraceId();
        if (traceId != null) {
            return traceId;
        }
        return request.getHeader(TraceContextFilter.HEADER);
    }
}
