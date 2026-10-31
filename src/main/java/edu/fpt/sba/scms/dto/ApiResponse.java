package edu.fpt.sba.scms.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Envelope thành công (IMPLEMENTATION.md §8.1). FE đọc {@code data}; {@code traceId} để đối soát log.
 */
@Schema(description = "Envelope trả về thành công")
public record ApiResponse<T>(
        boolean success,
        int status,
        String code,
        String message,
        T data,
        Instant timestamp,
        String traceId) {

    public static <T> ApiResponse<T> ok(T data, String traceId) {
        return new ApiResponse<>(true, 200, "SUCCESS", "OK", data, Instant.now(), traceId);
    }
}
