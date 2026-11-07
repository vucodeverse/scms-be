package edu.fpt.sba.scms.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(name = "ShiftSummaryResponse", description = "Báo cáo tổng kết ca và két tiền hiện tại")
public record ShiftSummaryResponse(
        ShiftResponse shift,
        List<CashTransactionResponse> transactions,
        BigDecimal openingCash,
        BigDecimal cashRevenue,
        BigDecimal transferRevenue,
        BigDecimal totalCashIn,
        BigDecimal totalCashOut,
        BigDecimal totalRefundOut,
        BigDecimal currentCashBalance
) {
}

