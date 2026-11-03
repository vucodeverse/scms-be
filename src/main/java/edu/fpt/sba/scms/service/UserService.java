package edu.fpt.sba.scms.service;

import edu.fpt.sba.scms.dto.request.AssignRoleRequest;
import edu.fpt.sba.scms.dto.request.ResetPasswordRequest;
import edu.fpt.sba.scms.dto.request.SaveUserRequest;
import edu.fpt.sba.scms.dto.request.SaveUserStatusRequest;
import edu.fpt.sba.scms.dto.request.query.UserListQuery;
import edu.fpt.sba.scms.dto.response.RoleDto;
import edu.fpt.sba.scms.dto.response.UserDetailDto;
import edu.fpt.sba.scms.dto.response.UserDto;
import edu.fpt.sba.scms.security.principal.UserPrincipal;
import org.springframework.data.domain.Page;

import java.util.List;

/** UC02 + quản lý user (IMPLEMENTATION.md §10.2, §10.2.1). */
public interface UserService {

    /** Danh sách phân trang; BM chỉ thấy user của chi nhánh mình (branchId trong query bị bỏ qua). */
    Page<UserDto> list(UserListQuery query);

    /** Chi tiết; id ngoài chi nhánh của BM → 403 CROSS_BRANCH_DENIED. */
    UserDetailDto detail(Integer id);

    UserDto create(SaveUserRequest request, UserPrincipal actor);

    UserDto update(Integer id, SaveUserRequest request, UserPrincipal actor);

    UserDto changeStatus(Integer id, SaveUserStatusRequest request, UserPrincipal actor);

    /** UC02 — COO đổi role; hiệu lực từ lần đăng nhập sau (thu hồi refresh token để bắt đăng nhập lại). */
    UserDto assignRole(Integer id, AssignRoleRequest request, UserPrincipal actor);

    /** Đặt lại mật khẩu (chỉ COO/BM); thu hồi toàn bộ refresh token của user. */
    void resetPassword(Integer id, ResetPasswordRequest request, UserPrincipal actor);

    /** {@code GET /roles} — kèm số user ACTIVE theo từng role. */
    List<RoleDto> roles();
}
