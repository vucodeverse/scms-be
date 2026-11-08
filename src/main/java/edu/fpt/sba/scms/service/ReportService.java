package edu.fpt.sba.scms.service;

import edu.fpt.sba.scms.dto.request.query.report.CashFlowReportQuery;
import edu.fpt.sba.scms.dto.request.query.report.FinancialReportQuery;
import edu.fpt.sba.scms.dto.request.query.report.ProductSalesQuery;
import edu.fpt.sba.scms.dto.request.query.report.ReportPeriodQuery;
import edu.fpt.sba.scms.dto.request.query.report.SalesReportQuery;
import edu.fpt.sba.scms.dto.response.report.CashFlowReportDto;
import edu.fpt.sba.scms.dto.response.report.FinancialReportDto;
import edu.fpt.sba.scms.dto.response.report.HrShiftReportDto;
import edu.fpt.sba.scms.dto.response.report.InventoryReportDto;
import edu.fpt.sba.scms.dto.response.report.PeakHoursReportDto;
import edu.fpt.sba.scms.dto.response.report.ProductSalesReportDto;
import edu.fpt.sba.scms.dto.response.report.SalesReportDto;

/**
 * Các báo cáo phân tích (chỉ đọc) — UC35, UC37–UC42. Phạm vi chi nhánh theo BR-04 ({@code ReportScope}).
 */
public interface ReportService {

    /** UC35 — doanh số chi nhánh. */
    SalesReportDto getSalesReport(SalesReportQuery query);

    /** UC37 — dòng tiền két theo ca. */
    CashFlowReportDto getCashFlowReport(CashFlowReportQuery query);

    /** UC38 — xuất - nhập - tồn - hao hụt kho. */
    InventoryReportDto getInventoryReport(ReportPeriodQuery query);

    /** UC39 — nhân sự &amp; ca làm việc. */
    HrShiftReportDto getHrShiftReport(ReportPeriodQuery query);

    /** UC40 — tài chính hợp nhất toàn chuỗi (COO). */
    FinancialReportDto getConsolidatedFinancialReport(FinancialReportQuery query);

    /** UC41 — sản phẩm bán chạy / bán chậm. */
    ProductSalesReportDto getProductSalesReport(ProductSalesQuery query);

    /** UC42 — khung giờ cao điểm. */
    PeakHoursReportDto getPeakHoursReport(ReportPeriodQuery query);
}
