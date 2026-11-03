package edu.fpt.sba.scms.service.impl;

import edu.fpt.sba.scms.common.audit.AuditLogService;
import edu.fpt.sba.scms.common.enums.AuditAction;
import edu.fpt.sba.scms.common.enums.ErrorCode;
import edu.fpt.sba.scms.common.enums.UserStatus;
import edu.fpt.sba.scms.common.exception.NotFoundException;
import edu.fpt.sba.scms.dto.request.AssignRoleRequest;
import edu.fpt.sba.scms.dto.request.UpdateRolePermissionsRequest;
import edu.fpt.sba.scms.dto.response.RolePermissionDto;
import edu.fpt.sba.scms.dto.response.RolePermissionsResponse;
import edu.fpt.sba.scms.dto.response.UserDto;
import edu.fpt.sba.scms.model.Role;
import edu.fpt.sba.scms.repository.RoleRepository;
import edu.fpt.sba.scms.repository.UserRepository;
import edu.fpt.sba.scms.security.PermissionCatalog;
import edu.fpt.sba.scms.security.principal.UserPrincipal;
import edu.fpt.sba.scms.service.RoleService;
import edu.fpt.sba.scms.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final AuditLogService auditLogService;

    /** Lưu trữ quyền tùy biến theo từng role code (mặc định lấy từ PermissionCatalog). */
    private final Map<String, List<String>> rolePermissionsMap = new ConcurrentHashMap<>();

    @Override
    @Transactional(readOnly = true)
    public List<RolePermissionDto> listRoles() {
        List<Role> roles = roleRepository.findAll();
        return roles.stream().map(role -> {
            long userCount = userRepository.countByRoleCodesAndStatus(List.of(role.getCode()), UserStatus.ACTIVE);
            List<String> perms = getPermissionsForRole(role.getCode());
            return new RolePermissionDto(
                    role.getId(),
                    role.getCode(),
                    role.getName(),
                    role.getDescription(),
                    userCount,
                    perms
            );
        }).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RolePermissionsResponse getRolePermissions(Integer roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new NotFoundException("vai trò"));

        List<String> assigned = getPermissionsForRole(role.getCode());
        List<String> allAvailable = PermissionCatalog.allPermissions();

        return new RolePermissionsResponse(
                role.getId(),
                role.getCode(),
                role.getName(),
                role.getDescription(),
                assigned,
                allAvailable
        );
    }

    @Override
    @Transactional
    public RolePermissionsResponse updateRolePermissions(
            Integer roleId,
            UpdateRolePermissionsRequest request,
            UserPrincipal principal) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new NotFoundException("vai trò"));

        List<String> before = getPermissionsForRole(role.getCode());
        List<String> newPermissions = List.copyOf(request.permissions());
        rolePermissionsMap.put(role.getCode(), newPermissions);

        auditLogService.log(AuditAction.SETTINGS_UPDATE, "role_permission", role.getId(), before, newPermissions);
        log.info("COO '{}' cập nhật quyền hạn cho vai trò '{}' (ID={}): {}", principal.username(), role.getCode(), role.getId(), newPermissions);

        return new RolePermissionsResponse(
                role.getId(),
                role.getCode(),
                role.getName(),
                role.getDescription(),
                newPermissions,
                PermissionCatalog.allPermissions()
        );
    }

    @Override
    @Transactional
    public UserDto assignRole(Integer employeeId, AssignRoleRequest request, UserPrincipal principal) {
        return userService.assignRole(employeeId, request, principal);
    }

    private List<String> getPermissionsForRole(String roleCode) {
        return rolePermissionsMap.computeIfAbsent(roleCode, PermissionCatalog::of);
    }
}
