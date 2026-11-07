package edu.fpt.sba.scms.dto.request.query;

import edu.fpt.sba.scms.common.enums.OrderStatus;
import edu.fpt.sba.scms.common.enums.SortDirection;
import edu.fpt.sba.scms.dto.PageQueryDefaults;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.Instant;

@Schema(name = "OrderListQuery", description = "Query danh sách đơn hàng (UC18, §10.11)")
public record OrderListQuery(
        @Schema(description = "ID chi nhánh (nếu COO lọc, các role khác tự động gán theo token)")
        Integer branchId,

        @Schema(description = "Lọc theo trạng thái đơn hàng")
        OrderStatus status,

        @Schema(description = "Từ thời điểm (ISO-8601 UTC)")
        Instant fromDate,

        @Schema(description = "Đến thời điểm (ISO-8601 UTC)")
        Instant toDate,

        @Schema(description = "Tìm kiếm theo số hóa đơn (receiptNo) hoặc ghi chú")
        String keyword,

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
        String sortBy
) {
}
