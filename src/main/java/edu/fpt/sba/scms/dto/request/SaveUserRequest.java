package edu.fpt.sba.scms.dto.request;

import edu.fpt.sba.scms.common.enums.Roles;
import edu.fpt.sba.scms.common.enums.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body {@code POST /users} và {@code PUT /users/{id}} (IMPLEMENTATION.md §10.2.1).
 * <ul>
 *   <li>{@code username}/{@code password}: chỉ COO/BRANCH_MANAGER; sai -> 400.</li>
 *   <li>{@code pin}: bắt buộc khi tạo Barista/Waiter/Cashier; có giá trị khi sửa = đặt lại PIN.</li>
 *   <li>{@code branchId}: COO bắt buộc; BM gửi hay không đều bị ghi đè bằng chi nhánh trong JWT.</li>
 * </ul>
 */
@Schema(name = "SaveUserRequest")
public record SaveUserRequest(
        @NotBlank(message = "Họ tên là bắt buộc.")
        @Size(max = 100, message = "Họ tên tối đa 100 ký tự.")
        String fullName,

        @NotBlank(message = "Số điện thoại là bắt buộc.")
        @Pattern(regexp = "^0\\d{9,10}$", message = "Số điện thoại phải bắt đầu bằng 0 và gồm 10-11 số.")
        String phone,

        @Email(message = "Email không hợp lệ.")
        @Size(max = 255, message = "Email tối đa 255 ký tự.")
        String email,

        @NotNull(message = "Chức danh là bắt buộc.")
        Roles position,

        @Schema(description = "COO bắt buộc; BM bị ghi đè bằng chi nhánh trong JWT")
        Integer branchId,

        @Size(max = 50, message = "Tên đăng nhập tối đa 50 ký tự.")
        @Pattern(regexp = "^[A-Za-z0-9._-]*$", message = "Tên đăng nhập chỉ gồm chữ, số, '.', '_', '-'.")
        String username,

        @Schema(format = "password", description = "≥ 8 ký tự, có chữ và số. Bỏ trống khi sửa = giữ nguyên")
        @Size(max = 72, message = "Mật khẩu tối đa 72 ký tự.")
        String password,

        @Pattern(regexp = "^\\d{4}$", message = "PIN phải gồm đúng 4 số.")
        String pin,

        @Size(max = 15, message = "Mã thẻ tối đa 15 ký tự.")
        String cardCode,

        @Min(value = 1, message = "Lương/giờ phải > 0.")
        @Max(value = 100_000_000, message = "Lương/giờ quá lớn.")
        @Schema(description = "VND/giờ (số nguyên); khác mức hiện hành -> ghi thêm một dòng wage_rate")
        Integer hourlyRate,

        @NotNull(message = "Trạng thái là bắt buộc.")
        UserStatus status) {
}
