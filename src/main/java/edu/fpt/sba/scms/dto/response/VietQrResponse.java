package edu.fpt.sba.scms.dto.response;

import edu.fpt.sba.scms.common.enums.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(name = "VietQrResponse", description = "Mã VietQR động tạo cho đơn hàng (UC43, POP-02)")
public record VietQrResponse(
        Integer orderId,
        String referenceCode,
        BigDecimal amount,
        String bankName,
        String accountNumber,
        String accountHolder,
        @Schema(description = "URL ảnh mã QR để hiển thị trực tiếp lên màn hình POS")
        String qrImageUrl,
        @Schema(description = "Chuỗi payload VietQR chuẩn EMVCo")
        String qrPayload,
        Instant expiresAt,
        PaymentStatus status
) {
}

