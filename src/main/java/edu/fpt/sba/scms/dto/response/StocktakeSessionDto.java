package edu.fpt.sba.scms.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record StocktakeSessionDto(
        Long stocktakeId,
        String slipCode,
        Integer branchId,
        String branchName,
        String roundType,
        String status,
        String startedBy,
        String witnessName,
        Instant startedAt,
        Integer totalItems,
        List<StocktakeLineDto> lines
) {
    public record StocktakeLineDto(
            Long ingredientId,
            String ingredientCode,
            String ingredientName,
            String packUnit,
            String baseUnit,
            BigDecimal bookQuantity,
            BigDecimal bookPackUnits,
            BigDecimal countedQuantity,
            BigDecimal variance
    ) {
    }
}
