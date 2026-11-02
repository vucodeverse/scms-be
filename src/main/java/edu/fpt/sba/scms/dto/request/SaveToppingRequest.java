package edu.fpt.sba.scms.dto.request;

import edu.fpt.sba.scms.common.enums.RecordStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Body {@code POST/PUT /toppings} (UC11, §10.7). */
@Schema(name = "SaveToppingRequest")
public record SaveToppingRequest(
        @NotBlank(message = "Tên topping là bắt buộc.")
        @Size(max = 100, message = "Tên topping tối đa 100 ký tự.")
        String name,

        @NotNull(message = "Giá topping là bắt buộc.")
        @DecimalMin(value = "0", message = "Giá không được âm.")
        @DecimalMax(value = "100000000", message = "Giá tối đa 100.000.000 VND.")
        @Digits(integer = 19, fraction = 0, message = "Giá phải là số nguyên VND.")
        BigDecimal defaultPrice,

        @NotNull(message = "Trạng thái là bắt buộc.")
        RecordStatus status) {
}
