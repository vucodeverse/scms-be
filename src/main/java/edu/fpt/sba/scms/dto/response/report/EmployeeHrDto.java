package edu.fpt.sba.scms.dto.response.report;

import java.math.BigDecimal;

/**
 * Tổng hợp chấm công / ca của một nhân viên (UC39). Chỉ số năng suất chỉ có với Thu ngân (BR-09/10), không chứa lương (BR-47).
 */
public record EmployeeHrDto(
        Integer userId,
        String fullName,
        String roleCode,
        int shiftsScheduled,
        int shiftsWorked,
        int absentCount,
        int attendanceCount,
        BigDecimal hoursWorked,
        int lateCount,
        int earlyLeaveCount,
        int missingCheckoutCount,
        Long ordersProcessed,
        BigDecimal revenue,
        BigDecimal revenuePerHour) {
}
