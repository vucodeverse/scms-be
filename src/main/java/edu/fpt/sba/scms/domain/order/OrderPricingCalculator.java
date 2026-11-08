package edu.fpt.sba.scms.domain.order;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Thuật toán và công thức tính toán giá trị đơn hàng (Pure Java - Domain).
 * Tuân thủ quy tắc kiến trúc: chỉ dùng JDK thuần, không phụ thuộc Spring/JPA/Database.
 */
public final class OrderPricingCalculator {

    private OrderPricingCalculator() {
        // Utility class
    }

    /**
     * Tính tổng thành tiền của một dòng đơn hàng:
     * lineTotal = (unitPrice + toppingTotal) * quantity
     */
    public static BigDecimal calculateLineTotal(BigDecimal unitPrice, BigDecimal toppingTotal, int quantity) {
        BigDecimal basePrice = unitPrice != null ? unitPrice : BigDecimal.ZERO;
        BigDecimal toppingPrice = toppingTotal != null ? toppingTotal : BigDecimal.ZERO;
        int qty = Math.max(0, quantity);

        return basePrice.add(toppingPrice).multiply(BigDecimal.valueOf(qty));
    }

    /**
     * Tính số tiền thanh toán cuối cùng (finalAmount):
     * finalAmount = max(0, totalAmount - discountAmount)
     */
    public static BigDecimal calculateFinalAmount(BigDecimal totalAmount, BigDecimal discountAmount) {
        BigDecimal total = totalAmount != null ? totalAmount : BigDecimal.ZERO;
        BigDecimal discount = discountAmount != null ? discountAmount : BigDecimal.ZERO;

        BigDecimal payable = total.subtract(discount);
        return payable.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : payable;
    }

    /**
     * Kiểm tra số tiền khách đưa có đủ để thanh toán hay không.
     */
    public static boolean isSufficientCash(BigDecimal cashReceived, BigDecimal finalAmount) {
        if (cashReceived == null || finalAmount == null) {
            return false;
        }
        return cashReceived.compareTo(finalAmount) >= 0;
    }

    /**
     * Tính số tiền thối lại cho khách:
     * changeReturned = cashReceived - finalAmount
     */
    public static BigDecimal calculateChange(BigDecimal cashReceived, BigDecimal finalAmount) {
        Objects.requireNonNull(cashReceived, "cashReceived must not be null");
        Objects.requireNonNull(finalAmount, "finalAmount must not be null");
        return cashReceived.subtract(finalAmount);
    }
}
