package edu.fpt.sba.scms.dto.response;

import edu.fpt.sba.scms.common.enums.OrderStatus;
import edu.fpt.sba.scms.common.enums.PaymentMethod;
import edu.fpt.sba.scms.common.enums.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(name = "PaymentStatusResponse", description = "Trạng thái thanh toán của đơn hàng (UC20, UC44)")
public record PaymentStatusResponse(
        Integer orderId,
        OrderStatus orderStatus,
        PaymentStatus paymentStatus,
        PaymentMethod paymentMethod,
        BigDecimal amount,
        BigDecimal receivedAmount,
        String referenceCode,
        String sepayRef,
        Instant paidAt,
        String message
) {
}

