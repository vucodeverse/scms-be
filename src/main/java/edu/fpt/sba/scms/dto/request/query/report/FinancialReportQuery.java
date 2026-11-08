package edu.fpt.sba.scms.dto.request.query.report;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

/**
 * Bộ lọc báo cáo tài chính hợp nhất (UC40). {@code branchIds} trống = mọi chi nhánh.
 */
@Schema(name = "FinancialReportQuery")
public record FinancialReportQuery(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Schema(description = "Từ ngày (giờ VN, bao gồm).", example = "2026-11-01")
        LocalDate from,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Schema(description = "Đến ngày (giờ VN, bao gồm).", example = "2026-11-30")
        LocalDate to,

        @Schema(description = "Danh sách chi nhánh (?branchIds=1&branchIds=2); bỏ trống = tất cả.")
        List<@Positive(message = "Mã chi nhánh không hợp lệ.") Integer> branchIds) {
}
