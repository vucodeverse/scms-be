package edu.fpt.sba.scms.dto.response;

import edu.fpt.sba.scms.common.enums.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(name = "OrderDto", description = "Tóm tắt thông tin đơn hàng POS (UC18)")
public record OrderDto(
        Integer id,
        Integer branchId,
        Integer shiftId,
        Integer cashierId,
        OrderStatus status,
        BigDecimal totalAmount,
        BigDecimal discountAmount,
        BigDecimal finalAmount,
        String note,
        String receiptNo,
        Instant receiptPrintedAt,
        String cancelReason,
        Instant createdAt,
        Instant paidAt,
        Instant cancelledAt
) {
}
