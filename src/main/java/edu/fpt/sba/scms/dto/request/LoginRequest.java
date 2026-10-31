package edu.fpt.sba.scms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body của {@code POST /api/v1/auth/login} (IMPLEMENTATION.md §10.1).
 *
 * @param roleHint chỉ là gợi ý điều hướng của FE, KHÔNG ảnh hưởng quyền — landing luôn tính từ role trong DB.
 */
@Schema(name = "LoginRequest")
public record LoginRequest(
        @Schema(example = "coo")
        @NotBlank(message = "Tên đăng nhập là bắt buộc.")
        @Size(max = 50, message = "Tên đăng nhập tối đa 50 ký tự.")
        String username,

        @Schema(example = "Admin@123", format = "password")
        @NotBlank(message = "Mật khẩu là bắt buộc.")
        @Size(max = 72, message = "Mật khẩu tối đa 72 ký tự.")
        String password,

        @Schema(description = "Ghi nhớ đăng nhập — nếu true server trả refreshToken trong body", example = "false")
        Boolean rememberMe,

        @Schema(description = "Gợi ý vai trò để FE chọn màn hình", allowableValues = {"COO", "BRANCH_MANAGER", "CASHIER"})
        @Pattern(regexp = "^(COO|BRANCH_MANAGER|CASHIER)?$", message = "roleHint không hợp lệ.")
        String roleHint) {
}
