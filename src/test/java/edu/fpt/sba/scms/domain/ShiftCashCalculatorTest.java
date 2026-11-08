package edu.fpt.sba.scms.domain;

import edu.fpt.sba.scms.common.enums.CashTransactionType;
import edu.fpt.sba.scms.domain.shift.ShiftCashCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Domain: ShiftCashCalculator Unit Tests")
class ShiftCashCalculatorTest {

    @Test
    @DisplayName("calculateVariance: Tính đúng độ chênh lệch thực tế và sổ sách")
    void calculateVariance_calculatesCorrectly() {
        // Thực tế 1.050.000, sổ sách 1.000.000 => Thừa 50.000
        BigDecimal variancePositive = ShiftCashCalculator.calculateVariance(
                BigDecimal.valueOf(1050000), BigDecimal.valueOf(1000000));
        assertEquals(BigDecimal.valueOf(50000), variancePositive);

        // Thực tế 950.000, sổ sách 1.000.000 => Thiếu 50.000 (-50.000)
        BigDecimal varianceNegative = ShiftCashCalculator.calculateVariance(
                BigDecimal.valueOf(950000), BigDecimal.valueOf(1000000));
        assertEquals(BigDecimal.valueOf(-50000), varianceNegative);

        // Khớp 100% => 0
        BigDecimal varianceZero = ShiftCashCalculator.calculateVariance(
                BigDecimal.valueOf(1000000), BigDecimal.valueOf(1000000));
        assertEquals(BigDecimal.ZERO, varianceZero);

        // Null an toàn
        assertEquals(BigDecimal.valueOf(100), ShiftCashCalculator.calculateVariance(BigDecimal.valueOf(100), null));
        assertEquals(BigDecimal.valueOf(-100), ShiftCashCalculator.calculateVariance(null, BigDecimal.valueOf(100)));
    }

    @Test
    @DisplayName("hasDiscrepancy: Nhận diện chính xác khi có chênh lệch tiền mặt (BR-38)")
    void hasDiscrepancy_detectsVariance() {
        assertTrue(ShiftCashCalculator.hasDiscrepancy(BigDecimal.valueOf(1010000), BigDecimal.valueOf(1000000)));
        assertTrue(ShiftCashCalculator.hasDiscrepancy(BigDecimal.valueOf(990000), BigDecimal.valueOf(1000000)));
        assertFalse(ShiftCashCalculator.hasDiscrepancy(BigDecimal.valueOf(1000000), BigDecimal.valueOf(1000000)));
    }

    @Test
    @DisplayName("isSufficientCash: Kiểm tra số dư két có đủ để rút hoặc hoàn tiền (BR-34)")
    void isSufficientCash_checksSufficiency() {
        assertTrue(ShiftCashCalculator.isSufficientCash(BigDecimal.valueOf(500000), BigDecimal.valueOf(300000)));
        assertTrue(ShiftCashCalculator.isSufficientCash(BigDecimal.valueOf(500000), BigDecimal.valueOf(500000)));
        assertFalse(ShiftCashCalculator.isSufficientCash(BigDecimal.valueOf(500000), BigDecimal.valueOf(500001)));
        assertFalse(ShiftCashCalculator.isSufficientCash(null, BigDecimal.valueOf(100000)));
        assertFalse(ShiftCashCalculator.isSufficientCash(BigDecimal.valueOf(100000), null));
    }

    @Test
    @DisplayName("addCash and deductCash: Cộng/trừ tiền két chính xác (BR-39)")
    void addAndDeductCash_updatesBalance() {
        BigDecimal afterIn = ShiftCashCalculator.addCash(BigDecimal.valueOf(1000000), BigDecimal.valueOf(200000));
        assertEquals(BigDecimal.valueOf(1200000), afterIn);

        BigDecimal afterOut = ShiftCashCalculator.deductCash(afterIn, BigDecimal.valueOf(150000));
        assertEquals(BigDecimal.valueOf(1050000), afterOut);
    }

    @Test
    @DisplayName("calculateExpectedCash: Tính chuẩn xác tổng kỳ vọng két từ các luồng tiền")
    void calculateExpectedCash_aggregatesAllFlows() {
        // Mở ca 1tr, nạp 200k, rút 50k, hoàn 50k, doanh thu tiền mặt 300k => 1.4tr
        BigDecimal expected = ShiftCashCalculator.calculateExpectedCash(
                BigDecimal.valueOf(1000000),
                BigDecimal.valueOf(200000),
                BigDecimal.valueOf(50000),
                BigDecimal.valueOf(50000),
                BigDecimal.valueOf(300000)
        );
        assertEquals(BigDecimal.valueOf(1400000), expected);
    }

    @Test
    @DisplayName("aggregate: Tổng hợp chính xác các giao dịch dòng tiền trong ca")
    void aggregate_sumsTransactionsCorrectly() {
        List<ShiftCashCalculator.CashEntry> entries = List.of(
                new ShiftCashCalculator.CashEntry(CashTransactionType.OPEN, BigDecimal.valueOf(1000000)),
                new ShiftCashCalculator.CashEntry(CashTransactionType.CASH_IN, BigDecimal.valueOf(200000)),
                new ShiftCashCalculator.CashEntry(CashTransactionType.CASH_IN, BigDecimal.valueOf(300000)),
                new ShiftCashCalculator.CashEntry(CashTransactionType.CASH_OUT, BigDecimal.valueOf(150000)),
                new ShiftCashCalculator.CashEntry(CashTransactionType.REFUND_OUT, BigDecimal.valueOf(50000)),
                new ShiftCashCalculator.CashEntry(CashTransactionType.CLOSE, BigDecimal.valueOf(1300000))
        );

        ShiftCashCalculator.CashAggregation result = ShiftCashCalculator.aggregate(entries);
        assertEquals(BigDecimal.valueOf(500000), result.totalIn());
        assertEquals(BigDecimal.valueOf(150000), result.totalOut());
        assertEquals(BigDecimal.valueOf(50000), result.totalRefund());
    }
}
