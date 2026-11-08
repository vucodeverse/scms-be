package edu.fpt.sba.scms.dto.response.report;

import java.math.BigDecimal;

/**
 * Nguyên liệu dưới ngưỡng an toàn (BR-45).
 */
public record LowStockItemDto(
        Long ingredientId,
        String name,
        String baseUnit,
        BigDecimal currentQty,
        BigDecimal safetyStockLevel) {
}
