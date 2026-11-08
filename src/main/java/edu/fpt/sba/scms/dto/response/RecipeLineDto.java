package edu.fpt.sba.scms.dto.response;

import edu.fpt.sba.scms.common.enums.ProductSizeName;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Một dòng công thức định lượng nguyên vật liệu (UC12, §10.7).
 */
@Schema(name = "RecipeLineDto")
public record RecipeLineDto(
        Integer ingredientId,
        String ingredientName,
        @Schema(description = "Đơn vị gốc của nguyên liệu", example = "g") String baseUnit,
        @Schema(description = "Định lượng theo đơn vị gốc", example = "18.0000") BigDecimal quantityBase,
        @Schema(description = "null = áp cho mọi size", example = "M") ProductSizeName size,
        @Schema(description = "Đơn giá cơ sở của nguyên liệu", example = "250.00") BigDecimal unitCost,
        @Schema(description = "Chi phí nguyên liệu trong công thức = quantityBase * unitCost", example = "4500.00") BigDecimal lineCost) {

    public RecipeLineDto(Integer ingredientId, String ingredientName, String baseUnit, BigDecimal quantityBase, ProductSizeName size) {
        this(ingredientId, ingredientName, baseUnit, quantityBase, size, null, null);
    }
}
