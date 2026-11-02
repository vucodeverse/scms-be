package edu.fpt.sba.scms.dto.request;

import edu.fpt.sba.scms.common.enums.ProductSizeName;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Body {@code POST /products/{id}/sizes} (UC11, §10.7) — upsert theo size: đã có thì cập nhật giá.
 *
 * @param price VND, số nguyên, 0 ≤ price ≤ 100.000.000
 */
@Schema(name = "SaveProductSizeRequest")
public record SaveProductSizeRequest(
        @NotNull(message = "Size là bắt buộc.")
        ProductSizeName size,

        @NotNull(message = "Giá là bắt buộc.")
        @DecimalMin(value = "0", message = "Giá không được âm.")
        @DecimalMax(value = "100000000", message = "Giá tối đa 100.000.000 VND.")
        @Digits(integer = 19, fraction = 0, message = "Giá phải là số nguyên VND.")
        BigDecimal price) {
}
