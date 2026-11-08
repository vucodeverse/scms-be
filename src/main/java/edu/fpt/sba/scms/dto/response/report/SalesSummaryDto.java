package edu.fpt.sba.scms.dto.response.report;

import java.math.BigDecimal;

/**
 * Thẻ tổng hợp doanh số (UC35). {@code revenue} = tiền khách trả, {@code netRevenue} = sau hoàn tiền.
 */
public record SalesSummaryDto(
        long orderCount,
        BigDecimal grossAmount,
        BigDecimal discountAmount,
        BigDecimal revenue,
        BigDecimal refundAmount,
        BigDecimal netRevenue,
        BigDecimal averageOrderValue) {
}
