package edu.fpt.sba.scms.service.report.export;

import edu.fpt.sba.scms.common.enums.ReportType;
import edu.fpt.sba.scms.dto.response.report.HrShiftReportDto;
import edu.fpt.sba.scms.dto.response.report.HrSummaryDto;
import edu.fpt.sba.scms.integration.export.ReportDocument;

import java.util.List;

import static edu.fpt.sba.scms.service.report.export.ExportCells.decimal;
import static edu.fpt.sba.scms.service.report.export.ExportCells.integer;
import static edu.fpt.sba.scms.service.report.export.ExportCells.money;
import static edu.fpt.sba.scms.service.report.export.ExportCells.row;
import static edu.fpt.sba.scms.service.report.export.ExportCells.text;

/** File xuất "Employee Hours" — báo cáo nhân sự &amp; ca (UC39, UC36). Không chứa lương (BR-47). */
public final class HrShiftReportDocument {

    private HrShiftReportDocument() {
    }

    public static ReportDocument build(HrShiftReportDto r) {
        HrSummaryDto s = r.summary();
        ReportDocument.Table summary = new ReportDocument.Table("Tổng hợp",
                List.of(integer("Nhân viên"), integer("Ca phân công"), integer("Ca đã làm"), integer("Vắng"),
                        integer("Lượt chấm công"), decimal("Giờ công"), integer("Đi muộn"), integer("Về sớm"),
                        integer("Quên check-out")),
                List.of(row(s.employeeCount(), s.shiftsScheduled(), s.shiftsWorked(), s.absentCount(),
                        s.attendanceCount(), s.hoursWorked(), s.lateCount(), s.earlyLeaveCount(),
                        s.missingCheckoutCount())));
        ReportDocument.Table employees = new ReportDocument.Table("Theo nhân viên",
                List.of(text("Nhân viên"), text("Vai trò"), integer("Ca phân công"), integer("Ca đã làm"),
                        integer("Vắng"), integer("Lượt chấm công"), decimal("Giờ công"), integer("Đi muộn"),
                        integer("Về sớm"), integer("Quên check-out"), integer("Số đơn xử lý"), money("Doanh thu"),
                        money("Doanh thu/giờ")),
                r.employees().stream().map(e -> row(e.fullName(), e.roleCode(), e.shiftsScheduled(),
                        e.shiftsWorked(), e.absentCount(), e.attendanceCount(), e.hoursWorked(), e.lateCount(),
                        e.earlyLeaveCount(), e.missingCheckoutCount(), e.ordersProcessed(), e.revenue(),
                        e.revenuePerHour())).toList());
        return new ReportDocument(ReportType.HR_SHIFTS.title(),
                List.of(ExportCells.branchLine(r.branchName()), ExportCells.periodLine(r.from(), r.to())),
                List.of(summary, employees));
    }
}
