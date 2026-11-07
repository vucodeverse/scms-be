package edu.fpt.sba.scms.mapper;

import edu.fpt.sba.scms.dto.response.CashPaymentResponse;
import edu.fpt.sba.scms.dto.response.OrderResponse;
import edu.fpt.sba.scms.dto.response.PaymentStatusResponse;
import edu.fpt.sba.scms.dto.response.VietQrResponse;
import edu.fpt.sba.scms.model.Order;
import edu.fpt.sba.scms.model.Payment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Mapper chuyển đổi Entity ↔ DTO cho thanh toán Tiền mặt, VietQR và SePay (UC19, UC20, UC43, UC44).
 */
@Mapper(componentModel = "spring")
public interface PaymentMapper {

    @Mapping(target = "orderId", source = "order.id")
    @Mapping(target = "branchId", source = "order.branchId")
    @Mapping(target = "status", source = "order.status")
    @Mapping(target = "totalAmount", source = "order.totalAmount")
    @Mapping(target = "discountAmount", source = "order.discountAmount")
    @Mapping(target = "finalAmount", source = "order.finalAmount")
    @Mapping(target = "note", source = "order.note")
    @Mapping(target = "createdAt", source = "order.createdAt")
    @Mapping(target = "paidAt", source = "order.paidAt")
    OrderResponse toOrderResponse(Order order);

    @Mapping(target = "orderId", source = "order.id")
    @Mapping(target = "orderStatus", source = "order.status")
    @Mapping(target = "paymentStatus", source = "payment.status")
    @Mapping(target = "paymentMethod", source = "payment.method")
    @Mapping(target = "totalAmount", source = "order.finalAmount")
    @Mapping(target = "cashReceived", source = "cashReceived")
    @Mapping(target = "changeReturned", source = "changeReturned")
    @Mapping(target = "paidAt", source = "paidAt")
    @Mapping(target = "message", source = "message")
    CashPaymentResponse toCashPaymentResponse(
            Order order,
            Payment payment,
            BigDecimal cashReceived,
            BigDecimal changeReturned,
            Instant paidAt,
            String message
    );

    @Mapping(target = "orderId", source = "order.id")
    @Mapping(target = "referenceCode", source = "payment.referenceCode")
    @Mapping(target = "amount", source = "payment.amount")
    @Mapping(target = "bankName", source = "bankName")
    @Mapping(target = "accountNumber", source = "accountNumber")
    @Mapping(target = "accountHolder", source = "accountHolder")
    @Mapping(target = "qrImageUrl", source = "payment.qrCode")
    @Mapping(target = "qrPayload", source = "qrPayload")
    @Mapping(target = "expiresAt", source = "payment.qrExpiresAt")
    @Mapping(target = "status", source = "payment.status")
    VietQrResponse toVietQrResponse(
            Order order,
            Payment payment,
            String bankName,
            String accountNumber,
            String accountHolder,
            String qrPayload
    );

    @Mapping(target = "orderId", expression = "java(order != null ? order.getId() : (payment != null ? payment.getOrderId() : null))")
    @Mapping(target = "orderStatus", expression = "java(order != null ? order.getStatus() : null)")
    @Mapping(target = "paymentStatus", expression = "java(payment != null ? payment.getStatus() : null)")
    @Mapping(target = "paymentMethod", expression = "java(payment != null ? payment.getMethod() : null)")
    @Mapping(target = "amount", expression = "java(payment != null && payment.getAmount() != null ? payment.getAmount() : (order != null ? order.getFinalAmount() : null))")
    @Mapping(target = "receivedAmount", expression = "java(payment != null ? payment.getReceivedAmount() : null)")
    @Mapping(target = "referenceCode", expression = "java(payment != null ? payment.getReferenceCode() : null)")
    @Mapping(target = "sepayRef", expression = "java(payment != null ? payment.getSepayRef() : null)")
    @Mapping(target = "paidAt", expression = "java(order != null ? order.getPaidAt() : null)")
    @Mapping(target = "message", source = "message")
    PaymentStatusResponse toPaymentStatusResponse(
            Order order,
            Payment payment,
            String message
    );
}
