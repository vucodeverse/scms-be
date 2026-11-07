package edu.fpt.sba.scms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "CancelOrderRequest", description = "Hủy đơn hàng chưa thanh toán (BR-26b)")
public record CancelOrderRequest(
        @Schema(description = "Lý do hủy đơn hàng bắt buộc", example = "Khách hàng đổi ý không mua nữa")
        @NotBlank(message = "Lý do hủy đơn hàng không được để trống")
        @Size(max = 500, message = "Lý do hủy tối đa 500 ký tự")
        String cancelReason
) {
}
