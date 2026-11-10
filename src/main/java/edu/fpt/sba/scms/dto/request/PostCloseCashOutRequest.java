package edu.fpt.sba.scms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(name = "PostCloseCashOutRequest", description = "Quản lý rút bớt tiền khỏi két sau khi ca gần nhất đã đóng (cất két Quản lý)")
public record PostCloseCashOutRequest(
        @Schema(description = "ID chi nhánh", example = "1")
        @NotNull(message = "Chi nhánh không được để trống")
        Integer branchId,

        @Schema(description = "Số tiền mặt rút khỏi két sau kết ca", example = "3000000")
        @NotNull(message = "Số tiền rút không được để trống")
        @DecimalMin(value = "1.0", message = "Số tiền rút phải lớn hơn 0")
        BigDecimal amount,

        @Schema(description = "Lý do rút tiền sau kết ca", example = "Thu hồi doanh thu cuối ngày cất két Quản lý")
        @NotBlank(message = "Lý do rút tiền là bắt buộc")
        @Size(max = 500, message = "Lý do tối đa 500 ký tự")
        String reason,

        @Schema(description = "Mã PIN của Quản lý chi nhánh (tùy chọn khi Quản lý hoặc COO đang đăng nhập trực tiếp)", example = "654321")
        @Size(max = 10, message = "Mã PIN tối đa 10 ký tự")
        String managerPin
) {
}
