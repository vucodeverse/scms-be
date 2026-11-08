package edu.fpt.sba.scms.dto.response.report;

import java.math.BigDecimal;

/**
 * Một khung giờ 1 tiếng (giờ VN) trong giờ hoạt động (UC42).
 */
public record HourSlotDto(
        int hour,
        String label,
        long orderCount,
        BigDecimal revenue,
        BigDecimal averageOrdersPerDay,
        boolean peak) {
}
