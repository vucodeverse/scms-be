package edu.fpt.sba.scms.controller;

import edu.fpt.sba.scms.common.enums.ExportFormat;
import edu.fpt.sba.scms.dto.ApiErrorResponse;
import edu.fpt.sba.scms.dto.request.query.report.CashFlowReportQuery;
import edu.fpt.sba.scms.dto.request.query.report.FinancialReportQuery;
import edu.fpt.sba.scms.dto.request.query.report.ProductSalesQuery;
import edu.fpt.sba.scms.dto.request.query.report.ReportPeriodQuery;
import edu.fpt.sba.scms.dto.request.query.report.SalesReportQuery;
import edu.fpt.sba.scms.dto.response.report.ReportFileDto;
import edu.fpt.sba.scms.service.ReportExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * Xuất báo cáo ra file Excel (.xlsx) hoặc PDF (UC36). Mỗi endpoint nhận <b>đúng bộ lọc</b> của API xem tương ứng
 * nên file khớp màn hình (BR-02). Quyền giống API xem.
 */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "22. Reports & Analytics")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "200", description = "File báo cáo",
                content = {@Content(mediaType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
                        @Content(mediaType = "application/pdf")}),
        @ApiResponse(responseCode = "403", description = "Không có quyền / chi nhánh ngoài phạm vi (AT1)",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "REPORT_NO_DATA_TO_EXPORT — không có dữ liệu để xuất (AT2)",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Tạo file thất bại (AT3)",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
public class ReportExportController {

    private static final String FORMAT_DOC = "XLSX (mặc định) hoặc PDF";

    private final ReportExportService exportService;

    @Operation(summary = "Xuất báo cáo doanh số (UC36 / UC35)")
    @GetMapping("/sales/export")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER')")
    public ResponseEntity<byte[]> exportSales(
            @ModelAttribute @Valid SalesReportQuery query,
            @Parameter(description = FORMAT_DOC) @RequestParam(defaultValue = "XLSX") ExportFormat format) {
        return download(exportService.exportSales(query, format));
    }

    @Operation(summary = "Xuất báo cáo dòng tiền két (UC36 / UC37)")
    @GetMapping("/cash-flow/export")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER')")
    public ResponseEntity<byte[]> exportCashFlow(
            @ModelAttribute @Valid CashFlowReportQuery query,
            @Parameter(description = FORMAT_DOC) @RequestParam(defaultValue = "XLSX") ExportFormat format) {
        return download(exportService.exportCashFlow(query, format));
    }

    @Operation(summary = "Xuất báo cáo xuất - nhập - tồn kho (UC36 / UC38)")
    @GetMapping("/inventory/export")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER')")
    public ResponseEntity<byte[]> exportInventory(
            @ModelAttribute @Valid ReportPeriodQuery query,
            @Parameter(description = FORMAT_DOC) @RequestParam(defaultValue = "XLSX") ExportFormat format) {
        return download(exportService.exportInventory(query, format));
    }

    @Operation(summary = "Xuất báo cáo giờ công nhân sự (UC36 / UC39)")
    @GetMapping("/hr-shifts/export")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER')")
    public ResponseEntity<byte[]> exportHrShifts(
            @ModelAttribute @Valid ReportPeriodQuery query,
            @Parameter(description = FORMAT_DOC) @RequestParam(defaultValue = "XLSX") ExportFormat format) {
        return download(exportService.exportHrShifts(query, format));
    }

    @Operation(summary = "Xuất báo cáo tài chính hợp nhất (UC36 / UC40 — chỉ COO)")
    @GetMapping("/consolidated-financial/export")
    @PreAuthorize("hasAuthority('COO')")
    public ResponseEntity<byte[]> exportConsolidatedFinancial(
            @ModelAttribute @Valid FinancialReportQuery query,
            @Parameter(description = FORMAT_DOC) @RequestParam(defaultValue = "XLSX") ExportFormat format) {
        return download(exportService.exportConsolidatedFinancial(query, format));
    }

    @Operation(summary = "Xuất báo cáo sản phẩm bán chạy / bán chậm (UC36 / UC41 — chỉ COO)")
    @GetMapping("/product-sales/export")
    @PreAuthorize("hasAuthority('COO')")
    public ResponseEntity<byte[]> exportProductSales(
            @ModelAttribute @Valid ProductSalesQuery query,
            @Parameter(description = FORMAT_DOC) @RequestParam(defaultValue = "XLSX") ExportFormat format) {
        return download(exportService.exportProductSales(query, format));
    }

    @Operation(summary = "Xuất báo cáo giờ cao điểm (UC36 / UC42)")
    @GetMapping("/peak-hours/export")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER')")
    public ResponseEntity<byte[]> exportPeakHours(
            @ModelAttribute @Valid ReportPeriodQuery query,
            @Parameter(description = FORMAT_DOC) @RequestParam(defaultValue = "XLSX") ExportFormat format) {
        return download(exportService.exportPeakHours(query, format));
    }

    private static ResponseEntity<byte[]> download(ReportFileDto file) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.content().length)
                .body(file.content());
    }
}
