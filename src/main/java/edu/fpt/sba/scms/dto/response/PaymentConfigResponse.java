package edu.fpt.sba.scms.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(name = "PaymentConfigResponse", description = "Thông tin cấu hình thanh toán SePay (UC15)")
public record PaymentConfigResponse(
        Integer id,
        String bankName,
        String accountNumber,
        String accountHolder,
        String paymentPrefix,
        @Schema(description = "Mã token SePay đã được che mờ bảo mật")
        String maskedApiToken,
        String webhookUrl,
        boolean active,
        Instant updatedAt
) {
}

