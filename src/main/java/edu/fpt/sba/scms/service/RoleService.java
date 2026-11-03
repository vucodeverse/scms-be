package edu.fpt.sba.scms.service;

import edu.fpt.sba.scms.dto.request.AssignRoleRequest;
import edu.fpt.sba.scms.dto.request.UpdateRolePermissionsRequest;
import edu.fpt.sba.scms.dto.response.RolePermissionDto;
import edu.fpt.sba.scms.dto.response.RolePermissionsResponse;
import edu.fpt.sba.scms.dto.response.UserDto;
import edu.fpt.sba.scms.security.principal.UserPrincipal;

import java.util.List;

/**
 * Nghiệp vụ quản lý vai trò & ma trận phân quyền (UC02).
 */
public interface RoleService {

    /** Danh sách tất cả vai trò kèm số lượng user và quyền hạn. */
    List<RolePermissionDto> listRoles();

    /** Xem chi tiết ma trận quyền của một vai trò. */
    RolePermissionsResponse getRolePermissions(Integer roleId);

    /** Cập nhật quyền hạn cho vai trò. */
    RolePermissionsResponse updateRolePermissions(Integer roleId, UpdateRolePermissionsRequest request, UserPrincipal principal);

    /** Gán vai trò cho nhân viên. */
    UserDto assignRole(Integer employeeId, AssignRoleRequest request, UserPrincipal principal);
}

