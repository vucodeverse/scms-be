package edu.fpt.sba.scms.dto.response.report;

import edu.fpt.sba.scms.common.enums.CashTransactionType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Một lần nạp / rút / hoàn tiền mặt trong ca, kèm lý do (UC37).
 */
public record CashMovementDto(
        CashTransactionType type,
        BigDecimal amount,
        String reason,
        Instant createdAt) {
}
