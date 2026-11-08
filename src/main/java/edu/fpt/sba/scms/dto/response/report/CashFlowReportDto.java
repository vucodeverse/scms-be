package edu.fpt.sba.scms.dto.response.report;

import java.time.LocalDate;
import java.util.List;

/**
 * Báo cáo dòng tiền thu/chi thực tế tại quầy (UC37).
 */
public record CashFlowReportDto(
        Integer branchId,
        String branchName,
        LocalDate from,
        LocalDate to,
        boolean empty,
        CashFlowSummaryDto summary,
        List<ShiftCashFlowDto> shifts) implements ReportResult {
}
