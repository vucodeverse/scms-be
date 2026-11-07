package edu.fpt.sba.scms.domain;

import edu.fpt.sba.scms.domain.inventory.InventoryCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Domain: InventoryCalculator Unit Tests")
class InventoryCalculatorTest {

    @Test
    @DisplayName("calculatePackUnits: Quy đổi số lượng cơ sở sang đơn vị đóng gói")
    void calculatePackUnits_calculatesCorrectly() {
        // Tồn 2500g, mỗi gói 1000g => 2.50 gói
        assertEquals(new BigDecimal("2.50"),
                InventoryCalculator.calculatePackUnits(new BigDecimal("2500"), new BigDecimal("1000")));

        // unitsPerPack = 0 hoặc null => 0.00
        assertEquals(new BigDecimal("0.00"),
                InventoryCalculator.calculatePackUnits(new BigDecimal("2500"), BigDecimal.ZERO));
        assertEquals(new BigDecimal("0.00"),
                InventoryCalculator.calculatePackUnits(null, new BigDecimal("1000")));
    }

    @Test
    @DisplayName("isLowStock: Cảnh báo khi tồn kho chạm hoặc thấp hơn định mức an toàn (BR-45)")
    void isLowStock_detectsLowStock() {
        // Tồn 5kg, định mức 10kg => Thiếu (True)
        assertTrue(InventoryCalculator.isLowStock(BigDecimal.valueOf(5), BigDecimal.valueOf(10)));

        // Tồn 10kg, định mức 10kg => Chạm ngưỡng (True)
        assertTrue(InventoryCalculator.isLowStock(BigDecimal.valueOf(10), BigDecimal.valueOf(10)));

        // Tồn 15kg, định mức 10kg => An toàn (False)
        assertFalse(InventoryCalculator.isLowStock(BigDecimal.valueOf(15), BigDecimal.valueOf(10)));
    }

    @Test
    @DisplayName("calculateRequiredStock: Tính nguyên liệu cần trừ theo công thức định lượng (BOM)")
    void calculateRequiredStock_calculatesCorrectly() {
        // Bán 3 cốc cà phê, mỗi cốc 20g bột cà phê => 60g
        assertEquals(new BigDecimal("60.0000"),
                InventoryCalculator.calculateRequiredStock(new BigDecimal("3"), new BigDecimal("20")));
    }
}

