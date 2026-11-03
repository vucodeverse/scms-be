package edu.fpt.sba.scms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body {@code PUT /users/{id}/password} (IMPLEMENTATION.md §10.2.1, quy tắc Q-03: ≥ 8, có chữ và số). */
@Schema(name = "ResetPasswordRequest")
public record ResetPasswordRequest(
        @NotBlank(message = "Mật khẩu là bắt buộc.")
        @Size(min = 8, max = 72, message = "Mật khẩu phải từ 8 đến 72 ký tự.")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$", message = "Mật khẩu phải có ít nhất một chữ và một số.")
        @Schema(format = "password")
        String password) {
}
