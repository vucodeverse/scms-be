package edu.fpt.sba.scms.domain.voucher;

import edu.fpt.sba.scms.common.enums.DiscountType;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Công thức tính toán chiết khấu voucher (Pure Java - Domain).
 * Tuân thủ quy tắc nghiệp vụ BR-20, BR-21:
 * - PERCENT: giảm theo % giá trị đơn, làm tròn HALF_UP, tối đa bằng tổng tiền đơn hàng.
 * - FIXED: giảm theo số tiền cố định, tối đa bằng tổng tiền đơn hàng.
 */
public final class VoucherDiscountCalculator {

    private VoucherDiscountCalculator() {
        // Utility class
    }

    /**
     * Tính toán số tiền được giảm thực tế từ voucher.
     *
     * @param discountType  Loại giảm giá (PERCENT hoặc FIXED)
     * @param discountValue Giá trị giảm (số % hoặc số tiền cụ thể)
     * @param orderTotal    Tổng tiền hàng
     * @return Số tiền được giảm (không âm, không vượt quá orderTotal)
     */
    public static BigDecimal calculateDiscount(DiscountType discountType,
                                               BigDecimal discountValue,
                                               BigDecimal orderTotal) {
        if (discountType == null || discountValue == null || orderTotal == null) {
            return BigDecimal.ZERO;
        }
        if (discountValue.compareTo(BigDecimal.ZERO) <= 0 || orderTotal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal discount;
        if (discountType == DiscountType.PERCENT) {
            discount = orderTotal.multiply(discountValue)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        } else {
            discount = discountValue;
        }

        // Không bao giờ giảm vượt quá tổng tiền đơn
        if (discount.compareTo(orderTotal) > 0) {
            return orderTotal;
        }

        return discount;
    }
}
