package edu.fpt.sba.scms.service.report.export;

import edu.fpt.sba.scms.common.enums.ProductSalesRanking;
import edu.fpt.sba.scms.common.enums.ReportType;
import edu.fpt.sba.scms.dto.response.report.ProductSalesItemDto;
import edu.fpt.sba.scms.dto.response.report.ProductSalesReportDto;
import edu.fpt.sba.scms.integration.export.ReportDocument;

import java.util.List;

import static edu.fpt.sba.scms.service.report.export.ExportCells.integer;
import static edu.fpt.sba.scms.service.report.export.ExportCells.money;
import static edu.fpt.sba.scms.service.report.export.ExportCells.percent;
import static edu.fpt.sba.scms.service.report.export.ExportCells.row;
import static edu.fpt.sba.scms.service.report.export.ExportCells.text;

/** File xuất của báo cáo sản phẩm bán chạy / bán chậm (UC41). */
public final class ProductSalesReportDocument {

    private ProductSalesReportDocument() {
    }

    public static ReportDocument build(ProductSalesReportDto r) {
        String title = r.ranking() == ProductSalesRanking.SLOW_SELLING ? "Sản phẩm bán chậm" : "Sản phẩm bán chạy";
        List<String> header = List.of(ExportCells.branchLine(r.branchName()), ExportCells.periodLine(r.from(), r.to()),
                "Xếp hạng: " + title + " - top " + r.limit(),
                "Tổng số lượng bán: " + r.totalQuantity());
        return new ReportDocument(ReportType.PRODUCT_SALES.title(), header, List.of(itemsTable(title, r.items())));
    }

    static ReportDocument.Table itemsTable(String name, List<ProductSalesItemDto> items) {
        return new ReportDocument.Table(name,
                List.of(integer("Hạng"), text("Sản phẩm"), integer("Số lượng"), money("Doanh thu"),
                        percent("% Số lượng")),
                items.stream().map(i -> row(i.rank(), i.productName(), i.quantity(), i.revenue(),
                        i.quantitySharePercent())).toList());
    }
}
