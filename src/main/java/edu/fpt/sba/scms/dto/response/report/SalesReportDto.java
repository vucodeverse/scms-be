package edu.fpt.sba.scms.dto.response.report;

import edu.fpt.sba.scms.common.enums.PaymentMethod;

import java.time.LocalDate;
import java.util.List;

/**
 * Báo cáo doanh số chi nhánh (UC35).
 */
public record SalesReportDto(
        Integer branchId,
        String branchName,
        LocalDate from,
        LocalDate to,
        Integer shiftId,
        PaymentMethod paymentMethod,
        boolean empty,
        SalesSummaryDto summary,
        List<DailySalesDto> daily,
        List<ShiftSalesDto> shifts,
        List<ProductSalesItemDto> topProducts) implements ReportResult {
}
