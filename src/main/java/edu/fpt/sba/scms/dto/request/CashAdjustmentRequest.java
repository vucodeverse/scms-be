package edu.fpt.sba.scms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(name = "CashAdjustmentRequest", description = "Nạp hoặc Rút tiền mặt khỏi két trong ca (UC25, UC26, BR-37, BR-32)")
public record CashAdjustmentRequest(
        @Schema(description = "Số tiền mặt nạp/rút", example = "200000")
        @NotNull(message = "Số tiền không được để trống")
        @DecimalMin(value = "1.0", message = "Số tiền phải lớn hơn 0")
        BigDecimal amount,

        @Schema(description = "Lý do nạp/rút tiền (Bắt buộc theo BR-37)", example = "Nạp thêm tiền lẻ 10k, 20k để thối")
        @NotBlank(message = "Lý do là bắt buộc")
        @Size(max = 500, message = "Lý do tối đa 500 ký tự")
        String reason,

        @Schema(description = "Mã PIN của Quản lý chi nhánh xác thực (Quy tắc 2 người duyệt BR-32)", example = "123456")
        @NotBlank(message = "Mã PIN Quản lý chi nhánh là bắt buộc")
        String managerPin
) {
}

