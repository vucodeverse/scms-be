package edu.fpt.sba.scms.service.report.export;

import edu.fpt.sba.scms.common.enums.CostDataGap;
import edu.fpt.sba.scms.common.enums.ReportType;
import edu.fpt.sba.scms.dto.response.report.FinancialFiguresDto;
import edu.fpt.sba.scms.dto.response.report.FinancialReportDto;
import edu.fpt.sba.scms.integration.export.ReportDocument;

import java.util.ArrayList;
import java.util.List;

import static edu.fpt.sba.scms.service.report.export.ExportCells.integer;
import static edu.fpt.sba.scms.service.report.export.ExportCells.money;
import static edu.fpt.sba.scms.service.report.export.ExportCells.percent;
import static edu.fpt.sba.scms.service.report.export.ExportCells.row;
import static edu.fpt.sba.scms.service.report.export.ExportCells.text;
import static edu.fpt.sba.scms.service.report.export.ExportCells.yesNo;

/** File xuất của báo cáo tài chính hợp nhất (UC40). */
public final class FinancialReportDocument {

    private FinancialReportDocument() {
    }

    public static ReportDocument build(FinancialReportDto r) {
        List<List<Object>> rows = new ArrayList<>();
        r.branches().forEach(b -> rows.add(figuresRow(b.branchCode(), b.branchName(), b.figures(),
                yesNo(b.costIncomplete()))));
        rows.add(figuresRow("", "TOÀN CHUỖI", r.chainTotal(), yesNo(r.costIncomplete())));
        ReportDocument.Table branches = new ReportDocument.Table("Theo chi nhánh",
                List.of(text("Mã CN"), text("Chi nhánh"), integer("Số đơn"), money("Doanh thu"), money("Giá vốn"),
                        money("Lãi gộp"), percent("Biên lãi gộp"), text("Thiếu dữ liệu giá vốn")),
                rows);
        ReportDocument.Table missing = new ReportDocument.Table("Thiếu dữ liệu giá vốn",
                List.of(text("Sản phẩm"), text("Size"), text("Lý do")),
                r.missingCostProducts().stream().map(m -> row(m.productName(), m.size(), gapLabel(m.reason())))
                        .toList());
        List<String> header = new ArrayList<>(List.of(ExportCells.periodLine(r.from(), r.to())));
        if (r.costIncomplete()) {
            header.add("Cảnh báo: dữ liệu giá vốn chưa đầy đủ — giá vốn hiển thị thấp hơn thực tế.");
        }
        return new ReportDocument(ReportType.CONSOLIDATED_FINANCIAL.title(), header, List.of(branches, missing));
    }

    private static List<Object> figuresRow(String code, String name, FinancialFiguresDto f, String incomplete) {
        return row(code, name, f.orderCount(), f.revenue(), f.cogs(), f.grossProfit(), f.grossMarginPercent(),
                incomplete);
    }

    private static String gapLabel(CostDataGap gap) {
        return gap == CostDataGap.NO_RECIPE ? "Chưa có công thức" : "Nguyên liệu chưa có đơn giá";
    }
}
