package edu.fpt.sba.scms.dto.response.report;

import java.math.BigDecimal;

/**
 * Một sản phẩm trong bảng xếp hạng bán hàng (UC35, UC41).
 */
public record ProductSalesItemDto(
        int rank,
        Integer productId,
        String productName,
        long quantity,
        BigDecimal revenue,
        BigDecimal quantitySharePercent) {
}
