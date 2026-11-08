package edu.fpt.sba.scms.dto.response.report;

import java.util.List;

/**
 * Cảnh báo tồn thấp (UC34): tổng số + tối đa 5 nguyên liệu thiếu nhất.
 */
public record LowStockWidgetDto(
        int lowStockCount,
        List<LowStockItemDto> items) {
}
