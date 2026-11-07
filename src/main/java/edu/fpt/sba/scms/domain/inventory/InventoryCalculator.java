package edu.fpt.sba.scms.domain.inventory;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Công thức tính toán tồn kho, quy đổi đơn vị và cảnh báo định mức an toàn (Pure Java - Domain).
 * Hiện thực quy tắc nghiệp vụ BR-45.
 */
public final class InventoryCalculator {

    private InventoryCalculator() {
        // Utility class
    }

    /**
     * Quy đổi số lượng tồn cơ sở (base units) sang số đơn vị đóng gói (pack units):
     * currentPackUnits = currentQtyBase / unitsPerPack (làm tròn 2 chữ số thập phân HALF_UP).
     */
    public static BigDecimal calculatePackUnits(BigDecimal currentQtyBase, BigDecimal unitsPerPack) {
        if (currentQtyBase == null || unitsPerPack == null || unitsPerPack.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return currentQtyBase.divide(unitsPerPack, 2, RoundingMode.HALF_UP);
    }

    /**
     * BR-45: Kiểm tra xem mức tồn kho cơ sở có chạm hoặc dưới ngưỡng an toàn (safetyStockLevel) hay không.
     * Cờ cảnh báo lowStockFlag = currentQtyBase <= safetyStockLevel.
     */
    public static boolean isLowStock(BigDecimal currentQtyBase, BigDecimal safetyStockLevel) {
        BigDecimal qty = currentQtyBase != null ? currentQtyBase : BigDecimal.ZERO;
        BigDecimal safety = safetyStockLevel != null ? safetyStockLevel : BigDecimal.ZERO;
        return qty.compareTo(safety) <= 0;
    }

    /**
     * Tính lượng nguyên vật liệu cần trừ theo công thức định lượng (recipe BOM):
     * requiredQty = itemQuantity * recipePortion
     */
    public static BigDecimal calculateRequiredStock(BigDecimal itemQuantity, BigDecimal recipePortion) {
        BigDecimal qty = itemQuantity != null ? itemQuantity : BigDecimal.ZERO;
        BigDecimal portion = recipePortion != null ? recipePortion : BigDecimal.ZERO;
        return qty.multiply(portion).setScale(4, RoundingMode.HALF_UP);
    }
}

