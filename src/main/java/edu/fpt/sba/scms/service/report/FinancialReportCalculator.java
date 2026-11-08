package edu.fpt.sba.scms.service.report;

import edu.fpt.sba.scms.common.enums.CostDataGap;
import edu.fpt.sba.scms.dto.response.report.BranchFinancialDto;
import edu.fpt.sba.scms.dto.response.report.FinancialFiguresDto;
import edu.fpt.sba.scms.dto.response.report.MissingCostProductDto;
import edu.fpt.sba.scms.model.Branch;
import edu.fpt.sba.scms.repository.SalesReportRepository.BranchAmountRow;
import edu.fpt.sba.scms.repository.SalesReportRepository.BranchSalesRow;
import edu.fpt.sba.scms.repository.SalesReportRepository.SoldProductCostRow;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static edu.fpt.sba.scms.service.report.ReportMath.money;
import static edu.fpt.sba.scms.service.report.ReportMath.zeroIfNull;

/**
 * Doanh thu - giá vốn - lãi gộp hợp nhất (UC40).
 * <ul>
 *   <li>Doanh thu = tiền khách trả của đơn được ghi nhận − hoàn tiền (BR-02, BR-26/48).</li>
 *   <li>Giá vốn: ưu tiên snapshot {@code order_line.cogs_amount}; dòng chưa có snapshot thì
 *       Σ(định lượng công thức × số ly × đơn giá nguyên liệu) (BR-03, BR-19/48). Công thức theo size dùng dòng
 *       cùng size + dòng chung (size NULL).</li>
 *   <li>Thiếu công thức hoặc đơn giá → cảnh báo dữ liệu giá vốn chưa đủ, vẫn trả số tính được (AT2).</li>
 *   <li>Chi nhánh không có đơn vẫn có dòng với số 0 (AT1).</li>
 * </ul>
 */
public final class FinancialReportCalculator {

    /** Một dòng công thức: {@code unitCost = null} khi nguyên liệu chưa có đơn giá. */
    public record RecipeCostLine(Integer productId, String size, BigDecimal quantityBase, BigDecimal unitCost) {
    }

    public record FinancialReport(FinancialFiguresDto chainTotal, List<BranchFinancialDto> branches,
                                  List<MissingCostProductDto> missingCostProducts) {
    }

    /** Giá vốn một ly theo (sản phẩm, size), hoặc lý do không tính được. */
    private record UnitCost(BigDecimal cost, CostDataGap gap) {
    }

    private record ProductSize(Integer productId, String size) {
    }

    private FinancialReportCalculator() {
    }

    public static FinancialReport calculate(List<Branch> branches, List<BranchSalesRow> sales,
                                            List<BranchAmountRow> refunds, List<SoldProductCostRow> sold,
                                            List<RecipeCostLine> recipes) {
        Map<Integer, BranchSalesRow> salesByBranch = new HashMap<>();
        sales.forEach(s -> salesByBranch.put(s.getBranchId(), s));
        Map<Integer, BigDecimal> refundByBranch = new HashMap<>();
        refunds.forEach(r -> refundByBranch.merge(r.getBranchId(), zeroIfNull(r.getAmount()), BigDecimal::add));

        Map<Integer, List<RecipeCostLine>> recipesByProduct = new HashMap<>();
        recipes.forEach(r -> recipesByProduct.computeIfAbsent(r.productId(), id -> new ArrayList<>()).add(r));
        Map<ProductSize, UnitCost> unitCosts = new HashMap<>();
        Map<ProductSize, MissingCostProductDto> missing = new LinkedHashMap<>();
        Map<Integer, BigDecimal> cogsByBranch = new HashMap<>();
        Map<Integer, Boolean> incompleteByBranch = new HashMap<>();

        for (SoldProductCostRow row : sold) {
            ProductSize key = new ProductSize(row.getProductId(), row.getSize());
            UnitCost unit = unitCosts.computeIfAbsent(key, k -> unitCost(recipesByProduct.get(k.productId()), k.size()));
            long toCompute = zeroIfNull(row.getQuantityWithoutSnapshot());
            BigDecimal cogs = zeroIfNull(row.getSnapshotCogs());
            if (toCompute > 0 && unit.gap() != null) {
                incompleteByBranch.put(row.getBranchId(), true);
                missing.putIfAbsent(key, new MissingCostProductDto(row.getProductId(), row.getProductName(),
                        row.getSize(), unit.gap()));
            }
            if (toCompute > 0 && unit.gap() == null) {
                cogs = cogs.add(unit.cost().multiply(BigDecimal.valueOf(toCompute)));
            }
            cogsByBranch.merge(row.getBranchId(), cogs, BigDecimal::add);
        }

        List<BranchFinancialDto> rows = new ArrayList<>();
        for (Branch branch : branches) {
            BranchSalesRow s = salesByBranch.get(branch.getId());
            long orders = s == null ? 0 : zeroIfNull(s.getOrderCount());
            BigDecimal revenue = (s == null ? BigDecimal.ZERO : zeroIfNull(s.getRevenue()))
                    .subtract(refundByBranch.getOrDefault(branch.getId(), BigDecimal.ZERO));
            rows.add(new BranchFinancialDto(branch.getId(), branch.getCode(), branch.getName(),
                    figures(orders, revenue, cogsByBranch.getOrDefault(branch.getId(), BigDecimal.ZERO)),
                    incompleteByBranch.getOrDefault(branch.getId(), false)));
        }
        List<MissingCostProductDto> missingList = new ArrayList<>(missing.values());
        missingList.sort(Comparator.comparing(MissingCostProductDto::productName,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(MissingCostProductDto::size, Comparator.nullsFirst(Comparator.naturalOrder())));
        return new FinancialReport(total(rows), rows, missingList);
    }

    private static UnitCost unitCost(List<RecipeCostLine> lines, String size) {
        List<RecipeCostLine> applicable = lines == null ? List.of() : lines.stream()
                .filter(l -> l.size() == null || Objects.equals(l.size(), size))
                .toList();
        if (applicable.isEmpty()) {
            return new UnitCost(null, CostDataGap.NO_RECIPE);
        }
        BigDecimal cost = BigDecimal.ZERO;
        for (RecipeCostLine line : applicable) {
            if (line.unitCost() == null) {
                return new UnitCost(null, CostDataGap.MISSING_UNIT_COST);
            }
            cost = cost.add(zeroIfNull(line.quantityBase()).multiply(line.unitCost()));
        }
        return new UnitCost(cost, null);
    }

    private static FinancialFiguresDto figures(long orders, BigDecimal revenue, BigDecimal cogs) {
        BigDecimal profit = revenue.subtract(cogs);
        return new FinancialFiguresDto(orders, money(revenue), money(cogs), money(profit),
                ReportMath.percent(profit, revenue));
    }

    private static FinancialFiguresDto total(List<BranchFinancialDto> rows) {
        long orders = 0;
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal cogs = BigDecimal.ZERO;
        for (BranchFinancialDto row : rows) {
            orders += row.figures().orderCount();
            revenue = revenue.add(row.figures().revenue());
            cogs = cogs.add(row.figures().cogs());
        }
        return figures(orders, revenue, cogs);
    }
}
