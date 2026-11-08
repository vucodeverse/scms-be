package edu.fpt.sba.scms.dto.response.report;

import java.math.BigDecimal;
import java.util.List;

/**
 * KPI doanh số hôm nay so với hôm qua + xu hướng 7 ngày (UC34). Phần trăm = null khi hôm qua = 0.
 */
public record SalesKpiDto(
        long orderCount,
        BigDecimal revenue,
        BigDecimal averageOrderValue,
        long yesterdayOrderCount,
        BigDecimal yesterdayRevenue,
        BigDecimal orderChangePercent,
        BigDecimal revenueChangePercent,
        List<DailySalesDto> last7Days) {
}
