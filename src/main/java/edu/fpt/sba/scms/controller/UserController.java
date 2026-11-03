package edu.fpt.sba.scms.controller;

import edu.fpt.sba.scms.dto.ApiErrorResponse;
import edu.fpt.sba.scms.dto.PageResponse;
import edu.fpt.sba.scms.dto.request.AssignRoleRequest;
import edu.fpt.sba.scms.dto.request.ResetPasswordRequest;
import edu.fpt.sba.scms.dto.request.SaveUserRequest;
import edu.fpt.sba.scms.dto.request.SaveUserStatusRequest;
import edu.fpt.sba.scms.dto.request.query.UserListQuery;
import edu.fpt.sba.scms.dto.response.MessageResponse;
import edu.fpt.sba.scms.dto.response.RoleDto;
import edu.fpt.sba.scms.dto.response.UserDetailDto;
import edu.fpt.sba.scms.dto.response.UserDto;
import edu.fpt.sba.scms.security.principal.UserPrincipal;
import edu.fpt.sba.scms.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * UC02 + quản lý user (IMPLEMENTATION.md §10.2, §10.2.1). COO quản mọi chi nhánh; Branch Manager
 * chỉ thấy/sửa nhân sự chi nhánh mình (kiểm trong service bằng {@code BranchContext}).
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "02. Users", description = "Người dùng & phân quyền (UC02, SCR-09)")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    @Operation(summary = "Danh sách người dùng (phân trang)",
            description = "Mặc định 10 phần tử/trang; BM chỉ thấy user của chi nhánh mình. "
                    + "sortBy: fullName|username|createdAt.")
    @GetMapping
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER')")
    public PageResponse<UserDto> list(@ModelAttribute @Valid UserListQuery query) {
        return PageResponse.of(userService.list(query));
    }

    @Operation(summary = "Danh sách role (kèm số user đang hoạt động)")
    @GetMapping("/roles")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER')")
    public List<RoleDto> roles() {
        return userService.roles();
    }

    @Operation(summary = "Chi tiết người dùng")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "RESOURCE_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER')")
    public UserDetailDto detail(@PathVariable Integer id) {
        return userService.detail(id);
    }

    @Operation(summary = "Tạo người dùng",
            description = "COO tạo được mọi role (trừ COO); BM chỉ tạo Cashier/Barista/Waiter cho chi nhánh mình.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Đã tạo"),
            @ApiResponse(responseCode = "409", description = "DUPLICATE_USERNAME | DUPLICATE_PIN | DUPLICATE_CARD",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER')")
    public UserDto create(@RequestBody @Valid SaveUserRequest request,
                          @AuthenticationPrincipal UserPrincipal principal) {
        return userService.create(request, principal);
    }

    @Operation(summary = "Cập nhật người dùng",
            description = "Mật khẩu/PIN bỏ trống = giữ nguyên; có giá trị = đặt lại.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "403", description = "CROSS_BRANCH_DENIED",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "RESOURCE_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER')")
    public UserDto update(@PathVariable Integer id, @RequestBody @Valid SaveUserRequest request,
                          @AuthenticationPrincipal UserPrincipal principal) {
        return userService.update(id, request, principal);
    }

    @Operation(summary = "Đổi trạng thái tài khoản", description = "Không tự khoá chính mình.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "422", description = "SELF_ACTION_FORBIDDEN",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER')")
    public UserDto changeStatus(@PathVariable Integer id, @RequestBody @Valid SaveUserStatusRequest request,
                                @AuthenticationPrincipal UserPrincipal principal) {
        return userService.changeStatus(id, request, principal);
    }

    @Operation(summary = "Gán role (UC02)", description = "Hiệu lực từ lần đăng nhập sau.")
    @PutMapping("/{id}/role")
    @PreAuthorize("hasAuthority('COO')")
    public UserDto assignRole(@PathVariable Integer id, @RequestBody @Valid AssignRoleRequest request,
                              @AuthenticationPrincipal UserPrincipal principal) {
        return userService.assignRole(id, request, principal);
    }

    @Operation(summary = "Đặt lại mật khẩu", description = "Thu hồi toàn bộ phiên đăng nhập của user.")
    @PutMapping("/{id}/password")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER')")
    public MessageResponse resetPassword(@PathVariable Integer id, @RequestBody @Valid ResetPasswordRequest request,
                                         @AuthenticationPrincipal UserPrincipal principal) {
        userService.resetPassword(id, request, principal);
        return new MessageResponse("Đã đặt lại mật khẩu.");
    }
}
