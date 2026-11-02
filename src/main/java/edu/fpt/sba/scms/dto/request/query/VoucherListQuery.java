package edu.fpt.sba.scms.dto.request.query;

import edu.fpt.sba.scms.common.enums.RecordStatus;
import edu.fpt.sba.scms.common.enums.SortDirection;
import edu.fpt.sba.scms.dto.PageQueryDefaults;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * {@code GET /api/v1/vouchers} (UC13, IMPLEMENTATION.md §10.8).
 *
 * @param sortBy code | startDate | endDate | createdAt (mặc định createdAt giảm dần — voucher mới lên đầu)
 */
@Schema(name = "VoucherListQuery")
public record VoucherListQuery(
        @Size(max = 100, message = "Từ khoá tối đa 100 ký tự.")
        @Schema(description = "Tìm theo mã voucher", example = "KHAITRUONG")
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

        @Schema(defaultValue = "DESC")
        SortDirection sortDir,

        @Schema(description = "Trường sắp xếp", defaultValue = "createdAt")
        String sortBy) {
}
