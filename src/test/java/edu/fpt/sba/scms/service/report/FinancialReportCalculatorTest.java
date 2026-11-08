package edu.fpt.sba.scms.service.report;

import edu.fpt.sba.scms.common.enums.CostDataGap;
import edu.fpt.sba.scms.dto.response.report.BranchFinancialDto;
import edu.fpt.sba.scms.dto.response.report.MissingCostProductDto;
import edu.fpt.sba.scms.model.Branch;
import edu.fpt.sba.scms.repository.SalesReportRepository.BranchAmountRow;
import edu.fpt.sba.scms.repository.SalesReportRepository.BranchSalesRow;
import edu.fpt.sba.scms.repository.SalesReportRepository.SoldProductCostRow;
import edu.fpt.sba.scms.service.report.FinancialReportCalculator.RecipeCostLine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FinancialReportCalculatorTest {

    private static final Branch HCM = Branch.builder().id(1).code("HCM").name("Hồ Chí Minh").build();
    private static final Branch HN = Branch.builder().id(2).code("HN").name("Hà Nội").build();

    private static SoldProductCostRow sold(int productId, String name, String size, long qty, long withoutSnapshot,
                                           String snapshot) {
        return Rows.of(SoldProductCostRow.class, "branchId", 1, "productId", productId, "productName", name,
                "size", size, "quantity", qty, "quantityWithoutSnapshot", withoutSnapshot,
                "snapshotCogs", new BigDecimal(snapshot));
    }

    private static RecipeCostLine recipe(int productId, String size, String qty, String unitCost) {
        return new RecipeCostLine(productId, size, new BigDecimal(qty), unitCost == null ? null : new BigDecimal(unitCost));
    }

    private final List<BranchSalesRow> sales = List.of(Rows.of(BranchSalesRow.class, "branchId", 1, "orderCount", 3L,
            "revenue", new BigDecimal("215000")));
    private final List<BranchAmountRow> refunds = List.of(Rows.of(BranchAmountRow.class, "branchId", 1,
            "amount", new BigDecimal("10000")));

    @Test
    void giaVonTuCongThucTheoSizeVaSnapshot() {
        List<SoldProductCostRow> sold = List.of(
                // 4 ly size L chưa snapshot: dòng chung (20g × 100) + dòng size L (5g × 200) = 3.000 / ly
                sold(1, "Cà phê sữa", "L", 4, 4, "0"),
                // 2 ly đã có snapshot 5.000
                sold(1, "Cà phê sữa", "M", 2, 0, "5000"));
        List<RecipeCostLine> recipes = List.of(recipe(1, null, "20", "100"), recipe(1, "L", "5", "200"),
                recipe(1, "M", "99", "999"));

        FinancialReportCalculator.FinancialReport report = FinancialReportCalculator.calculate(List.of(HCM, HN),
                sales, refunds, sold, recipes);

        BranchFinancialDto hcm = report.branches().get(0);
        assertThat(hcm.figures().revenue()).isEqualByComparingTo("205000");
        assertThat(hcm.figures().cogs()).isEqualByComparingTo("17000");
        assertThat(hcm.figures().grossProfit()).isEqualByComparingTo("188000");
        assertThat(hcm.figures().grossMarginPercent()).isEqualByComparingTo("91.71");
        assertThat(hcm.costIncomplete()).isFalse();

        BranchFinancialDto hn = report.branches().get(1);
        assertThat(hn.figures().orderCount()).as("chi nhánh không có đơn vẫn có dòng 0 (AT1)").isZero();
        assertThat(hn.figures().revenue()).isEqualByComparingTo("0");
        assertThat(hn.figures().grossMarginPercent()).isNull();

        assertThat(report.chainTotal().revenue()).isEqualByComparingTo("205000");
        assertThat(report.missingCostProducts()).isEmpty();
    }

    @Test
    void thieuCongThucHoacDonGiaThiCanhBao() {
        List<SoldProductCostRow> sold = List.of(
                sold(1, "Cà phê sữa", "M", 2, 2, "0"),
                sold(2, "Trà đào", "M", 1, 1, "0"));
        List<RecipeCostLine> recipes = List.of(recipe(1, null, "20", null));

        FinancialReportCalculator.FinancialReport report = FinancialReportCalculator.calculate(List.of(HCM), sales,
                List.of(), sold, recipes);

        assertThat(report.branches().get(0).costIncomplete()).isTrue();
        assertThat(report.branches().get(0).figures().cogs()).isEqualByComparingTo("0");
        assertThat(report.missingCostProducts()).extracting(MissingCostProductDto::reason)
                .containsExactly(CostDataGap.MISSING_UNIT_COST, CostDataGap.NO_RECIPE);
    }
}
