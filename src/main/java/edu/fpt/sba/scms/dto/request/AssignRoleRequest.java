package edu.fpt.sba.scms.dto.request;

import edu.fpt.sba.scms.common.enums.Roles;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Body {@code PUT /users/{id}/role} (UC02, IMPLEMENTATION.md §10.2). Hiệu lực từ lần đăng nhập sau. */
@Schema(name = "AssignRoleRequest")
public record AssignRoleRequest(
        @NotNull(message = "Role là bắt buộc.")
        Roles roleCode) {
}
