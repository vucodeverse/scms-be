package edu.fpt.sba.scms.service.impl;

import edu.fpt.sba.scms.common.audit.AuditLogService;
import edu.fpt.sba.scms.common.enums.AuditAction;
import edu.fpt.sba.scms.common.enums.BusinessErrorCode;
import edu.fpt.sba.scms.common.enums.ExportFormat;
import edu.fpt.sba.scms.common.enums.ReportType;
import edu.fpt.sba.scms.common.exception.BusinessRuleException;
import edu.fpt.sba.scms.common.exception.ReportGenerationException;
import edu.fpt.sba.scms.common.util.DateTimeUtil;
import edu.fpt.sba.scms.dto.request.query.report.CashFlowReportQuery;
import edu.fpt.sba.scms.dto.request.query.report.FinancialReportQuery;
import edu.fpt.sba.scms.dto.request.query.report.ProductSalesQuery;
import edu.fpt.sba.scms.dto.request.query.report.ReportPeriodQuery;
import edu.fpt.sba.scms.dto.request.query.report.SalesReportQuery;
import edu.fpt.sba.scms.dto.response.report.ReportFileDto;
import edu.fpt.sba.scms.dto.response.report.ReportResult;
import edu.fpt.sba.scms.integration.export.ReportDocument;
import edu.fpt.sba.scms.integration.export.ReportDocumentWriter;
import edu.fpt.sba.scms.service.ReportExportService;
import edu.fpt.sba.scms.service.ReportService;
import edu.fpt.sba.scms.service.report.export.CashFlowReportDocument;
import edu.fpt.sba.scms.service.report.export.FinancialReportDocument;
import edu.fpt.sba.scms.service.report.export.HrShiftReportDocument;
import edu.fpt.sba.scms.service.report.export.InventoryReportDocument;
import edu.fpt.sba.scms.service.report.export.PeakHoursReportDocument;
import edu.fpt.sba.scms.service.report.export.ProductSalesReportDocument;
import edu.fpt.sba.scms.service.report.export.SalesReportDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Xuất báo cáo (UC36): lấy đúng report của màn hình → dựng {@link ReportDocument} → ghi file bằng writer theo
 * định dạng (Strategy) → ghi audit trong cùng transaction.
 */
@Slf4j
@Service
public class ReportExportServiceImpl implements ReportExportService {

    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(DateTimeUtil.VN);

    private final ReportService reportService;
    private final AuditLogService auditLogService;
    private final Map<ExportFormat, ReportDocumentWriter> writers = new EnumMap<>(ExportFormat.class);

    public ReportExportServiceImpl(ReportService reportService, AuditLogService auditLogService,
                                   List<ReportDocumentWriter> writers) {
        this.reportService = reportService;
        this.auditLogService = auditLogService;
        writers.forEach(w -> this.writers.put(w.format(), w));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReportFileDto exportSales(SalesReportQuery query, ExportFormat format) {
        return export(ReportType.SALES, format, query, reportService.getSalesReport(query),
                SalesReportDocument::build);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReportFileDto exportCashFlow(CashFlowReportQuery query, ExportFormat format) {
        return export(ReportType.CASH_FLOW, format, query, reportService.getCashFlowReport(query),
                CashFlowReportDocument::build);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReportFileDto exportInventory(ReportPeriodQuery query, ExportFormat format) {
        return export(ReportType.INVENTORY, format, query, reportService.getInventoryReport(query),
                InventoryReportDocument::build);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReportFileDto exportHrShifts(ReportPeriodQuery query, ExportFormat format) {
        return export(ReportType.HR_SHIFTS, format, query, reportService.getHrShiftReport(query),
                HrShiftReportDocument::build);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReportFileDto exportConsolidatedFinancial(FinancialReportQuery query, ExportFormat format) {
        return export(ReportType.CONSOLIDATED_FINANCIAL, format, query,
                reportService.getConsolidatedFinancialReport(query), FinancialReportDocument::build);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReportFileDto exportProductSales(ProductSalesQuery query, ExportFormat format) {
        return export(ReportType.PRODUCT_SALES, format, query, reportService.getProductSalesReport(query),
                ProductSalesReportDocument::build);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReportFileDto exportPeakHours(ReportPeriodQuery query, ExportFormat format) {
        return export(ReportType.PEAK_HOURS, format, query, reportService.getPeakHoursReport(query),
                PeakHoursReportDocument::build);
    }

    private <R extends ReportResult> ReportFileDto export(ReportType type, ExportFormat format, Object filters,
                                                          R report, Function<R, ReportDocument> toDocument) {
        if (report.empty()) {
            throw new BusinessRuleException(BusinessErrorCode.REPORT_NO_DATA_TO_EXPORT);
        }
        byte[] content = write(format, toDocument.apply(report));
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("type", type.slug());
        after.put("format", format);
        after.put("filters", filters);
        auditLogService.log(AuditAction.REPORT_EXPORT, "report", type.slug(), null, after);
        String fileName = type.slug() + "_" + FILE_TIMESTAMP.format(Instant.now()) + "." + format.extension();
        return new ReportFileDto(fileName, format.contentType(), content);
    }

    private byte[] write(ExportFormat format, ReportDocument document) {
        ReportDocumentWriter writer = writers.get(format);
        if (writer == null) {
            throw new IllegalStateException("Chưa cấu hình writer cho định dạng " + format);
        }
        try {
            return writer.write(document);
        } catch (IOException | RuntimeException e) {
            log.error("Report export failed: format={} title={}", format, document.title(), e);
            throw new ReportGenerationException(e);
        }
    }
}
