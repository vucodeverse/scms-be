package edu.fpt.sba.scms.dto.response.report;

import java.math.BigDecimal;

/**
 * Tổng hợp báo cáo kho (UC38).
 */
public record InventorySummaryDto(
        int ingredientCount,
        int lowStockCount,
        int varianceCount,
        int uncountedCount,
        BigDecimal disposedQtyTotal) {
}
