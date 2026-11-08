package edu.fpt.sba.scms.domain;

import edu.fpt.sba.scms.common.enums.DiscountType;
import edu.fpt.sba.scms.domain.voucher.VoucherDiscountCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Domain: VoucherDiscountCalculator Unit Tests")
class VoucherDiscountCalculatorTest {

    @Test
    @DisplayName("calculateDiscount: Giảm theo PERCENT và làm tròn 2 chữ số thập phân")
    void calculateDiscount_percent() {
        // 100.000đ giảm 15% => 15.000đ
        BigDecimal discount = VoucherDiscountCalculator.calculateDiscount(
                DiscountType.PERCENT, BigDecimal.valueOf(15), BigDecimal.valueOf(100000));
        assertEquals(new BigDecimal("15000.00"), discount);

        // 100.000đ giảm 100% => 100.000đ
        BigDecimal fullDiscount = VoucherDiscountCalculator.calculateDiscount(
                DiscountType.PERCENT, BigDecimal.valueOf(100), BigDecimal.valueOf(100000));
        assertEquals(new BigDecimal("100000.00"), fullDiscount);
    }

    @Test
    @DisplayName("calculateDiscount: Giảm theo FIXED và chặn không vượt quá tổng đơn")
    void calculateDiscount_fixed() {
        // Đơn 100.000đ giảm cố định 30.000đ => 30.000đ
        BigDecimal discount = VoucherDiscountCalculator.calculateDiscount(
                DiscountType.FIXED, BigDecimal.valueOf(30000), BigDecimal.valueOf(100000));
        assertEquals(BigDecimal.valueOf(30000), discount);

        // Đơn 50.000đ voucher 70.000đ => tối đa giảm 50.000đ
        BigDecimal capped = VoucherDiscountCalculator.calculateDiscount(
                DiscountType.FIXED, BigDecimal.valueOf(70000), BigDecimal.valueOf(50000));
        assertEquals(BigDecimal.valueOf(50000), capped);
    }

    @Test
    @DisplayName("calculateDiscount: Đầu vào không hợp lệ hoặc bằng 0 trả về 0")
    void calculateDiscount_invalidInputs() {
        assertEquals(BigDecimal.ZERO, VoucherDiscountCalculator.calculateDiscount(null, BigDecimal.valueOf(10), BigDecimal.valueOf(100)));
        assertEquals(BigDecimal.ZERO, VoucherDiscountCalculator.calculateDiscount(DiscountType.PERCENT, null, BigDecimal.valueOf(100)));
        assertEquals(BigDecimal.ZERO, VoucherDiscountCalculator.calculateDiscount(DiscountType.PERCENT, BigDecimal.ZERO, BigDecimal.valueOf(100)));
        assertEquals(BigDecimal.ZERO, VoucherDiscountCalculator.calculateDiscount(DiscountType.PERCENT, BigDecimal.valueOf(10), BigDecimal.ZERO));
    }
}
