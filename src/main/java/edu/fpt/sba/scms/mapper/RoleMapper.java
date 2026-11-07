package edu.fpt.sba.scms.mapper;

import edu.fpt.sba.scms.dto.response.RolePermissionDto;
import edu.fpt.sba.scms.dto.response.RolePermissionsResponse;
import edu.fpt.sba.scms.model.Role;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Mapper chuyển đổi Entity ↔ DTO cho vai trò và phân quyền (UC02).
 */
@Mapper(componentModel = "spring")
public interface RoleMapper {

    @Mapping(target = "roleId", source = "role.id")
    @Mapping(target = "code", source = "role.code")
    @Mapping(target = "name", source = "role.name")
    @Mapping(target = "description", source = "role.description")
    @Mapping(target = "userCount", source = "userCount")
    @Mapping(target = "permissions", source = "permissions")
    RolePermissionDto toPermissionDto(Role role, long userCount, List<String> permissions);

    @Mapping(target = "roleId", source = "role.id")
    @Mapping(target = "roleCode", source = "role.code")
    @Mapping(target = "roleName", source = "role.name")
    @Mapping(target = "description", source = "role.description")
    @Mapping(target = "assignedPermissions", source = "assignedPermissions")
    @Mapping(target = "allAvailablePermissions", source = "allAvailablePermissions")
    RolePermissionsResponse toPermissionsResponse(
            Role role,
            List<String> assignedPermissions,
            List<String> allAvailablePermissions
    );
}

