package edu.fpt.sba.scms.dto.response.report;

import java.math.BigDecimal;

/**
 * Tổng hợp nhân sự toàn chi nhánh (UC39).
 */
public record HrSummaryDto(
        int employeeCount,
        int shiftsScheduled,
        int shiftsWorked,
        int absentCount,
        int attendanceCount,
        BigDecimal hoursWorked,
        int lateCount,
        int earlyLeaveCount,
        int missingCheckoutCount) {
}
