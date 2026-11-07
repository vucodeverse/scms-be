package edu.fpt.sba.scms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Schema(name = "CreateOrderRequest", description = "Tạo đơn hàng tại quầy POS")
public record CreateOrderRequest(
        @Schema(description = "ID chi nhánh bán", example = "1")
        @NotNull(message = "Chi nhánh không được để trống")
        Integer branchId,

        @Schema(description = "Tổng tiền trước giảm", example = "60000")
        @NotNull(message = "Tổng tiền không được để trống")
        @DecimalMin(value = "0.0", message = "Tổng tiền phải >= 0")
        BigDecimal totalAmount,

        @Schema(description = "Tiền giảm giá", example = "5000")
        BigDecimal discountAmount,

        @Schema(description = "Tiền phải thanh toán", example = "55000")
        @NotNull(message = "Tiền thanh toán không được để trống")
        @DecimalMin(value = "0.0", message = "Tiền thanh toán phải >= 0")
        BigDecimal finalAmount,

        @Schema(description = "Ghi chú đơn hàng", example = "Ít đường, nhiều đá")
        String note
) {
}

