package edu.fpt.sba.scms.dto.response;

import edu.fpt.sba.scms.common.enums.ProductSizeName;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Một dòng công thức {@code GET /products/{id}/recipe} (UC12, §10.7).
 * READ-ONLY trong phạm vi master data: API ghi công thức thuộc hạng mục M06 còn lại (chưa triển khai).
 */
@Schema(name = "RecipeLineDto")
public record RecipeLineDto(
        Integer ingredientId,
        String ingredientName,
        @Schema(description = "Đơn vị gốc của nguyên liệu", example = "g") String baseUnit,
        @Schema(description = "Định lượng theo đơn vị gốc", example = "18") BigDecimal quantityBase,
        @Schema(description = "null = áp cho mọi size") ProductSizeName size) {
}
