package edu.fpt.sba.scms.integration.inventory;

import java.util.List;
import java.util.Map;

/**
 * Adapter mặc định khi module kho (M09) chưa cung cấp nguyên liệu: trả rỗng để menu vẫn hoạt động
 * (công thức hiển thị "chưa có nguyên liệu"). Khi M09 có adapter thật, bean đó thay thế bean này
 * (component loại "thật" đặt {@code @Primary} hoặc đổi bean này thành {@code @ConditionalOnMissingBean}).
 */
final class UnavailableIngredientLookup implements IngredientLookupPort {

    static final UnavailableIngredientLookup INSTANCE = new UnavailableIngredientLookup();

    private UnavailableIngredientLookup() {
    }

    @Override
    public Map<Integer, IngredientInfo> findByIds(List<Integer> ingredientIds) {
        return Map.of();
    }
}
