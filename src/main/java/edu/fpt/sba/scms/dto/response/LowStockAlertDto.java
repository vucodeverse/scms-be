package edu.fpt.sba.scms.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * DTO cảnh báo thiếu hàng & gợi ý nhập bù nhanh (UC33 §4.2).
 */
@Schema(name = "LowStockAlertDto")
public record LowStockAlertDto(
        @Schema(description = "Mã nguyên vật liệu", example = "5")
        Long ingredientId,

        @Schema(description = "Mã hiển thị nguyên liệu", example = "ING-005")
        String ingredientCode,

        @Schema(description = "Tên nguyên vật liệu", example = "Hạt Cà phê Robusta Đắk Lắk")
        String ingredientName,

        @Schema(description = "Đơn vị đóng gói / kiện lớn (BR-40)", example = "Bao 5kg")
        String packUnit,

        @Schema(description = "Đơn vị cơ sở / định lượng", example = "g")
        String baseUnit,

        @Schema(description = "Quy cách đóng gói (số lượng base / kiện)", example = "5000.0")
        BigDecimal unitsPerPack,

        @Schema(description = "Số lượng tồn hiện tại theo đơn vị cơ sở", example = "12000.0")
        BigDecimal currentQtyBase,

        @Schema(description = "Định mức tồn an toàn tối thiểu (BR-45)", example = "20000.0")
        BigDecimal safetyStockLevel,

        @Schema(description = "Số lượng thiếu hụt cần bù (safetyStockLevel - currentQtyBase)", example = "8000.0")
        BigDecimal deficitQtyBase,

        @Schema(description = "Gợi ý số kiện cần đặt hàng (làm tròn lên CEIL)", example = "2")
        Integer suggestedPackRestock,

        @Schema(description = "Ước tính chi phí nhập bù (suggestedPacks * unitsPerPack * unitCost)", example = "1500000")
        BigDecimal estimatedRestockCost
) {
}
