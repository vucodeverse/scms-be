package edu.fpt.sba.scms.dto.response;

import edu.fpt.sba.scms.common.enums.ProductSizeName;
import edu.fpt.sba.scms.common.enums.ProductStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/** Dòng danh sách {@code GET /products} (UC10) — kèm size/topping theo §10.7. */
@Schema(name = "ProductDto")
public record ProductDto(
        Integer id,
        String name,
        Integer categoryId,
        String categoryName,
        String categoryCode,
        String description,
        String imageUrl,
        ProductSizeName defaultSize,
        ProductStatus status,
        List<ProductSizeDto> sizes,
        List<ProductToppingDto> toppings,
        Instant createdAt,
        Instant updatedAt) {
}
