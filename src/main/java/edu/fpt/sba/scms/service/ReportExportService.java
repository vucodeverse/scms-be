package edu.fpt.sba.scms.service;

import edu.fpt.sba.scms.common.enums.ExportFormat;
import edu.fpt.sba.scms.dto.request.query.report.CashFlowReportQuery;
import edu.fpt.sba.scms.dto.request.query.report.FinancialReportQuery;
import edu.fpt.sba.scms.dto.request.query.report.ProductSalesQuery;
import edu.fpt.sba.scms.dto.request.query.report.ReportPeriodQuery;
import edu.fpt.sba.scms.dto.request.query.report.SalesReportQuery;
import edu.fpt.sba.scms.dto.response.report.ReportFileDto;

/**
 * Xuất báo cáo ra file Excel / PDF (UC36). Mỗi method dùng <b>cùng bộ lọc và cùng service</b> với API xem báo cáo,
 * nên file khớp số liệu trên màn hình (BR-02). Kỳ không có dữ liệu → 422 {@code REPORT_NO_DATA_TO_EXPORT} (AT2);
 * lỗi sinh file → 500 (AT3); mỗi lần xuất được ghi audit {@code REPORT_EXPORT} (BR-04 / BR-51).
 */
public interface ReportExportService {

    ReportFileDto exportSales(SalesReportQuery query, ExportFormat format);

    ReportFileDto exportCashFlow(CashFlowReportQuery query, ExportFormat format);

    ReportFileDto exportInventory(ReportPeriodQuery query, ExportFormat format);

    ReportFileDto exportHrShifts(ReportPeriodQuery query, ExportFormat format);

    ReportFileDto exportConsolidatedFinancial(FinancialReportQuery query, ExportFormat format);

    ReportFileDto exportProductSales(ProductSalesQuery query, ExportFormat format);

    ReportFileDto exportPeakHours(ReportPeriodQuery query, ExportFormat format);
}
