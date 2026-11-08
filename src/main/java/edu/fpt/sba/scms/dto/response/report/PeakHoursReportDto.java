package edu.fpt.sba.scms.dto.response.report;

import java.time.LocalDate;
import java.util.List;

/**
 * Báo cáo khung giờ cao điểm (UC42). {@code branchId = null} = toàn chuỗi.
 */
public record PeakHoursReportDto(
        Integer branchId,
        String branchName,
        LocalDate from,
        LocalDate to,
        boolean empty,
        long totalOrders,
        long dayCount,
        long peakOrderCount,
        List<HourSlotDto> slots) implements ReportResult {
}
