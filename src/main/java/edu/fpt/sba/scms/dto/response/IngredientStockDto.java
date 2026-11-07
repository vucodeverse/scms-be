package edu.fpt.sba.scms.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO hiển thị dòng tồn kho chi nhánh (UC33 §4.1).
 */
@Schema(name = "IngredientStockDto")
public record IngredientStockDto(
        @Schema(description = "Mã dòng tồn kho", example = "12")
        Long inventoryId,

        @Schema(description = "Mã nguyên vật liệu", example = "5")
        Long ingredientId,

        @Schema(description = "Mã hiển thị nguyên liệu", example = "ING-005")
        String ingredientCode,

        @Schema(description = "Tên nguyên vật liệu", example = "Hạt Cà phê Robusta Đắk Lắk")
        String ingredientName,

        @Schema(description = "Tên phân loại", example = "Cà phê & Hạt")
        String categoryName,

        @Schema(description = "Đơn vị đóng gói / kiện lớn (BR-40)", example = "Bao 5kg")
        String packUnit,

        @Schema(description = "Đơn vị cơ sở / định lượng pha chế", example = "g")
        String baseUnit,

        @Schema(description = "Hệ số quy đổi (số đơn vị cơ sở trong 1 kiện)", example = "5000.0")
        BigDecimal unitsPerPack,

        @Schema(description = "Số lượng tồn theo kiện lớn", example = "2.4")
        BigDecimal currentPackUnits,

        @Schema(description = "Số lượng tồn theo đơn vị cơ sở", example = "12000.0")
        BigDecimal currentQtyBase,

        @Schema(description = "Định mức tồn kho an toàn (BR-45)", example = "20000.0")
        BigDecimal safetyStockLevel,

        @Schema(description = "Cờ cảnh báo tồn thấp (currentQtyBase <= safetyStockLevel)", example = "true")
        Boolean lowStockFlag,

        @Schema(description = "Đơn giá vốn mỗi đơn vị cơ sở (VND)", example = "150.0")
        BigDecimal unitCost,

        @Schema(description = "Tổng giá trị vốn hàng tồn (currentQtyBase * unitCost)", example = "1800000")
        BigDecimal holdingValue,

        @Schema(description = "Thời điểm cập nhật lần cuối")
        Instant lastUpdated
) {
}
