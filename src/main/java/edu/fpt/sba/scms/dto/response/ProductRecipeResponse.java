package edu.fpt.sba.scms.dto.response;

import edu.fpt.sba.scms.common.enums.ProductSizeName;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/**
 * Phản hồi chi tiết công thức định lượng và giá vốn ước tính (COGS) của sản phẩm (UC12).
 */
@Schema(name = "ProductRecipeResponse", description = "Công thức và chi phí giá vốn ước tính")
public record ProductRecipeResponse(
        @Schema(description = "Mã sản phẩm", example = "1")
        Integer productId,

        @Schema(description = "Tên sản phẩm", example = "Trà Đào Cam Sả")
        String productName,

        @Schema(description = "Size áp dụng (null = công thức chung)", example = "M")
        ProductSizeName size,

        @Schema(description = "Tổng giá vốn định lượng ước tính (COGS)", example = "15200.00")
        BigDecimal estimatedCogs,

        @Schema(description = "Danh sách chi tiết các dòng nguyên liệu trong công thức")
        List<RecipeLineDto> items
) {
}

