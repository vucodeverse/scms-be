package edu.fpt.sba.scms.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import edu.fpt.sba.scms.common.enums.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;

/**
 * Envelope lỗi (IMPLEMENTATION.md §8.2). Dùng factory {@link #of(HttpStatus, ErrorCode, ...)} ở MỌI nơi
 * (GlobalExceptionHandler, Security entry point/handler) để một chỗ duy nhất quyết định cách dựng.
 */
@Schema(description = "Envelope trả về lỗi")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        boolean success,
        int status,
        @Schema(example = "INVALID_CREDENTIALS") String errorCode,
        @Schema(description = "Mã con khi errorCode = BUSINESS_RULE_VIOLATION", example = "ORDER_FROZEN")
        String detailCode,
        @Schema(example = "Tên đăng nhập hoặc mật khẩu không đúng.") String message,
        @Schema(example = "/api/v1/auth/login") String path,
        Instant timestamp,
        String traceId,
        List<FieldErrorItem> fieldErrors) {

    public ApiErrorResponse {
        fieldErrors = (fieldErrors == null || fieldErrors.isEmpty()) ? null : List.copyOf(fieldErrors);
    }

    public static ApiErrorResponse of(int status, String errorCode, String detailCode, String message,
                                      String path, String traceId, List<FieldErrorItem> fieldErrors) {
        return new ApiErrorResponse(false, status, errorCode, detailCode, message, path,
                Instant.now(), traceId, fieldErrors);
    }

    /** Factory chính: code lỗi 1-1 với HTTP status, message mặc định của {@link ErrorCode}. */
    public static ApiErrorResponse of(HttpStatus status, ErrorCode errorCode, String path, String traceId,
                                      List<FieldErrorItem> fieldErrors) {
        return of(status.value(), errorCode.name(), null, errorCode.message(), path, traceId, fieldErrors);
    }
}
