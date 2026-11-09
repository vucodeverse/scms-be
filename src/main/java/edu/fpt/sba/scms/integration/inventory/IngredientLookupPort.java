package edu.fpt.sba.scms.integration.inventory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Cổng đọc nguyên liệu cho module menu (UC12 — hiển thị & cập nhật công thức định lượng BOM).
 */
public interface IngredientLookupPort {

    /**
     * @return {@code ingredientId -> thông tin nguyên liệu}; id không tồn tại không có trong map
     */
    Map<Integer, IngredientInfo> findByIds(List<Integer> ingredientIds);

    record IngredientInfo(
            Integer id,
            String name,
            String baseUnit,
            BigDecimal unitCost,
            String status
    ) {
        public IngredientInfo(Integer id, String name, String baseUnit) {
            this(id, name, baseUnit, BigDecimal.ZERO, "ACTIVE");
        }
    }
}
