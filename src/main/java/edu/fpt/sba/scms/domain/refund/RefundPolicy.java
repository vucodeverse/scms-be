package edu.fpt.sba.scms.domain.refund;

import edu.fpt.sba.scms.common.enums.OrderStatus;

import java.math.BigDecimal;

/**
 * Quy tắc nghiệp vụ và công thức tính toán hoàn tiền đơn hàng (Pure Java - Domain).
 * Hiện thực các quy tắc nghiệp vụ BR-34, BR-35.
 */
public final class RefundPolicy {

    /**
     * Ngưỡng hoàn tiền yêu cầu phê duyệt từ cấp COO: 500.000 VNĐ (BR-35).
     */
    public static final BigDecimal COO_APPROVAL_THRESHOLD = new BigDecimal("500000");

    private RefundPolicy() {
        // Utility class
    }

    /**
     * BR-35: Kiểm tra xem khoản hoàn tiền có vượt quá ngưỡng 500.000 đ và bắt buộc
     * COO phê duyệt hay không.
     */
    public static boolean requiresCooApproval(BigDecimal amount) {
        if (amount == null) {
            return false;
        }
        return amount.compareTo(COO_APPROVAL_THRESHOLD) > 0;
    }

    /**
     * BR-34: Kiểm tra số tiền hoàn có vượt quá số tiền đơn hàng đã thanh toán hay không.
     */
    public static boolean isExceedsPaidAmount(BigDecimal refundAmount, BigDecimal paidAmount) {
        if (refundAmount == null || paidAmount == null) {
            return false;
        }
        return refundAmount.compareTo(paidAmount) > 0;
    }

    /**
     * Xác định trạng thái của đơn hàng sau khi hoàn tiền:
     * - Nếu hoàn toàn bộ (refundAmount >= orderFinalAmount): REFUNDED
     * - Nếu hoàn một phần: PARTIALLY_REFUNDED
     */
    public static OrderStatus determinePostRefundStatus(BigDecimal refundAmount, BigDecimal orderFinalAmount) {
        if (refundAmount == null || orderFinalAmount == null) {
            return OrderStatus.PARTIALLY_REFUNDED;
        }
        return refundAmount.compareTo(orderFinalAmount) >= 0
                ? OrderStatus.REFUNDED
                : OrderStatus.PARTIALLY_REFUNDED;
    }

    /**
     * Tính số tiền còn lại của đơn hàng sau khi hoàn một phần tiền:
     * remaining = max(0, orderFinalAmount - refundAmount)
     */
    public static BigDecimal calculateRemainingAmount(BigDecimal orderFinalAmount, BigDecimal refundAmount) {
        BigDecimal total = orderFinalAmount != null ? orderFinalAmount : BigDecimal.ZERO;
        BigDecimal refunded = refundAmount != null ? refundAmount : BigDecimal.ZERO;

        BigDecimal remaining = total.subtract(refunded);
        return remaining.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : remaining;
    }
}

