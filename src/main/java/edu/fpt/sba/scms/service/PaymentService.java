package edu.fpt.sba.scms.service;

import edu.fpt.sba.scms.dto.request.CreateOrderRequest;
import edu.fpt.sba.scms.dto.request.SepayWebhookRequest;
import edu.fpt.sba.scms.dto.response.OrderResponse;
import edu.fpt.sba.scms.dto.response.PaymentStatusResponse;
import edu.fpt.sba.scms.dto.response.VietQrResponse;
import edu.fpt.sba.scms.security.principal.UserPrincipal;

/**
 * Nghiệp vụ thanh toán VietQR (UC43) và đối soát Webhook SePay (UC44, UC20).
 */
public interface PaymentService {

    /** Tạo đơn hàng tại quầy POS để chuẩn bị thanh toán. */
    OrderResponse createOrder(CreateOrderRequest request, UserPrincipal principal);

    /** Tạo mã VietQR động theo đơn hàng dựa trên PaymentConfig (UC43). */
    VietQrResponse generateVietQr(Integer orderId, UserPrincipal principal);

    /** Tiếp nhận và đối soát Webhook từ cổng thanh toán SePay (UC44, UC20). */
    PaymentStatusResponse processSepayWebhook(SepayWebhookRequest payload, String authHeader, String rawPayload);

    /** Tra cứu trạng thái thanh toán của đơn hàng (dành cho POS kiểm tra/polling) (UC20). */
    PaymentStatusResponse getPaymentStatus(Integer orderId);
}

