package edu.fpt.sba.scms.integration.inventory;

import edu.fpt.sba.scms.model.Ingredient;
import edu.fpt.sba.scms.repository.IngredientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Adapter hiện thực cổng {@link IngredientLookupPort} để truy vấn dữ liệu nguyên vật liệu
 * từ phân hệ Kho (M09) phục vụ phân hệ Menu/Sản phẩm (UC12).
 */
@Component
@RequiredArgsConstructor
public class IngredientLookupAdapter implements IngredientLookupPort {

    private final IngredientRepository ingredientRepository;

    @Override
    public Map<Integer, IngredientInfo> findByIds(List<Integer> ingredientIds) {
        if (ingredientIds == null || ingredientIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> longIds = ingredientIds.stream()
                .filter(id -> id != null)
                .map(Integer::longValue)
                .distinct()
                .toList();

        if (longIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Ingredient> ingredients = ingredientRepository.findAllById(longIds);

        return ingredients.stream()
                .filter(ing -> ing.getId() != null)
                .collect(Collectors.toMap(
                        ing -> ing.getId().intValue(),
                        ing -> new IngredientInfo(
                                ing.getId().intValue(),
                                ing.getName(),
                                ing.getBaseUnit()
                        ),
                        (existing, replacement) -> existing
                ));
    }
}
