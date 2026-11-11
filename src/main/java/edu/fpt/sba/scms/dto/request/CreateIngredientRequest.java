package edu.fpt.sba.scms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(name = "CreateIngredientRequest", description = "Dữ liệu tạo mặt hàng nguyên vật liệu mới")
public record CreateIngredientRequest(
        @Schema(description = "Tên nguyên vật liệu", example = "Hạt Cà phê Robusta")
        @NotBlank(message = "Tên nguyên vật liệu không được để trống.")
        @Size(max = 150, message = "Tên nguyên vật liệu tối đa 150 ký tự.")
        String name,

        @Schema(description = "Đơn vị cơ sở (g, ml, cái...)", example = "g")
        @NotBlank(message = "Đơn vị cơ sở không được để trống.")
        @Size(max = 20, message = "Đơn vị cơ sở tối đa 20 ký tự.")
        String baseUnit,

        @Schema(description = "Đơn vị đóng gói (Bao 5kg, Thùng 12 hộp...)", example = "Bao 5kg")
        @NotBlank(message = "Đơn vị đóng gói không được để trống.")
        @Size(max = 20, message = "Đơn vị đóng gói tối đa 20 ký tự.")
        String packUnit,

        @Schema(description = "Số lượng đơn vị cơ sở trong 1 kiện đóng gói", example = "5000.0000")
        @NotNull(message = "Hệ số quy đổi đơn vị không được để trống.")
        @DecimalMin(value = "0.0001", message = "Hệ số quy đổi phải lớn hơn 0.")
        BigDecimal unitsPerPack,

        @Schema(description = "Đơn giá cơ sở", example = "150.00")
        @NotNull(message = "Đơn giá cơ sở không được để trống.")
        @DecimalMin(value = "0.00", message = "Đơn giá cơ sở không được âm.")
        BigDecimal unitCost
) {
}
