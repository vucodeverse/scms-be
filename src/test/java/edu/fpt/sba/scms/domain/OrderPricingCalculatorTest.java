package edu.fpt.sba.scms.domain;

import edu.fpt.sba.scms.domain.order.OrderPricingCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Domain: OrderPricingCalculator Unit Tests")
class OrderPricingCalculatorTest {

    @Test
    @DisplayName("calculateLineTotal: Tính đúng thành tiền có và không có topping")
    void calculateLineTotal_calculatesCorrectly() {
        // Đơn giá 30.000, topping 10.000, số lượng 2 => 80.000
        BigDecimal lineTotal = OrderPricingCalculator.calculateLineTotal(
                BigDecimal.valueOf(30000), BigDecimal.valueOf(10000), 2);
        assertEquals(BigDecimal.valueOf(80000), lineTotal);

        // Không có topping
        BigDecimal lineNoTopping = OrderPricingCalculator.calculateLineTotal(
                BigDecimal.valueOf(25000), BigDecimal.ZERO, 3);
        assertEquals(BigDecimal.valueOf(75000), lineNoTopping);

        // Tham số null an toàn
        BigDecimal lineNull = OrderPricingCalculator.calculateLineTotal(null, null, 1);
        assertEquals(BigDecimal.ZERO, lineNull);
    }

    @Test
    @DisplayName("calculateFinalAmount: Tính đúng số tiền thanh toán và chặn âm")
    void calculateFinalAmount_calculatesAndCapsAtZero() {
        // Tổng 100.000, giảm 20.000 => 80.000
        BigDecimal finalAmount = OrderPricingCalculator.calculateFinalAmount(
                BigDecimal.valueOf(100000), BigDecimal.valueOf(20000));
        assertEquals(BigDecimal.valueOf(80000), finalAmount);

        // Giảm giá vượt tổng tiền => về 0
        BigDecimal capped = OrderPricingCalculator.calculateFinalAmount(
                BigDecimal.valueOf(50000), BigDecimal.valueOf(60000));
        assertEquals(BigDecimal.ZERO, capped);

        // Null an toàn
        assertEquals(BigDecimal.valueOf(50000), OrderPricingCalculator.calculateFinalAmount(BigDecimal.valueOf(50000), null));
    }

    @Test
    @DisplayName("isSufficientCash: Kiểm tra số tiền khách đưa đủ hoặc thiếu")
    void isSufficientCash_checksCorrectly() {
        assertTrue(OrderPricingCalculator.isSufficientCash(BigDecimal.valueOf(100000), BigDecimal.valueOf(100000)));
        assertTrue(OrderPricingCalculator.isSufficientCash(BigDecimal.valueOf(120000), BigDecimal.valueOf(100000)));
        assertFalse(OrderPricingCalculator.isSufficientCash(BigDecimal.valueOf(90000), BigDecimal.valueOf(100000)));
        assertFalse(OrderPricingCalculator.isSufficientCash(null, BigDecimal.valueOf(100000)));
    }

    @Test
    @DisplayName("calculateChange: Tính đúng tiền thối lại")
    void calculateChange_calculatesCorrectly() {
        BigDecimal change = OrderPricingCalculator.calculateChange(
                BigDecimal.valueOf(100000), BigDecimal.valueOf(85000));
        assertEquals(BigDecimal.valueOf(15000), change);
    }
}
