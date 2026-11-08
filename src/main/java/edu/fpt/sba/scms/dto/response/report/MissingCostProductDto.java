package edu.fpt.sba.scms.dto.response.report;

import edu.fpt.sba.scms.common.enums.CostDataGap;

/**
 * Sản phẩm không tính được giá vốn (UC40 AT2).
 */
public record MissingCostProductDto(
        Integer productId,
        String productName,
        String size,
        CostDataGap reason) {
}
