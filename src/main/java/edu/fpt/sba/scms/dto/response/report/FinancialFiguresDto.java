package edu.fpt.sba.scms.dto.response.report;

import java.math.BigDecimal;

/**
 * Doanh thu - giá vốn - lãi gộp (UC40). {@code grossMarginPercent = null} khi doanh thu = 0.
 */
public record FinancialFiguresDto(
        long orderCount,
        BigDecimal revenue,
        BigDecimal cogs,
        BigDecimal grossProfit,
        BigDecimal grossMarginPercent) {
}
