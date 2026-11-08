package edu.fpt.sba.scms.service.report.export;

import edu.fpt.sba.scms.common.enums.ReportType;
import edu.fpt.sba.scms.common.enums.StockCountStatus;
import edu.fpt.sba.scms.dto.response.report.InventoryReportDto;
import edu.fpt.sba.scms.dto.response.report.InventorySummaryDto;
import edu.fpt.sba.scms.integration.export.ReportDocument;

import java.util.ArrayList;
import java.util.List;

import static edu.fpt.sba.scms.service.report.export.ExportCells.dateTime;
import static edu.fpt.sba.scms.service.report.export.ExportCells.decimal;
import static edu.fpt.sba.scms.service.report.export.ExportCells.integer;
import static edu.fpt.sba.scms.service.report.export.ExportCells.row;
import static edu.fpt.sba.scms.service.report.export.ExportCells.text;
import static edu.fpt.sba.scms.service.report.export.ExportCells.yesNo;

/** File xuất của báo cáo xuất - nhập - tồn - hao hụt kho (UC38). */
public final class InventoryReportDocument {

    private InventoryReportDocument() {
    }

    public static ReportDocument build(InventoryReportDto r) {
        InventorySummaryDto s = r.summary();
        ReportDocument.Table summary = new ReportDocument.Table("Tổng hợp",
                List.of(integer("Số nguyên liệu"), integer("Tồn thấp"), integer("Lệch kiểm kê"),
                        integer("Chưa kiểm kê"), decimal("Tổng hủy (ĐV cơ sở)")),
                List.of(row(s.ingredientCount(), s.lowStockCount(), s.varianceCount(), s.uncountedCount(),
                        s.disposedQtyTotal())));
        ReportDocument.Table items = new ReportDocument.Table("Xuất - nhập - tồn",
                List.of(text("Nguyên liệu"), text("ĐV cơ sở"), text("ĐV kiện"), decimal("Quy cách kiện"),
                        decimal("Tồn đầu"), decimal("Nhập"), decimal("Xuất bar"), decimal("Hủy"),
                        decimal("Điều chỉnh"), decimal("Tồn cuối"), decimal("Tồn cuối (kiện)"),
                        decimal("Tồn hiện tại"), decimal("Ngưỡng an toàn"), text("Tồn thấp"), text("Kiểm kê"),
                        text("Ngày kiểm kê"), decimal("Tồn sổ lúc đếm"), decimal("Thực đếm"), decimal("Chênh lệch")),
                r.items().stream().map(i -> row(i.name(), i.baseUnit(), i.packUnit(), i.unitsPerPack(),
                        i.openingQty(), i.receivedQty(), i.issuedQty(), i.disposedQty(), i.adjustedQty(),
                        i.closingQty(), i.closingPacks(), i.currentQty(), i.safetyStockLevel(), yesNo(i.lowStock()),
                        countLabel(i.countStatus()), dateTime(i.lastCountedAt()), i.lastBookQty(),
                        i.lastCountedQty(), i.countVariance())).toList());
        List<String> header = new ArrayList<>(List.of(ExportCells.branchLine(r.branchName()),
                ExportCells.periodLine(r.from(), r.to())));
        if (r.noTransactions()) {
            header.add("Không có giao dịch kho trong kỳ — số liệu là tồn hiện tại.");
        }
        return new ReportDocument(ReportType.INVENTORY.title(), header, List.of(summary, items));
    }

    private static String countLabel(StockCountStatus status) {
        return switch (status) {
            case MATCHED -> "Khớp";
            case VARIANCE -> "Lệch";
            case UNCOUNTED -> "Chưa kiểm kê";
        };
    }
}
