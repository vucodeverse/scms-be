package edu.fpt.sba.scms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

/** Body của {@code POST /api/v1/auth/refresh}; có thể bỏ trống khi server đọc refresh token từ cookie httpOnly. */
@Schema(name = "RefreshTokenRequest")
public record RefreshTokenRequest(
        @Schema(description = "Refresh token nhận từ login") String refreshToken) {
}
