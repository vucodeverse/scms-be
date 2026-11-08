package edu.fpt.sba.scms.integration.vietqr;

import java.math.BigDecimal;

/**
 * Cổng tích hợp dịch vụ tạo mã VietQR động (UC43, SDD).
 */
public interface VietQrPort {

    VietQrData generateQr(VietQrCommand command);

    record VietQrCommand(
            String bankName,
            String accountNumber,
            String accountHolder,
            BigDecimal amount,
            String referenceCode
    ) {
    }

    record VietQrData(
            String qrImageUrl,
            String qrPayload
    ) {
    }
}

