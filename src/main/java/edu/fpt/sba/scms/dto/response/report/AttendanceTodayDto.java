package edu.fpt.sba.scms.dto.response.report;

/**
 * Chấm công hôm nay (UC34).
 */
public record AttendanceTodayDto(
        int scheduledEmployees,
        int checkedInEmployees,
        int onDutyNow,
        int lateCount,
        int absentCount,
        int missingCheckoutCount) {
}
