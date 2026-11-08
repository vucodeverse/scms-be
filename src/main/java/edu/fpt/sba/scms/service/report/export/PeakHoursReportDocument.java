package edu.fpt.sba.scms.service.report.export;

import edu.fpt.sba.scms.common.enums.ReportType;
import edu.fpt.sba.scms.dto.response.report.PeakHoursReportDto;
import edu.fpt.sba.scms.integration.export.ReportDocument;

import java.util.List;

import static edu.fpt.sba.scms.service.report.export.ExportCells.decimal;
import static edu.fpt.sba.scms.service.report.export.ExportCells.integer;
import static edu.fpt.sba.scms.service.report.export.ExportCells.money;
import static edu.fpt.sba.scms.service.report.export.ExportCells.row;
import static edu.fpt.sba.scms.service.report.export.ExportCells.text;
import static edu.fpt.sba.scms.service.report.export.ExportCells.yesNo;

/** File xuất của báo cáo giờ cao điểm (UC42). */
public final class PeakHoursReportDocument {

    private PeakHoursReportDocument() {
    }

    public static ReportDocument build(PeakHoursReportDto r) {
        ReportDocument.Table slots = new ReportDocument.Table("Theo khung giờ",
                List.of(text("Khung giờ"), integer("Số đơn"), money("Doanh thu"), decimal("TB đơn/ngày"),
                        text("Cao điểm")),
                r.slots().stream().map(s -> row(s.label(), s.orderCount(), s.revenue(), s.averageOrdersPerDay(),
                        yesNo(s.peak()))).toList());
        return new ReportDocument(ReportType.PEAK_HOURS.title(),
                List.of(ExportCells.branchLine(r.branchName()), ExportCells.periodLine(r.from(), r.to()),
                        "Tổng số đơn trong giờ hoạt động: " + r.totalOrders()),
                List.of(slots));
    }
}
