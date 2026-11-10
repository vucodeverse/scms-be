package edu.fpt.sba.scms.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(name = "IngredientDto", description = "Thông tin nguyên vật liệu")
public record IngredientDto(
        @Schema(description = "Mã nguyên vật liệu", example = "1")
        Long id,

        @Schema(description = "Tên nguyên vật liệu", example = "Cà phê Robusta")
        String name,

        @Schema(description = "Đơn vị cơ sở", example = "g")
        String baseUnit,

        @Schema(description = "Đơn vị đóng gói", example = "Bao 5kg")
        String packUnit,

        @Schema(description = "Số lượng đơn vị cơ sở trong 1 kiện đóng gói", example = "5000.0000")
        BigDecimal unitsPerPack,

        @Schema(description = "Đơn giá cơ sở", example = "120.00")
        BigDecimal unitCost,

        @Schema(description = "Trạng thái", example = "ACTIVE")
        String status
) {
}
