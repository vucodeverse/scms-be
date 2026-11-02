package edu.fpt.sba.scms.dto.request.query;

import edu.fpt.sba.scms.common.enums.RecordStatus;
import edu.fpt.sba.scms.common.enums.SortDirection;
import edu.fpt.sba.scms.dto.PageQueryDefaults;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * {@code GET /api/v1/menu-categories} (UC09, IMPLEMENTATION.md §10.7).
 * Record chỉ giữ dữ liệu đầu vào; phần xử lý chung (trim, mặc định, quy đổi sort) nằm ở {@code CategorySpecs}.
 *
 * @param sortBy sortOrder | code | name | createdAt (mặc định sortOrder)
 */
@Schema(name = "CategoryListQuery")
public record CategoryListQuery(
        @Size(max = 100, message = "Từ khoá tối đa 100 ký tự.")
        @Schema(description = "Tìm theo mã hoặc tên danh mục", example = "cà phê")
        String keyword,

        @Schema(description = "Lọc theo trạng thái")
        RecordStatus status,

        @Min(value = 1, message = "Trang phải bắt đầu từ 1.")
        @Schema(defaultValue = PageQueryDefaults.DEFAULT_PAGE, minimum = "1")
        Integer page,

        @Min(value = 1, message = "Số phần tử mỗi trang phải >= 1.")
        @Max(value = PageQueryDefaults.MAX_SIZE, message = "Số phần tử mỗi trang tối đa 100.")
        @Schema(defaultValue = PageQueryDefaults.DEFAULT_SIZE, maximum = "100")
        Integer size,

        @Schema(defaultValue = "ASC")
        SortDirection sortDir,

        @Schema(description = "Trường sắp xếp", defaultValue = "sortOrder")
        String sortBy) {
}
