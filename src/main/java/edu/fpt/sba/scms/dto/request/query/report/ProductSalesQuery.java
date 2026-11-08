package edu.fpt.sba.scms.dto.request.query.report;

import edu.fpt.sba.scms.common.enums.ProductSalesRanking;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Bộ lọc báo cáo sản phẩm bán chạy / bán chậm (UC41). {@code branchId} trống = toàn chuỗi.
 */
@Schema(name = "ProductSalesQuery")
public record ProductSalesQuery(
        @Positive(message = "Mã chi nhánh không hợp lệ.")
        @Schema(description = "Chi nhánh; bỏ trống = toàn chuỗi.", example = "1")
        Integer branchId,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Schema(description = "Từ ngày (giờ VN, bao gồm).", example = "2026-11-01")
        LocalDate from,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Schema(description = "Đến ngày (giờ VN, bao gồm).", example = "2026-11-30")
        LocalDate to,

        @Schema(description = "BEST_SELLING (mặc định) hoặc SLOW_SELLING.", example = "BEST_SELLING")
        ProductSalesRanking ranking,

        @Min(value = 1, message = "Số sản phẩm phải từ 1 trở lên.")
        @Max(value = 100, message = "Số sản phẩm tối đa là 100.")
        @Schema(description = "Số sản phẩm hiển thị (mặc định 10 — UC41 BR-03).", example = "10")
        Integer limit) implements ReportPeriodFilter {
}
