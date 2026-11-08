package edu.fpt.sba.scms.service.report.export;

import edu.fpt.sba.scms.common.enums.ReportType;
import edu.fpt.sba.scms.dto.response.report.SalesReportDto;
import edu.fpt.sba.scms.dto.response.report.SalesSummaryDto;
import edu.fpt.sba.scms.integration.export.ReportDocument;

import java.util.ArrayList;
import java.util.List;

import static edu.fpt.sba.scms.service.report.export.ExportCells.date;
import static edu.fpt.sba.scms.service.report.export.ExportCells.integer;
import static edu.fpt.sba.scms.service.report.export.ExportCells.money;
import static edu.fpt.sba.scms.service.report.export.ExportCells.name;
import static edu.fpt.sba.scms.service.report.export.ExportCells.percent;
import static edu.fpt.sba.scms.service.report.export.ExportCells.row;
import static edu.fpt.sba.scms.service.report.export.ExportCells.text;

/** File xuất của báo cáo doanh số (UC35) — cùng số liệu với màn hình (UC36 BR-02). */
public final class SalesReportDocument {

    private SalesReportDocument() {
    }

    public static ReportDocument build(SalesReportDto r) {
        List<String> header = new ArrayList<>(List.of(ExportCells.branchLine(r.branchName()),
                ExportCells.periodLine(r.from(), r.to())));
        if (r.shiftId() != null) {
            header.add("Ca: #" + r.shiftId());
        }
        if (r.paymentMethod() != null) {
            header.add("Phương thức thanh toán: " + r.paymentMethod().name());
        }
        SalesSummaryDto s = r.summary();
        ReportDocument.Table summary = new ReportDocument.Table("Tổng hợp",
                List.of(integer("Số đơn"), money("Tổng tiền hàng"), money("Giảm giá"), money("Doanh thu"),
                        money("Hoàn tiền"), money("Doanh thu thuần"), money("Giá trị TB/đơn")),
                List.of(row(s.orderCount(), s.grossAmount(), s.discountAmount(), s.revenue(), s.refundAmount(),
                        s.netRevenue(), s.averageOrderValue())));
        ReportDocument.Table daily = new ReportDocument.Table("Theo ngày",
                List.of(text("Ngày"), integer("Số đơn"), money("Doanh thu")),
                r.daily().stream().map(d -> row(date(d.date()), d.orderCount(), d.revenue())).toList());
        ReportDocument.Table shifts = new ReportDocument.Table("Theo ca - Payment mix",
                List.of(text("Ca"), text("Ngày"), text("Loại ca"), integer("Số đơn"), money("Doanh thu"),
                        money("Tiền mặt"), money("VietQR"), percent("% Tiền mặt"), percent("% VietQR"),
                        money("Hoàn tiền")),
                r.shifts().stream().map(x -> row(x.shiftId() == null ? "Không gắn ca" : "#" + x.shiftId(),
                        date(x.shiftDate()), name(x.shiftType()), x.orderCount(), x.revenue(), x.cashAmount(),
                        x.vietQrAmount(), x.cashPercent(), x.vietQrPercent(), x.refundAmount())).toList());
        ReportDocument.Table products = ProductSalesReportDocument.itemsTable("Sản phẩm bán chạy", r.topProducts());
        return new ReportDocument(ReportType.SALES.title(), header, List.of(summary, daily, shifts, products));
    }
}
