package edu.fpt.sba.scms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(name = "PreOpenCashInRequest", description = "Quản lý nạp thêm tiền mặt vào két trước khi mở ca đầu ngày")
public record PreOpenCashInRequest(
        @Schema(description = "ID chi nhánh", example = "1")
        @NotNull(message = "Chi nhánh không được để trống")
        Integer branchId,

        @Schema(description = "Số tiền mặt nạp thêm đầu ngày", example = "1000000")
        @NotNull(message = "Số tiền nạp không được để trống")
        @DecimalMin(value = "1.0", message = "Số tiền nạp phải lớn hơn 0")
        BigDecimal amount,

        @Schema(description = "Lý do nạp thêm tiền đầu ngày", example = "Bổ sung tiền lẻ 10k, 20k cho ca sáng")
        @NotBlank(message = "Lý do nạp tiền là bắt buộc")
        @Size(max = 500, message = "Lý do tối đa 500 ký tự")
        String reason,

        @Schema(description = "Mã PIN của Quản lý chi nhánh (tùy chọn khi Quản lý hoặc COO đang đăng nhập trực tiếp)", example = "654321")
        @Size(max = 10, message = "Mã PIN tối đa 10 ký tự")
        String managerPin
) {
}
