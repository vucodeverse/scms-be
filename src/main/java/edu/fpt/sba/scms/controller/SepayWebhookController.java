package edu.fpt.sba.scms.controller;

import edu.fpt.sba.scms.dto.request.SepayWebhookRequest;
import edu.fpt.sba.scms.dto.response.PaymentStatusResponse;
import edu.fpt.sba.scms.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller tiếp nhận Webhook từ cổng thanh toán SePay (UC44, UC20, BR-29).
 */
@RestController
@RequestMapping("/api/v1/webhooks/sepay")
@RequiredArgsConstructor
@Tag(name = "17. SePay Webhook", description = "Tiếp nhận webhook thanh toán tự động từ SePay (UC44)")
public class SepayWebhookController {

    private final PaymentService paymentService;

    @Operation(summary = "Tiếp nhận Webhook biến động số dư từ SePay (UC44)",
            description = "Xác thực chữ ký/API token, chống xử lý trùng lặp, khớp mã đơn hàng CAFE{orderId} và tự động chuyển trạng thái đơn hàng sang PAID.")
    @PostMapping
    public PaymentStatusResponse handleSepayWebhook(
            @RequestBody SepayWebhookRequest payload,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String rawPayload = payload != null ? payload.toString() : "";
        return paymentService.processSepayWebhook(payload, authHeader, rawPayload);
    }
}

