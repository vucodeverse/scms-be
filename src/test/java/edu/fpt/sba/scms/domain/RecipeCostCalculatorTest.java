package edu.fpt.sba.scms.domain;

import edu.fpt.sba.scms.domain.recipe.RecipeCostCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Domain: RecipeCostCalculator Unit Tests")
class RecipeCostCalculatorTest {

    @Test
    @DisplayName("calculateLineCost: Tính đúng chi phí nguyên liệu theo định lượng * đơn giá")
    void calculateLineCost_calculatesCorrectly() {
        // Định lượng 20g bột cà phê, giá 250 đ/g => 5.000 đ
        BigDecimal lineCost = RecipeCostCalculator.calculateLineCost(
                new BigDecimal("20.00"), new BigDecimal("250.00"));
        assertEquals(new BigDecimal("5000.00"), lineCost);

        // Null an toàn
        assertEquals(new BigDecimal("0.00"), RecipeCostCalculator.calculateLineCost(null, new BigDecimal("250.00")));
        assertEquals(new BigDecimal("0.00"), RecipeCostCalculator.calculateLineCost(new BigDecimal("20.00"), null));
    }

    @Test
    @DisplayName("calculateTotalCogs: Tính đúng tổng giá vốn lý thuyết của công thức")
    void calculateTotalCogs_calculatesSum() {
        // Cà phê 5.000 đ + Sữa 4.500 đ + Đường 1.000 đ = 10.500 đ
        List<BigDecimal> lineCosts = List.of(
                new BigDecimal("5000.00"),
                new BigDecimal("4500.00"),
                new BigDecimal("1000.00")
        );
        BigDecimal total = RecipeCostCalculator.calculateTotalCogs(lineCosts);
        assertEquals(new BigDecimal("10500.00"), total);

        assertEquals(new BigDecimal("0.00"), RecipeCostCalculator.calculateTotalCogs(null));
        assertEquals(new BigDecimal("0.00"), RecipeCostCalculator.calculateTotalCogs(List.of()));
    }
}

