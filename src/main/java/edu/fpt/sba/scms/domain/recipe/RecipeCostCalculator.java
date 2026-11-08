package edu.fpt.sba.scms.domain.recipe;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

/**
 * Thuật toán tính toán chi phí nguyên vật liệu và giá vốn định lượng (COGS) của công thức món (Pure Java - Domain).
 * Tuân thủ quy tắc kiến trúc: chỉ dùng JDK thuần, không phụ thuộc Spring/JPA/Database.
 */
public final class RecipeCostCalculator {

    private RecipeCostCalculator() {
        // Utility class
    }

    /**
     * Tính chi phí nguyên vật liệu cho một dòng công thức:
     * lineCost = quantityBase * unitCost (làm tròn 2 chữ số thập phân HALF_UP).
     */
    public static BigDecimal calculateLineCost(BigDecimal quantityBase, BigDecimal unitCost) {
        BigDecimal qty = quantityBase != null ? quantityBase : BigDecimal.ZERO;
        BigDecimal cost = unitCost != null ? unitCost : BigDecimal.ZERO;

        return qty.multiply(cost).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Tính tổng giá vốn ước tính (Estimated Cost of Goods Sold - COGS) cho sản phẩm theo công thức BOM:
     * estimatedCogs = sum(lineCosts)
     */
    public static BigDecimal calculateTotalCogs(Collection<BigDecimal> lineCosts) {
        if (lineCosts == null || lineCosts.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return lineCosts.stream()
                .filter(cost -> cost != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }
}

