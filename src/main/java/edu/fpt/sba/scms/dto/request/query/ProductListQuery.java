package edu.fpt.sba.scms.dto.request.query;

import edu.fpt.sba.scms.common.enums.ProductStatus;
import edu.fpt.sba.scms.common.enums.SortDirection;
import edu.fpt.sba.scms.dto.PageQueryDefaults;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * {@code GET /api/v1/products} (UC10, IMPLEMENTATION.md §10.7).
 *
 * @param sortBy name | basePrice | createdAt (mặc định name)
 */
@Schema(name = "ProductListQuery")
public record ProductListQuery(
        @Size(max = 100, message = "Từ khoá tối đa 100 ký tự.")
        @Schema(description = "Tìm theo tên sản phẩm", example = "trà sữa")
        String keyword,

        @Min(value = 1, message = "categoryId phải > 0.")
        @Schema(description = "Lọc theo danh mục")
        Integer categoryId,

        @Schema(description = "Lọc theo trạng thái")
        ProductStatus status,

        @Min(value = 1, message = "Trang phải bắt đầu từ 1.")
        @Schema(defaultValue = PageQueryDefaults.DEFAULT_PAGE, minimum = "1")
        Integer page,

        @Min(value = 1, message = "Số phần tử mỗi trang phải >= 1.")
        @Max(value = PageQueryDefaults.MAX_SIZE, message = "Số phần tử mỗi trang tối đa 100.")
        @Schema(defaultValue = PageQueryDefaults.DEFAULT_SIZE, maximum = "100")
        Integer size,

        @Schema(defaultValue = "ASC")
        SortDirection sortDir,

        @Schema(description = "Trường sắp xếp", defaultValue = "name")
        String sortBy) {
}
