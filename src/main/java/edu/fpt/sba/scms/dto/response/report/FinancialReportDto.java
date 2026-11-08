package edu.fpt.sba.scms.dto.response.report;

import java.time.LocalDate;
import java.util.List;

/**
 * Báo cáo tài chính hợp nhất toàn chuỗi (UC40).
 */
public record FinancialReportDto(
        LocalDate from,
        LocalDate to,
        boolean empty,
        boolean costIncomplete,
        FinancialFiguresDto chainTotal,
        List<BranchFinancialDto> branches,
        List<MissingCostProductDto> missingCostProducts) implements ReportResult {
}
