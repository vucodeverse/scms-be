package edu.fpt.sba.scms.service.report;

import edu.fpt.sba.scms.common.enums.ProductSalesRanking;
import edu.fpt.sba.scms.dto.response.report.ProductSalesItemDto;
import edu.fpt.sba.scms.repository.SalesReportRepository.ProductRow;
import edu.fpt.sba.scms.repository.SalesReportRepository.ProductSalesRow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProductRankingCalculatorTest {

    private static ProductSalesRow sold(int id, String name, long qty, String revenue) {
        return Rows.of(ProductSalesRow.class, "productId", id, "productName", name, "quantity", qty,
                "revenue", new BigDecimal(revenue));
    }

    private static ProductRow menu(int id, String name) {
        return Rows.of(ProductRow.class, "productId", id, "productName", name);
    }

    private final List<ProductSalesRow> sold = List.of(
            sold(1, "Bạc xỉu", 5, "150000"),
            sold(2, "Trà đào", 8, "200000"),
            sold(3, "Cà phê sữa", 5, "175000"));

    @Test
    void xepTheoSoLuongRoiDoanhThuKhongTheoDoanhThu() {
        List<ProductSalesItemDto> top = ProductRankingCalculator.rank(sold, List.of(),
                ProductSalesRanking.BEST_SELLING, 10);

        assertThat(top).extracting(ProductSalesItemDto::productName)
                .containsExactly("Trà đào", "Cà phê sữa", "Bạc xỉu");
        assertThat(top).extracting(ProductSalesItemDto::rank).containsExactly(1, 2, 3);
        assertThat(top.get(0).quantitySharePercent()).isEqualByComparingTo("44.44");
    }

    @Test
    void gioiHanSoDong() {
        assertThat(ProductRankingCalculator.rank(sold, List.of(), ProductSalesRanking.BEST_SELLING, 2)).hasSize(2);
    }

    @Test
    void banChamGomCaMonKhongBanDuocLyNao() {
        List<ProductSalesItemDto> slow = ProductRankingCalculator.rank(sold,
                List.of(menu(1, "Bạc xỉu"), menu(4, "Matcha")), ProductSalesRanking.SLOW_SELLING, 3);

        assertThat(slow).extracting(ProductSalesItemDto::productName)
                .containsExactly("Matcha", "Bạc xỉu", "Cà phê sữa");
        assertThat(slow.get(0).quantity()).isZero();
    }

    @Test
    void tongSoLuongVaDoanhThu() {
        assertThat(ProductRankingCalculator.totalQuantity(sold)).isEqualTo(18);
        assertThat(ProductRankingCalculator.totalRevenue(sold)).isEqualByComparingTo("525000");
    }
}
