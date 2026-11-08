package edu.fpt.sba.scms.dto.response.report;

import java.math.BigDecimal;

/**
 * Tổng hợp dòng tiền của kỳ (UC37). {@code countedCash}/{@code totalVariance} chỉ tính các ca đã đối soát.
 */
public record CashFlowSummaryDto(
        int shiftCount,
        int unreconciledCount,
        int varianceShiftCount,
        BigDecimal openingCash,
        BigDecimal cashSales,
        BigDecimal cashIn,
        BigDecimal cashOut,
        BigDecimal cashRefunds,
        BigDecimal expectedCash,
        BigDecimal countedCash,
        BigDecimal totalVariance) {
}
