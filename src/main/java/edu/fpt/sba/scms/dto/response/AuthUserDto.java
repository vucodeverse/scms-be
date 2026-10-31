package edu.fpt.sba.scms.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** Thông tin user trong response auth — KHÔNG bao giờ chứa hash/PIN. */
@Schema(name = "AuthUser")
public record AuthUserDto(
        Integer id,
        String fullName,
        @Schema(example = "COO") String role,
        Integer branchId,
        String branchName) {
}
