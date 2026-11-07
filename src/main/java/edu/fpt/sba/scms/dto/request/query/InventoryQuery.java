package edu.fpt.sba.scms.dto.request.query;

import edu.fpt.sba.scms.common.enums.SortDirection;
import edu.fpt.sba.scms.dto.PageQueryDefaults;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Param tra cứu sổ kho chi nhánh (UC33).
 */
@Schema(name = "InventoryQuery")
public record InventoryQuery(
        @Schema(description = "Mã chi nhánh (chỉ áp dụng cho quyền COO)")
        Long branchId,

        @Size(max = 100, message = "Từ khoá tối đa 100 ký tự.")
        @Schema(description = "Tìm theo tên nguyên liệu", example = "Cà phê")
        String keyword,

        @Schema(description = "Bộ lọc trạng thái tồn: ALL (tất cả), LOW_STOCK (thiếu hàng), SUFFICIENT (đủ hàng)", defaultValue = "ALL")
        String status,

        @Min(value = 0, message = "Trang phải >= 0.")
        @Schema(defaultValue = "0", description = "Số trang (0-based)", example = "0")
        Integer page,

        @Min(value = 1, message = "Kích thước trang phải >= 1.")
        @Max(value = PageQueryDefaults.MAX_SIZE, message = "Kích thước trang tối đa 100.")
        @Schema(defaultValue = "20", description = "Số phần tử trên một trang", example = "20")
        Integer size,

        @Schema(description = "Trường sắp xếp", defaultValue = "ingredient.name")
        String sortBy,

        @Schema(description = "Thứ tự sắp xếp: ASC | DESC", defaultValue = "ASC")
        SortDirection sortDir
) {
}
