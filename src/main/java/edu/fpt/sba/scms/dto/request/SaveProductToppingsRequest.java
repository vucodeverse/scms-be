package edu.fpt.sba.scms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** Body {@code PUT /products/{id}/toppings} (UC11, §10.7) — thay toàn bộ danh sách topping của sản phẩm. */
@Schema(name = "SaveProductToppingsRequest")
public record SaveProductToppingsRequest(
        @NotNull(message = "Danh sách topping là bắt buộc.")
        @Size(max = 100, message = "Một sản phẩm tối đa 100 topping.")
        List<@NotNull @Valid Item> items) {

    @Schema(name = "ProductToppingItem")
    public record Item(
            @NotNull(message = "toppingId là bắt buộc.")
            @Min(value = 1, message = "toppingId không hợp lệ.")
            Integer toppingId,

            @NotNull(message = "Giá cộng thêm là bắt buộc.")
            @DecimalMin(value = "0", message = "Giá cộng thêm không được âm.")
            @DecimalMax(value = "100000000", message = "Giá cộng thêm tối đa 100.000.000 VND.")
            @Digits(integer = 19, fraction = 0, message = "Giá phải là số nguyên VND.")
            BigDecimal extraPrice,

            @Schema(description = "Tự chọn sẵn cho mọi ly mua sản phẩm này")
            boolean isDefault) {
    }
}
