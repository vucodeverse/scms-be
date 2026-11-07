package edu.fpt.sba.scms.dto.response;

import edu.fpt.sba.scms.common.enums.OrderStatus;
import edu.fpt.sba.scms.common.enums.PaymentMethod;
import edu.fpt.sba.scms.common.enums.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(name = "CashPaymentResponse", description = "Kết quả thanh toán tiền mặt tại quầy POS (UC19)")
public record CashPaymentResponse(
        Integer orderId,
        OrderStatus orderStatus,
        PaymentStatus paymentStatus,
        PaymentMethod paymentMethod,
        BigDecimal totalAmount,
        BigDecimal cashReceived,
        BigDecimal changeReturned,
        Instant paidAt,
        String message
) {
}

