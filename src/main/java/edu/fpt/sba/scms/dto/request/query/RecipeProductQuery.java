package edu.fpt.sba.scms.dto.request.query;

import edu.fpt.sba.scms.common.enums.ProductSizeName;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code GET /api/v1/products/{id}/recipe?size=} (UC12 — chỉ đọc, đặc tả ở §10.7).
 *
 * @param size null = công thức áp cho mọi size
 */
@Schema(name = "RecipeProductQuery")
public record RecipeProductQuery(
        @Schema(description = "Size cần xem; bỏ trống = công thức chung cho mọi size")
        ProductSizeName size) {
}
