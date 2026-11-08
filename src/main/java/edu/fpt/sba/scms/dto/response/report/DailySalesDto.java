package edu.fpt.sba.scms.dto.response.report;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Doanh số một ngày (giờ VN) — điểm trên biểu đồ.
 */
public record DailySalesDto(
        LocalDate date,
        long orderCount,
        BigDecimal revenue) {
}
