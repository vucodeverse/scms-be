package edu.fpt.sba.scms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Một dòng nguyên vật liệu trong công thức sản phẩm (UC12).
 */
@Schema(name = "RecipeItemRequest", description = "Định lượng một nguyên liệu trong công thức")
public record RecipeItemRequest(
        @NotNull(message = "Mã nguyên liệu không được để trống")
        @Schema(description = "ID nguyên vật liệu", example = "10")
        Integer ingredientId,

        @NotNull(message = "Định lượng không được để trống")
        @DecimalMin(value = "0.0001", message = "Định lượng phải lớn hơn 0")
        @DecimalMax(value = "1000000", message = "Định lượng quá lớn")
        @Digits(integer = 15, fraction = 4, message = "Định lượng tối đa 4 chữ số thập phân")
        @Schema(description = "Định lượng theo đơn vị cơ sở", example = "18.0000")
        BigDecimal quantityBase
) {
}
