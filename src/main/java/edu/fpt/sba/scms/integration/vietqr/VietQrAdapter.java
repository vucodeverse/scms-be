package edu.fpt.sba.scms.integration.vietqr;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Adapter hiện thực cổng sinh mã VietQR QuickLink (UC43).
 */
@Slf4j
@Component
public class VietQrAdapter implements VietQrPort {

    @Override
    public VietQrData generateQr(VietQrCommand command) {
        String encodedHolder = URLEncoder.encode(
                command.accountHolder() != null ? command.accountHolder().trim() : "",
                StandardCharsets.UTF_8
        );
        String bank = normalizeBank(command.bankName());
        String accountNo = command.accountNumber() != null ? command.accountNumber().trim() : "";
        BigDecimal amount = command.amount() != null ? command.amount() : BigDecimal.ZERO;
        String memo = command.referenceCode() != null ? command.referenceCode().trim() : "";
        String encodedMemo = URLEncoder.encode(memo, StandardCharsets.UTF_8);

        String qrImageUrl = String.format(
                "https://img.vietqr.io/image/%s-%s-compact2.png?amount=%s&addInfo=%s&accountName=%s",
                bank, accountNo, amount.toPlainString(), encodedMemo, encodedHolder
        );

        String qrPayload = String.format(
                "vietqr://transfer?bank=%s&acc=%s&amount=%s&memo=%s",
                bank, accountNo, amount.toPlainString(), encodedMemo
        );

        log.debug("Tạo VietQR thành công: bank={}, acc={}, amount={}, memo={}", bank, accountNo, amount, memo);
        return new VietQrData(qrImageUrl, qrPayload);
    }

    private String normalizeBank(String bank) {
        if (bank == null || bank.isBlank()) {
            return "VPBank";
        }
        String trimmed = bank.trim();
        if (trimmed.equalsIgnoreCase("vpbank") || trimmed.equalsIgnoreCase("vpb")) {
            return "VPBank";
        }
        return trimmed;
    }
}

