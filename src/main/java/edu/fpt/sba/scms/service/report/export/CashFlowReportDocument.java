package edu.fpt.sba.scms.service.report.export;

import edu.fpt.sba.scms.common.enums.CashReconcileStatus;
import edu.fpt.sba.scms.common.enums.ReportType;
import edu.fpt.sba.scms.dto.response.report.CashFlowReportDto;
import edu.fpt.sba.scms.dto.response.report.CashFlowSummaryDto;
import edu.fpt.sba.scms.integration.export.ReportDocument;

import java.util.List;

import static edu.fpt.sba.scms.service.report.export.ExportCells.date;
import static edu.fpt.sba.scms.service.report.export.ExportCells.dateTime;
import static edu.fpt.sba.scms.service.report.export.ExportCells.integer;
import static edu.fpt.sba.scms.service.report.export.ExportCells.money;
import static edu.fpt.sba.scms.service.report.export.ExportCells.name;
import static edu.fpt.sba.scms.service.report.export.ExportCells.row;
import static edu.fpt.sba.scms.service.report.export.ExportCells.text;

/** File xuất của báo cáo dòng tiền két (UC37). */
public final class CashFlowReportDocument {

    private CashFlowReportDocument() {
    }

    public static ReportDocument build(CashFlowReportDto r) {
        CashFlowSummaryDto s = r.summary();
        ReportDocument.Table summary = new ReportDocument.Table("Tổng hợp",
                List.of(integer("Số ca"), integer("Chưa đối soát"), integer("Ca lệch tiền"), money("Tiền đầu ca"),
                        money("Thu tiền mặt"), money("Nạp két"), money("Rút két"), money("Hoàn tiền"),
                        money("Tiền dự kiến"), money("Thực đếm"), money("Chênh lệch")),
                List.of(row(s.shiftCount(), s.unreconciledCount(), s.varianceShiftCount(), s.openingCash(),
                        s.cashSales(), s.cashIn(), s.cashOut(), s.cashRefunds(), s.expectedCash(), s.countedCash(),
                        s.totalVariance())));
        ReportDocument.Table shifts = new ReportDocument.Table("Theo ca",
                List.of(text("Ca"), text("Ngày"), text("Loại ca"), text("Trạng thái ca"), text("Mở lúc"),
                        text("Đóng lúc"), money("Tiền đầu ca"), money("Thu tiền mặt"), money("Nạp két"),
                        money("Rút két"), money("Hoàn tiền"), money("Tiền dự kiến"), money("Thực đếm"),
                        money("Chênh lệch"), text("Đối soát"), text("Giải trình")),
                r.shifts().stream().map(x -> row("#" + x.shiftId(), date(x.shiftDate()), name(x.shiftType()),
                        name(x.shiftStatus()), dateTime(x.openedAt()), dateTime(x.closedAt()), x.openingCash(),
                        x.cashSales(), x.cashIn(), x.cashOut(), x.cashRefunds(), x.expectedCash(), x.countedCash(),
                        x.variance(), reconcileLabel(x.reconcileStatus()), x.discrepancyReason())).toList());
        ReportDocument.Table movements = new ReportDocument.Table("Chi tiết thu chi",
                List.of(text("Ca"), text("Thời gian"), text("Loại"), money("Số tiền"), text("Lý do")),
                r.shifts().stream().flatMap(x -> x.movements().stream().map(m -> row("#" + x.shiftId(),
                        dateTime(m.createdAt()), name(m.type()), m.amount(), m.reason()))).toList());
        return new ReportDocument(ReportType.CASH_FLOW.title(),
                List.of(ExportCells.branchLine(r.branchName()), ExportCells.periodLine(r.from(), r.to())),
                List.of(summary, shifts, movements));
    }

    private static String reconcileLabel(CashReconcileStatus status) {
        return status == CashReconcileStatus.RECONCILED ? "Đã đối soát" : "Chưa đối soát";
    }
}
