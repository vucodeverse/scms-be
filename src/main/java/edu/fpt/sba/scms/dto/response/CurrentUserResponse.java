package edu.fpt.sba.scms.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Response {@code GET /api/v1/auth/me}. */
@Schema(name = "CurrentUser")
public record CurrentUserResponse(
        Integer id,
        String fullName,
        String username,
        @Schema(example = "BRANCH_MANAGER") String role,
        Integer branchId,
        String branchName,
        @Schema(description = "Quyền cố định theo role (RBAC, không ma trận)") List<String> permissions) {
}
