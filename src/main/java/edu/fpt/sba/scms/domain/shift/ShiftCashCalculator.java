package edu.fpt.sba.scms.domain.shift;

import edu.fpt.sba.scms.common.enums.CashTransactionType;

import java.math.BigDecimal;
import java.util.List;

/**
 * Thuật toán và công thức tính toán tiền mặt trong ca két POS (Pure Java - Domain).
 * Hiện thực các quy tắc nghiệp vụ BR-34, BR-38, BR-39.
 * Tuân thủ quy tắc kiến trúc: chỉ dùng JDK thuần và common/enums, không phụ thuộc Spring/JPA/Database.
 */
public final class ShiftCashCalculator {

    private ShiftCashCalculator() {
        // Utility class
    }

    /**
     * DTO thuần của domain đại diện cho một bản ghi dòng tiền két.
     */
    public record CashEntry(CashTransactionType type, BigDecimal amount) {
    }

    /**
     * Kết quả tổng hợp các dòng tiền trong ca.
     */
    public record CashAggregation(
            BigDecimal totalIn,
            BigDecimal totalOut,
            BigDecimal totalRefund
    ) {
        public static CashAggregation empty() {
            return new CashAggregation(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }

    /**
     * Tổng hợp các khoản nạp, rút và hoàn tiền trong ca (Tell, Don't Ask).
     */
    public static CashAggregation aggregate(List<CashEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return CashAggregation.empty();
        }
        BigDecimal totalIn = BigDecimal.ZERO;
        BigDecimal totalOut = BigDecimal.ZERO;
        BigDecimal totalRefund = BigDecimal.ZERO;

        for (CashEntry entry : entries) {
            if (entry == null || entry.type() == null || entry.amount() == null) {
                continue;
            }
            switch (entry.type()) {
                case CASH_IN -> totalIn = totalIn.add(entry.amount());
                case CASH_OUT -> totalOut = totalOut.add(entry.amount());
                case REFUND_OUT -> totalRefund = totalRefund.add(entry.amount());
                default -> {
                    // Các loại khác (OPEN, CLOSE) không tính vào luân chuyển trong ca
                }
            }
        }
        return new CashAggregation(totalIn, totalOut, totalRefund);
    }

    /**
     * Tính toán số tiền chênh lệch giữa tiền đếm thực tế và sổ sách:
     * variance = countedCash - expectedCash (BR-38)
     */
    public static BigDecimal calculateVariance(BigDecimal countedCash, BigDecimal expectedCash) {
        BigDecimal counted = countedCash != null ? countedCash : BigDecimal.ZERO;
        BigDecimal expected = expectedCash != null ? expectedCash : BigDecimal.ZERO;
        return counted.subtract(expected);
    }

    /**
     * Kiểm tra xem có chênh lệch tiền mặt hay không (variance != 0) (BR-38).
     */
    public static boolean hasDiscrepancy(BigDecimal countedCash, BigDecimal expectedCash) {
        return calculateVariance(countedCash, expectedCash).compareTo(BigDecimal.ZERO) != 0;
    }

    /**
     * Kiểm tra số dư két tiền có đủ để rút hoặc hoàn tiền hay không:
     * availableCash >= amountToWithdraw (BR-34).
     */
    public static boolean isSufficientCash(BigDecimal availableCash, BigDecimal amountToWithdraw) {
        if (availableCash == null || amountToWithdraw == null) {
            return false;
        }
        if (amountToWithdraw.compareTo(BigDecimal.ZERO) <= 0) {
            return true;
        }
        return availableCash.compareTo(amountToWithdraw) >= 0;
    }

    /**
     * Cộng thêm tiền vào két (Cash In / Bán hàng tiền mặt):
     * result = currentExpected + amount (BR-39).
     */
    public static BigDecimal addCash(BigDecimal currentExpected, BigDecimal amount) {
        BigDecimal current = currentExpected != null ? currentExpected : BigDecimal.ZERO;
        BigDecimal add = amount != null ? amount : BigDecimal.ZERO;
        return current.add(add);
    }

    /**
     * Trừ bớt tiền khỏi két (Cash Out / Hoàn tiền mặt):
     * result = currentExpected - amount (BR-39).
     */
    public static BigDecimal deductCash(BigDecimal currentExpected, BigDecimal amount) {
        BigDecimal current = currentExpected != null ? currentExpected : BigDecimal.ZERO;
        BigDecimal deduct = amount != null ? amount : BigDecimal.ZERO;
        return current.subtract(deduct);
    }

    /**
     * Tính tổng số tiền kỳ vọng lý thuyết trong két:
     * expected = openingCash + cashIn - cashOut - refundOut + cashRevenue
     */
    public static BigDecimal calculateExpectedCash(BigDecimal openingCash,
                                                   BigDecimal totalCashIn,
                                                   BigDecimal totalCashOut,
                                                   BigDecimal totalRefundOut,
                                                   BigDecimal cashRevenue) {
        BigDecimal opening = openingCash != null ? openingCash : BigDecimal.ZERO;
        BigDecimal in = totalCashIn != null ? totalCashIn : BigDecimal.ZERO;
        BigDecimal out = totalCashOut != null ? totalCashOut : BigDecimal.ZERO;
        BigDecimal refund = totalRefundOut != null ? totalRefundOut : BigDecimal.ZERO;
        BigDecimal revenue = cashRevenue != null ? cashRevenue : BigDecimal.ZERO;

        return opening.add(in).subtract(out).subtract(refund).add(revenue);
    }
}
