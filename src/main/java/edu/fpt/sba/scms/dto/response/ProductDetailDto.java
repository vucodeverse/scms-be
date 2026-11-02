package edu.fpt.sba.scms.dto.response;

import edu.fpt.sba.scms.common.enums.ProductSizeName;
import edu.fpt.sba.scms.common.enums.ProductStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/** Chi tiết {@code GET /products/{id}} (UC10): sản phẩm + bảng giá size + topping + công thức (UC12, chỉ đọc). */
@Schema(name = "ProductDetailDto")
public record ProductDetailDto(
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
        List<RecipeLineDto> recipe,
        Instant createdAt,
        Instant updatedAt) {
}
