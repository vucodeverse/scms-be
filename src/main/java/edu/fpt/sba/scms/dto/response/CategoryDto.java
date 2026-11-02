package edu.fpt.sba.scms.dto.response;

import edu.fpt.sba.scms.common.enums.RecordStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** Dòng danh sách {@code GET /menu-categories} (UC09). */
@Schema(name = "CategoryDto")
public record CategoryDto(
        Integer id,
        String code,
        String name,
        String description,
        Integer sortOrder,
        RecordStatus status,
        @Schema(description = "Số sản phẩm chưa bị xoá cứng trong danh mục") long productCount,
        Instant createdAt,
        Instant updatedAt) {
}
