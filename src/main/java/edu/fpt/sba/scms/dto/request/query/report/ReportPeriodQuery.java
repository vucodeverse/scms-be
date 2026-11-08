package edu.fpt.sba.scms.dto.request.query.report;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Query cơ bản của report: {@code ?branchId=&from=yyyy-MM-dd&to=yyyy-MM-dd}.
 * <p>
 * Kiểm {@code from <= to} và độ dài khoảng nằm ở {@code service.report.ReportPeriod} (cần cấu hình
 * {@code app.report.max-range-days}, mà tầng dto không được đọc config).
 */
@Schema(name = "ReportPeriodQuery")
public record ReportPeriodQuery(
        @Positive(message = "Mã chi nhánh không hợp lệ.")
        @Schema(description = "Chi nhánh. BM: bỏ trống hoặc đúng chi nhánh của mình. COO: bắt buộc với report cấp chi nhánh.",
                example = "1")
        Integer branchId,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Schema(description = "Từ ngày (giờ VN, bao gồm). Bỏ trống = bằng 'to', cả hai trống = hôm nay.",
                example = "2026-11-01")
        LocalDate from,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Schema(description = "Đến ngày (giờ VN, bao gồm). Bỏ trống = bằng 'from'.", example = "2026-11-05")
        LocalDate to) implements ReportPeriodFilter {
}
