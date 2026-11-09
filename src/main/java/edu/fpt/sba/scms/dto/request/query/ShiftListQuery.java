package edu.fpt.sba.scms.dto.request.query;

import edu.fpt.sba.scms.common.enums.ShiftStatus;
import edu.fpt.sba.scms.common.enums.SortDirection;
import edu.fpt.sba.scms.dto.PageQueryDefaults;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Schema(name = "ShiftListQuery", description = "Query danh sách lịch sử ca làm việc & đối soát két tiền")
public record ShiftListQuery(
        @Schema(description = "ID chi nhánh (COO chọn chi nhánh; các vai trò chi nhánh tự giới hạn theo token)")
        Integer branchId,

        @Schema(description = "Lọc theo trạng thái ca (SCHEDULED | OPEN | CLOSED)")
        ShiftStatus status,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Schema(description = "Từ ngày làm việc (yyyy-MM-dd)", example = "2026-11-01")
        LocalDate startDate,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Schema(description = "Đến ngày làm việc (yyyy-MM-dd)", example = "2026-11-30")
        LocalDate endDate,

        @Min(value = 1, message = "Trang phải bắt đầu từ 1.")
        @Schema(defaultValue = PageQueryDefaults.DEFAULT_PAGE, minimum = "1")
        Integer page,

        @Min(value = 1, message = "Số phần tử mỗi trang phải >= 1.")
        @Max(value = PageQueryDefaults.MAX_SIZE, message = "Số phần tử mỗi trang tối đa 100.")
        @Schema(defaultValue = PageQueryDefaults.DEFAULT_SIZE, maximum = "100")
        Integer size,

        @Schema(defaultValue = "DESC")
        SortDirection sortDir
) {
}
