package edu.fpt.sba.scms.dto.response;

import edu.fpt.sba.scms.common.enums.ProductSizeName;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/** Một size + giá (UC11). */
@Schema(name = "ProductSizeDto")
public record ProductSizeDto(
        Integer id,
        ProductSizeName size,
        @Schema(description = "Giá bán VND", example = "35000") BigDecimal price) {
}
