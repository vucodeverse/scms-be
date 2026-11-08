package edu.fpt.sba.scms.controller;

import edu.fpt.sba.scms.dto.ApiErrorResponse;
import edu.fpt.sba.scms.dto.request.query.report.CashFlowReportQuery;
import edu.fpt.sba.scms.dto.request.query.report.FinancialReportQuery;
import edu.fpt.sba.scms.dto.request.query.report.ProductSalesQuery;
import edu.fpt.sba.scms.dto.request.query.report.ReportPeriodQuery;
import edu.fpt.sba.scms.dto.request.query.report.SalesReportQuery;
import edu.fpt.sba.scms.dto.response.report.CashFlowReportDto;
import edu.fpt.sba.scms.dto.response.report.DashboardDto;
import edu.fpt.sba.scms.dto.response.report.FinancialReportDto;
import edu.fpt.sba.scms.dto.response.report.HrShiftReportDto;
import edu.fpt.sba.scms.dto.response.report.InventoryReportDto;
import edu.fpt.sba.scms.dto.response.report.PeakHoursReportDto;
import edu.fpt.sba.scms.dto.response.report.ProductSalesReportDto;
import edu.fpt.sba.scms.dto.response.report.SalesReportDto;
import edu.fpt.sba.scms.service.DashboardService;
import edu.fpt.sba.scms.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Báo cáo &amp; phân tích (UC34, UC35, UC37–UC42) — chỉ đọc. Ngày lọc theo giờ VN ({@code yyyy-MM-dd}).
 * BM chỉ xem chi nhánh của mình (bỏ trống {@code branchId} hoặc gửi đúng chi nhánh; khác → 403); COO chọn chi nhánh.
 */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Validated
@Tag(name = "22. Reports & Analytics", description = "Dashboard và báo cáo phân tích (UC34–UC42)")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Bộ lọc không hợp lệ (vd. 'Từ ngày' lớn hơn 'Đến ngày')",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Không có quyền / truy cập chi nhánh khác",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Chi nhánh không tồn tại",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
public class ReportController {

    private final ReportService reportService;
    private final DashboardService dashboardService;

    @Operation(summary = "Dashboard chi nhánh trong ngày (UC34)",
            description = "KPI doanh thu/đơn hôm nay so với hôm qua, xu hướng 7 ngày, két tiền ca đang mở, cảnh báo "
                    + "tồn thấp, chấm công hôm nay. Mỗi widget có status OK/ERROR riêng (AT1).")
    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER')")
    public DashboardDto getDashboard(
            @Parameter(description = "Chi nhánh (COO bắt buộc; BM bỏ trống)")
            @RequestParam(required = false) @Positive(message = "Mã chi nhánh không hợp lệ.") Integer branchId) {
        return dashboardService.getBranchDashboard(branchId);
    }

    @Operation(summary = "Báo cáo doanh số chi nhánh (UC35)",
            description = "Chỉ đơn đã thanh toán (BR-46). Tổng hợp, chuỗi theo ngày, payment mix theo ca (TAB-07), "
                    + "top sản phẩm. Lọc theo ca và phương thức thanh toán.")
    @GetMapping("/sales")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER')")
    public SalesReportDto getSalesReport(@ModelAttribute @Valid SalesReportQuery query) {
        return reportService.getSalesReport(query);
    }

    @Operation(summary = "Báo cáo dòng tiền két (UC37)",
            description = "Theo ca: tiền đầu ca, thu tiền mặt, nạp/rút, hoàn tiền, tiền dự kiến (BR-39), thực đếm, "
                    + "chênh lệch (BR-38). Ca chưa đóng = UNRECONCILED. Mặc định hôm nay.")
    @GetMapping("/cash-flow")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER')")
    public CashFlowReportDto getCashFlowReport(@ModelAttribute @Valid CashFlowReportQuery query) {
        return reportService.getCashFlowReport(query);
    }

    @Operation(summary = "Báo cáo xuất - nhập - tồn - hao hụt kho (UC38)",
            description = "Tồn đầu/cuối kỳ, nhập, xuất bar, hủy, điều chỉnh; chênh lệch kiểm kê gần nhất (BR-44), "
                    + "UNCOUNTED khi chưa kiểm kê; tồn thấp (BR-45); quy đổi kiện (BR-40/41).")
    @GetMapping("/inventory")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER')")
    public InventoryReportDto getInventoryReport(@ModelAttribute @Valid ReportPeriodQuery query) {
        return reportService.getInventoryReport(query);
    }

    @Operation(summary = "Báo cáo nhân sự & ca làm việc (UC39)",
            description = "Giờ công, ca phân công/đã làm, vắng, đi muộn, về sớm, quên check-out; năng suất thu ngân. "
                    + "Không chứa lương (BR-47). COO được xem để xuất 'Employee Hours' (UC36).")
    @GetMapping("/hr-shifts")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER')")
    public HrShiftReportDto getHrShiftReport(@ModelAttribute @Valid ReportPeriodQuery query) {
        return reportService.getHrShiftReport(query);
    }

    @Operation(summary = "Báo cáo tài chính hợp nhất toàn chuỗi (UC40 — chỉ COO)",
            description = "Doanh thu, giá vốn (công thức × số ly × đơn giá — BR-19/48), lãi gộp theo chi nhánh và "
                    + "toàn chuỗi. Cảnh báo khi thiếu công thức / đơn giá (AT2).")
    @GetMapping("/consolidated-financial")
    @PreAuthorize("hasAuthority('COO')")
    public FinancialReportDto getConsolidatedFinancialReport(@ModelAttribute @Valid FinancialReportQuery query) {
        return reportService.getConsolidatedFinancialReport(query);
    }

    @Operation(summary = "Báo cáo sản phẩm bán chạy / bán chậm (UC41 — chỉ COO)",
            description = "Xếp hạng theo số lượng bán (BR-49), mặc định top 10. SLOW_SELLING gồm cả món không bán "
                    + "được ly nào. Bỏ trống branchId = toàn chuỗi.")
    @GetMapping("/product-sales")
    @PreAuthorize("hasAuthority('COO')")
    public ProductSalesReportDto getProductSalesReport(@ModelAttribute @Valid ProductSalesQuery query) {
        return reportService.getProductSalesReport(query);
    }

    @Operation(summary = "Báo cáo khung giờ cao điểm (UC42)",
            description = "Số đơn theo khung 1 giờ 06:00–23:00 theo giờ tạo đơn (BR-01..03); slot cao điểm khi "
                    + ">= 80% slot cao nhất (BR-04). COO bỏ trống branchId = toàn chuỗi.")
    @GetMapping("/peak-hours")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER')")
    public PeakHoursReportDto getPeakHoursReport(@ModelAttribute @Valid ReportPeriodQuery query) {
        return reportService.getPeakHoursReport(query);
    }
}
