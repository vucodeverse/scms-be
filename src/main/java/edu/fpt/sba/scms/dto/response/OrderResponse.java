package edu.fpt.sba.scms.dto.response;

import edu.fpt.sba.scms.common.enums.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(name = "OrderResponse", description = "Thông tin đơn hàng")
public record OrderResponse(
        Integer orderId,
        Integer branchId,
        OrderStatus status,
        BigDecimal totalAmount,
        BigDecimal discountAmount,
        BigDecimal finalAmount,
        String note,
        Instant createdAt,
        Instant paidAt
) {
}

