package edu.fpt.sba.scms.dto.response.report;

import edu.fpt.sba.scms.common.enums.ProductSalesRanking;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Báo cáo sản phẩm bán chạy / bán chậm (UC41). {@code branchId = null} = toàn chuỗi.
 */
public record ProductSalesReportDto(
        Integer branchId,
        String branchName,
        LocalDate from,
        LocalDate to,
        ProductSalesRanking ranking,
        int limit,
        boolean empty,
        long totalQuantity,
        BigDecimal totalRevenue,
        List<ProductSalesItemDto> items) implements ReportResult {
}
