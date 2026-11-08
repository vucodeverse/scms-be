package edu.fpt.sba.scms.dto.response.report;

import java.time.LocalDate;
import java.util.List;

/**
 * Báo cáo xuất - nhập - tồn - hao hụt kho chi nhánh (UC38). {@code noTransactions}: kỳ không có giao dịch kho — vẫn hiển thị tồn hiện tại (AT1).
 */
public record InventoryReportDto(
        Integer branchId,
        String branchName,
        LocalDate from,
        LocalDate to,
        boolean empty,
        boolean noTransactions,
        InventorySummaryDto summary,
        List<InventoryItemDto> items) implements ReportResult {
}
