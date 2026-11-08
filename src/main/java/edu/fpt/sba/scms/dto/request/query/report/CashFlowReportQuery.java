package edu.fpt.sba.scms.dto.request.query.report;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Bộ lọc báo cáo dòng tiền (UC37): theo ngày (mặc định hôm nay) hoặc theo ca.
 */
@Schema(name = "CashFlowReportQuery")
public record CashFlowReportQuery(
        @Positive(message = "Mã chi nhánh không hợp lệ.")
        @Schema(description = "Chi nhánh. BM: bỏ trống hoặc đúng chi nhánh của mình. COO: bắt buộc.", example = "1")
        Integer branchId,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Schema(description = "Từ ngày (giờ VN, bao gồm). Bỏ trống = bằng 'to', cả hai trống = hôm nay.",
                example = "2026-11-01")
        LocalDate from,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Schema(description = "Đến ngày (giờ VN, bao gồm). Bỏ trống = bằng 'from'.", example = "2026-11-05")
        LocalDate to,

        @Positive(message = "Mã ca không hợp lệ.")
        @Schema(description = "Chỉ xem một ca.", example = "12")
        Integer shiftId) implements ReportPeriodFilter {
}
