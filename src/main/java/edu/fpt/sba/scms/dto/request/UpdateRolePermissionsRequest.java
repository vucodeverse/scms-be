package edu.fpt.sba.scms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

@Schema(name = "UpdateRolePermissionsRequest", description = "Cập nhật danh sách quyền cho vai trò (UC02)")
public record UpdateRolePermissionsRequest(
        @Schema(description = "Danh sách mã quyền chức năng được cấp cho vai trò", example = "[\"ORDER_VIEW\", \"REFUND_REQUEST\"]")
        @NotEmpty(message = "Danh sách quyền không được để trống")
        List<String> permissions
) {
}

