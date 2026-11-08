package edu.fpt.sba.scms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Schema(name = "ProcessCashPaymentRequest", description = "Yêu cầu thanh toán tiền mặt tại quầy POS (UC19)")
public record ProcessCashPaymentRequest(
        @Schema(description = "ID đơn hàng cần thanh toán", example = "101")
        @NotNull(message = "ID đơn hàng không được để trống")
        Integer orderId,

        @Schema(description = "Số tiền mặt khách đưa", example = "100000")
        @NotNull(message = "Số tiền khách đưa không được để trống")
        @DecimalMin(value = "0.0", inclusive = false, message = "Số tiền khách đưa phải lớn hơn 0")
        BigDecimal cashReceived,

        @Schema(description = "Ghi chú thanh toán (nếu có)", example = "Khách thanh toán tiền mặt 100k")
        String notes
) {
}

