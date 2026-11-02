package edu.fpt.sba.scms.dto.response;

import edu.fpt.sba.scms.common.enums.RecordStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Chi tiết {@code GET /menu-categories/{id}} — kèm danh sách sản phẩm (UC09, §10.7). */
@Schema(name = "CategoryDetailDto")
public record CategoryDetailDto(
        Integer id,
        String code,
        String name,
        String description,
        Integer sortOrder,
        @Schema(description = "Danh mục hệ thống (UNCAT): không sửa, không xoá") boolean systemCategory,
        List<ProductDto> products) {
}
