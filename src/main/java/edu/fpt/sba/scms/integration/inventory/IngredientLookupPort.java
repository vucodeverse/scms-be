package edu.fpt.sba.scms.integration.inventory;

import java.util.List;
import java.util.Map;

/**
 * Cổng đọc nguyên liệu cho module menu (UC12 — hiển thị công thức). Module kho (M09) chưa có code nên chưa có
 * {@code Ingredient} trong {@code model/}; port này giữ service menu không phụ thuộc repository của module khác
 * (IMPLEMENTATION.md §5.1).
 * <p>
 * Khi M09 triển khai, thêm một adapter (bean) hiện thực cổng này; module menu không phải sửa.
 */
public interface IngredientLookupPort {

    /**
     * @return {@code ingredientId -> thông tin nguyên liệu}; id không tồn tại không có trong map
     */
    Map<Integer, IngredientInfo> findByIds(List<Integer> ingredientIds);

    record IngredientInfo(Integer id, String name, String baseUnit) {
    }
}
