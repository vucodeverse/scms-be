package edu.fpt.sba.scms.dto.response.report;

import edu.fpt.sba.scms.common.enums.StockCountStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Xuất - nhập - tồn - hao hụt một nguyên liệu (UC38). Số lượng theo đơn vị cơ sở; {@code closingPacks} quy đổi ra đơn vị kiện (BR-40/41). {@code issued}/{@code disposed} là số dương (lượng ra), {@code adjusted} có dấu.
 */
public record InventoryItemDto(
        Long ingredientId,
        String name,
        String baseUnit,
        String packUnit,
        BigDecimal unitsPerPack,
        BigDecimal openingQty,
        BigDecimal receivedQty,
        BigDecimal issuedQty,
        BigDecimal disposedQty,
        BigDecimal adjustedQty,
        BigDecimal closingQty,
        BigDecimal closingPacks,
        BigDecimal currentQty,
        BigDecimal safetyStockLevel,
        boolean lowStock,
        StockCountStatus countStatus,
        Instant lastCountedAt,
        BigDecimal lastBookQty,
        BigDecimal lastCountedQty,
        BigDecimal countVariance) {
}
