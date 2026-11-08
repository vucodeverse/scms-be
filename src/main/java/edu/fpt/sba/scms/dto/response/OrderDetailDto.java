package edu.fpt.sba.scms.dto.response;

import edu.fpt.sba.scms.common.enums.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(name = "OrderDetailDto", description = "Chi tiết đầy đủ đơn hàng POS (UC18, UC16, UC17)")
public record OrderDetailDto(
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
        Instant cancelledAt,
        List<OrderLineDto> lines,
        OrderVoucherDto voucher
) {
}
