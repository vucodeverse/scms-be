package edu.fpt.sba.scms.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record StocktakeReconciliationDto(
        Long stocktakeId,
        String slipCode,
        String status,
        Instant closedAt,
        Integer totalItems,
        Integer matchedCount,
        Integer discrepancyCount,
        BigDecimal netVarianceValue,
        List<AdjustmentDetail> adjustments
) {
    public record AdjustmentDetail(
            Long ingredientId,
            String ingredientName,
            String baseUnit,
            BigDecimal bookQuantity,
            BigDecimal countedQuantity,
            BigDecimal variance,
            BigDecimal unitCost,
            BigDecimal varianceFinancialValue,
            String status,
            String notes
    ) {
    }
}
