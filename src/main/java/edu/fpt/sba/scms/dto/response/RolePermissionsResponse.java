package edu.fpt.sba.scms.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "RolePermissionsResponse", description = "Ma trận phân quyền của vai trò (UC02, SCR-16 TAB-09)")
public record RolePermissionsResponse(
        Integer roleId,
        String roleCode,
        String roleName,
        String description,
        @Schema(description = "Danh sách quyền hiện tại của vai trò")
        List<String> assignedPermissions,
        @Schema(description = "Toàn bộ danh sách quyền khả dụng trong hệ thống để render checkbox")
        List<String> allAvailablePermissions
) {
}

