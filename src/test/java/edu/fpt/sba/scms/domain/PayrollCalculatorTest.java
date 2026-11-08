package edu.fpt.sba.scms.domain;

import edu.fpt.sba.scms.domain.payroll.PayrollCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Domain: PayrollCalculator Unit Tests")
class PayrollCalculatorTest {

    @Test
    @DisplayName("minutesToHours: Quy đổi chuẩn xác phút sang giờ làm")
    void minutesToHours_convertsCorrectly() {
        // 60 phút = 1.00 giờ
        assertEquals(new BigDecimal("1.00"), PayrollCalculator.minutesToHours(60));

        // 90 phút = 1.50 giờ
        assertEquals(new BigDecimal("1.50"), PayrollCalculator.minutesToHours(90));

        // 480 phút = 8.00 giờ
        assertEquals(new BigDecimal("8.00"), PayrollCalculator.minutesToHours(480));

        // 0 phút hoặc âm => 0.00
        assertEquals(new BigDecimal("0.00"), PayrollCalculator.minutesToHours(0));
        assertEquals(new BigDecimal("0.00"), PayrollCalculator.minutesToHours(-10));
    }

    @Test
    @DisplayName("calculateBaseSalary: Tính lương cơ bản theo giờ làm * đơn giá")
    void calculateBaseSalary_calculatesCorrectly() {
        // 8 giờ * 25.000 đ = 200.000 đ
        BigDecimal base = PayrollCalculator.calculateBaseSalary(
                new BigDecimal("8.00"), new BigDecimal("25000.00"));
        assertEquals(new BigDecimal("200000.00"), base);

        // Null an toàn
        assertEquals(new BigDecimal("0.00"), PayrollCalculator.calculateBaseSalary(null, new BigDecimal("25000.00")));
    }

    @Test
    @DisplayName("calculateNetSalary: Tính lương thực nhận (Net) và chặn số âm")
    void calculateNetSalary_calculatesAndCapsAtZero() {
        // Lương 200.000 + Thưởng 50.000 - Phạt 20.000 = 230.000
        BigDecimal net = PayrollCalculator.calculateNetSalary(
                new BigDecimal("200000.00"), new BigDecimal("50000.00"), new BigDecimal("20000.00"));
        assertEquals(new BigDecimal("230000.00"), net);

        // Phạt vượt lương => Net về 0
        BigDecimal capped = PayrollCalculator.calculateNetSalary(
                new BigDecimal("100000.00"), BigDecimal.ZERO, new BigDecimal("150000.00"));
        assertEquals(BigDecimal.ZERO, capped);
    }

    @Test
    @DisplayName("calculatePeriodTotal: Tính chuẩn xác tổng quỹ lương kỳ")
    void calculatePeriodTotal_sumsAllSalaries() {
        List<BigDecimal> salaries = List.of(
                new BigDecimal("230000.00"),
                new BigDecimal("180000.00"),
                new BigDecimal("500000.00")
        );
        assertEquals(new BigDecimal("910000.00"), PayrollCalculator.calculatePeriodTotal(salaries));
        assertEquals(new BigDecimal("0.00"), PayrollCalculator.calculatePeriodTotal(null));
    }
}

