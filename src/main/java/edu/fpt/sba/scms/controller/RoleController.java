package edu.fpt.sba.scms.controller;

import edu.fpt.sba.scms.dto.ApiErrorResponse;
import edu.fpt.sba.scms.dto.request.AssignRoleRequest;
import edu.fpt.sba.scms.dto.request.UpdateRolePermissionsRequest;
import edu.fpt.sba.scms.dto.response.RolePermissionDto;
import edu.fpt.sba.scms.dto.response.RolePermissionsResponse;
import edu.fpt.sba.scms.dto.response.UserDto;
import edu.fpt.sba.scms.security.principal.UserPrincipal;
import edu.fpt.sba.scms.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller phục vụ quản lý vai trò và phân quyền (UC02, SCR-16 TAB-09).
 */
@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@Tag(name = "02. Roles & Permissions", description = "Quản lý vai trò & ma trận phân quyền (UC02)")
@SecurityRequirement(name = "bearerAuth")
public class RoleController {

    private final RoleService roleService;

    @Operation(summary = "Danh sách vai trò & quyền hạn (UC02)",
            description = "Trả về tất cả vai trò trong hệ thống kèm số nhân viên đang giữ vai trò và danh sách quyền hạn.")
    @GetMapping
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER')")
    public List<RolePermissionDto> list() {
        return roleService.listRoles();
    }

    @Operation(summary = "Xem ma trận phân quyền của vai trò (UC02)",
            description = "Trả về danh sách quyền hiện tại của vai trò và toàn bộ danh mục quyền để render checkbox ma trận.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "RESOURCE_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    @GetMapping("/{roleId}/permissions")
    @PreAuthorize("hasAuthority('COO')")
    public RolePermissionsResponse permissions(@PathVariable Integer roleId) {
        return roleService.getRolePermissions(roleId);
    }

    @Operation(summary = "Cập nhật ma trận phân quyền của vai trò (UC02)",
            description = "COO lưu lại các checkbox quyền chức năng đã chọn cho vai trò.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cập nhật thành công"),
            @ApiResponse(responseCode = "404", description = "RESOURCE_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    @PutMapping("/{roleId}/permissions")
    @PreAuthorize("hasAuthority('COO')")
    public RolePermissionsResponse updatePermissions(
            @PathVariable Integer roleId,
            @RequestBody @Valid UpdateRolePermissionsRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return roleService.updateRolePermissions(roleId, request, principal);
    }

    @Operation(summary = "Gán vai trò cho nhân viên (UC02)",
            description = "Gán một vai trò cho tài khoản nhân viên. Quyền mới có hiệu lực từ phiên làm việc tiếp theo.")
    @PutMapping("/assignments/{employeeId}")
    @PreAuthorize("hasAuthority('COO')")
    public UserDto assignRole(
            @PathVariable Integer employeeId,
            @RequestBody @Valid AssignRoleRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return roleService.assignRole(employeeId, request, principal);
    }
}

