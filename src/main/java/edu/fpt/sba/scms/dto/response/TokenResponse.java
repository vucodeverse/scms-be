package edu.fpt.sba.scms.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** Response {@code POST /api/v1/auth/refresh} — access token mới (và refresh token xoay vòng). */
@Schema(name = "TokenResponse")
public record TokenResponse(
        String accessToken,
        long expiresInSec,
        @Schema(description = "Refresh token mới (rotation); FE phải thay token cũ") String refreshToken,
        AuthUserDto user) {
}
