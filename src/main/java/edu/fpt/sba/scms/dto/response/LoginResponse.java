package edu.fpt.sba.scms.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response {@code POST /auth/login}.
 *
 * @param landing màn hình đích: SCR-10 (COO), SCR-07 (BM), SCR-04 (Cashier)
 */
@Schema(name = "LoginResponse")
public record LoginResponse(
        String accessToken,
        @Schema(description = "Số giây access token còn hiệu lực", example = "1800") long expiresInSec,
        @Schema(description = "Chỉ trả khi rememberMe = true") String refreshToken,
        AuthUserDto user,
        @Schema(allowableValues = {"SCR-10", "SCR-07", "SCR-04"}) String landing) {
}
