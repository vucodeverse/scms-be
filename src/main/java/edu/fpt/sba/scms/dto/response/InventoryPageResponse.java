package edu.fpt.sba.scms.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Schema mô tả trang dữ liệu sổ kho cho Swagger UI / OpenAPI (UC33 §4.1).
 */
@Schema(name = "InventoryPageResponse", description = "Trang dữ liệu sổ kho chi nhánh")
public record InventoryPageResponse(
        @Schema(description = "Danh sách chi tiết tồn kho các nguyên vật liệu")
        List<IngredientStockDto> content,

        @Schema(description = "Chỉ số trang hiện tại (0-based)", example = "0")
        int page,

        @Schema(description = "Số lượng phần tử trên mỗi trang", example = "20")
        int size,

        @Schema(description = "Tổng số bản ghi tồn kho", example = "10")
        long totalElements,

        @Schema(description = "Tổng số trang", example = "1")
        int totalPages
) {
}
