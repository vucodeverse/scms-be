package edu.fpt.sba.scms.dto.response;

import edu.fpt.sba.scms.common.enums.CashTransactionType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(name = "CashTransactionResponse", description = "Biến động tiền mặt trong két")
public record CashTransactionResponse(
        Integer id,
        Integer shiftId,
        CashTransactionType type,
        BigDecimal amount,
        String reason,
        Integer createdBy,
        Integer approvedBy,
        Boolean isSelfInitiated,
        Instant createdAt
) {
}

