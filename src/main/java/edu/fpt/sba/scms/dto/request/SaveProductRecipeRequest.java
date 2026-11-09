package edu.fpt.sba.scms.dto.request;

import edu.fpt.sba.scms.common.enums.ProductSizeName;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Yêu cầu lưu / cập nhật công thức định lượng (BOM) cho sản phẩm (UC12).
 */
@Schema(name = "SaveProductRecipeRequest", description = "Dữ liệu cấu hình công thức định lượng sản phẩm")
public record SaveProductRecipeRequest(
        @Schema(description = "Size áp dụng (null = áp dụng chung cho mọi size)", example = "M")
        ProductSizeName size,

        @NotNull(message = "Danh sách nguyên liệu không được để trống")
        @Valid
        @Schema(description = "Danh sách định lượng các nguyên liệu")
        List<RecipeItemRequest> items
) {
}

