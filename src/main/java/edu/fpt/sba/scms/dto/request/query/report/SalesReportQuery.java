package edu.fpt.sba.scms.dto.request.query.report;

import edu.fpt.sba.scms.common.enums.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Bộ lọc báo cáo doanh số chi nhánh (UC35): khoảng ngày, ca, phương thức thanh toán.
 */
@Schema(name = "SalesReportQuery")
public record SalesReportQuery(
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
        @Schema(description = "Lọc theo một ca làm việc.", example = "12")
        Integer shiftId,

        @Schema(description = "Lọc theo phương thức thanh toán.", example = "CASH")
        PaymentMethod paymentMethod) implements ReportPeriodFilter {
}
