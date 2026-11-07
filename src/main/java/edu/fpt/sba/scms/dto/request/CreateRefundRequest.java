package edu.fpt.sba.scms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(name = "CreateRefundRequest", description = "Yêu cầu hoàn tiền cho đơn hàng đã thanh toán (UC23, BR-32/33/34/35)")
public record CreateRefundRequest(
        @Schema(description = "ID đơn hàng cần hoàn tiền", example = "1")
        @NotNull(message = "ID đơn hàng không được để trống")
        Integer orderId,

        @Schema(description = "Số tiền hoàn (<= số tiền đã thanh toán của đơn)", example = "55000")
        @NotNull(message = "Số tiền hoàn không được để trống")
        @DecimalMin(value = "1.0", message = "Số tiền hoàn phải lớn hơn 0")
        BigDecimal amount,

        @Schema(description = "Lý do hoàn tiền (Bắt buộc theo BR-32)", example = "Khách phản ánh nước bị chua do sữa hỏng")
        @NotBlank(message = "Lý do hoàn tiền là bắt buộc")
        @Size(max = 500, message = "Lý do tối đa 500 ký tự")
        String reason,

        @Schema(description = "Mã PIN Quản lý chi nhánh phê duyệt (Quy tắc 2 người duyệt BR-32)", example = "123456")
        @NotBlank(message = "Mã PIN Quản lý chi nhánh là bắt buộc")
        String managerPin
) {
}

