package edu.fpt.sba.scms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@Schema(name = "PinLoginRequest", description = "Đăng nhập Thu ngân bằng mã PIN tại màn hình bán hàng / kiểm két")
public record PinLoginRequest(
        @Schema(description = "ID chi nhánh", example = "1")
        @NotNull(message = "Chi nhánh không được để trống")
        Integer branchId,

        @Schema(description = "ID nhân viên thu ngân (tùy chọn; nếu bỏ trống hệ thống tự tra cứu theo chi nhánh + PIN)")
        Integer employeeId,

        @Schema(description = "Mã PIN 4-6 chữ số", example = "1234")
        @NotBlank(message = "Mã PIN không được để trống")
        @Pattern(regexp = "^[0-9]{4,6}$", message = "Mã PIN phải từ 4 đến 6 chữ số")
        String pin
) {
}
