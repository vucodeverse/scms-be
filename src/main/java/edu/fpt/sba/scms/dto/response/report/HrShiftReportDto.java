package edu.fpt.sba.scms.dto.response.report;

import java.time.LocalDate;
import java.util.List;

/**
 * Báo cáo nhân sự &amp; ca làm việc chi nhánh (UC39).
 */
public record HrShiftReportDto(
        Integer branchId,
        String branchName,
        LocalDate from,
        LocalDate to,
        boolean empty,
        HrSummaryDto summary,
        List<EmployeeHrDto> employees) implements ReportResult {
}
