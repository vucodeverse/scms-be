package edu.fpt.sba.scms.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "RolePermissionDto", description = "Thông tin vai trò kèm danh sách quyền (UC02)")
public record RolePermissionDto(
        Integer roleId,
        String code,
        String name,
        String description,
        long userCount,
        List<String> permissions
) {
}

