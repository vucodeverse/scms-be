package edu.fpt.sba.scms.dto.response;

import edu.fpt.sba.scms.common.enums.RefundStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(name = "RefundResponse", description = "Kết quả xử lý hoàn tiền đơn hàng")
public record RefundResponse(
        Integer id,
        Integer orderId,
        Integer paymentId,
        Integer cashTransactionId,
        BigDecimal amount,
        String reason,
        RefundStatus status,
        Integer requestedBy,
        Instant createdAt,
        Integer approvedBy,
        Instant approvedAt,
        String message
) {
}

