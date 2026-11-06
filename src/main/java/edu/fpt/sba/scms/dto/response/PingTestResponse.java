package edu.fpt.sba.scms.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(name = "PingTestResponse", description = "Kết quả Ping Test kết nối SePay (UC15)")
public record PingTestResponse(
        boolean success,
        String status,
        String message,
        Instant testedAt
) {
}

