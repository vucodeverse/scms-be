package edu.fpt.sba.scms.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/** Topping gắn với sản phẩm (UC11). */
@Schema(name = "ProductToppingDto")
public record ProductToppingDto(
        Integer toppingId,
        String name,
        @Schema(description = "Giá cộng thêm khi mua sản phẩm này (VND)") BigDecimal extraPrice,
        boolean isDefault) {
}
