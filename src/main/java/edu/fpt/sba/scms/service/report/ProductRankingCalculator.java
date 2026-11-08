package edu.fpt.sba.scms.service.report;

import edu.fpt.sba.scms.common.enums.ProductSalesRanking;
import edu.fpt.sba.scms.dto.response.report.ProductSalesItemDto;
import edu.fpt.sba.scms.repository.SalesReportRepository.ProductRow;
import edu.fpt.sba.scms.repository.SalesReportRepository.ProductSalesRow;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static edu.fpt.sba.scms.service.report.ReportMath.money;
import static edu.fpt.sba.scms.service.report.ReportMath.zeroIfNull;

/**
 * Xếp hạng sản phẩm theo <b>số lượng bán</b>, không theo doanh thu (UC41 BR-02 / BR-49).
 * Hoà số lượng thì xét doanh thu, rồi tên — thứ tự ổn định giữa màn hình và file xuất (UC36 BR-02).
 */
public final class ProductRankingCalculator {

    private record Line(Integer productId, String productName, long quantity, BigDecimal revenue) {
    }

    private static final Comparator<Line> BEST_FIRST = Comparator.comparingLong(Line::quantity).reversed()
            .thenComparing(Line::revenue, Comparator.reverseOrder())
            .thenComparing(Line::productName, Comparator.nullsLast(Comparator.naturalOrder()));

    private static final Comparator<Line> SLOW_FIRST = Comparator.comparingLong(Line::quantity)
            .thenComparing(Line::revenue)
            .thenComparing(Line::productName, Comparator.nullsLast(Comparator.naturalOrder()));

    private ProductRankingCalculator() {
    }

    /**
     * @param sold    sản phẩm có bán trong kỳ
     * @param menu    sản phẩm đang trong thực đơn — chỉ dùng cho {@code SLOW_SELLING} để có cả món bán 0 ly
     * @param limit   số dòng tối đa trả về
     */
    public static List<ProductSalesItemDto> rank(List<ProductSalesRow> sold, List<ProductRow> menu,
                                                 ProductSalesRanking ranking, int limit) {
        List<Line> lines = new ArrayList<>();
        Set<Integer> soldIds = new HashSet<>();
        for (ProductSalesRow row : sold) {
            lines.add(new Line(row.getProductId(), row.getProductName(), zeroIfNull(row.getQuantity()),
                    zeroIfNull(row.getRevenue())));
            soldIds.add(row.getProductId());
        }
        long totalQuantity = totalQuantity(sold);
        if (ranking == ProductSalesRanking.SLOW_SELLING) {
            menu.stream()
                    .filter(p -> !soldIds.contains(p.getProductId()))
                    .forEach(p -> lines.add(new Line(p.getProductId(), p.getProductName(), 0, BigDecimal.ZERO)));
        }
        lines.sort(ranking == ProductSalesRanking.SLOW_SELLING ? SLOW_FIRST : BEST_FIRST);

        List<ProductSalesItemDto> result = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, lines.size()); i++) {
            Line line = lines.get(i);
            result.add(new ProductSalesItemDto(i + 1, line.productId(), line.productName(), line.quantity(),
                    money(line.revenue()), ReportMath.percent(line.quantity(), totalQuantity)));
        }
        return result;
    }

    public static long totalQuantity(List<ProductSalesRow> sold) {
        return sold.stream().mapToLong(r -> zeroIfNull(r.getQuantity())).sum();
    }

    public static BigDecimal totalRevenue(List<ProductSalesRow> sold) {
        return money(sold.stream().map(r -> zeroIfNull(r.getRevenue())).reduce(BigDecimal.ZERO, BigDecimal::add));
    }
}
