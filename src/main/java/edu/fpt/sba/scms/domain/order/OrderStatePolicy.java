package edu.fpt.sba.scms.domain.order;

import edu.fpt.sba.scms.common.enums.OrderStatus;

/**
 * Quy tắc chuyển đổi trạng thái và đóng băng đơn hàng (Pure Java - Domain).
 * Hiện thực các ràng buộc nghiệp vụ BR-26, BR-26b.
 */
public final class OrderStatePolicy {

    private OrderStatePolicy() {
        // Utility class
    }

    /**
     * BR-26b: Chỉ có thể hủy đơn hàng khi đang ở trạng thái PENDING.
     */
    public static boolean canCancel(OrderStatus status) {
        return status == OrderStatus.PENDING;
    }

    /**
     * BR-26: Đơn hàng bị đóng băng khi đã thanh toán (PAID) hoặc đã kết thúc (CANCELLED, REFUNDED).
     */
    public static boolean isFrozen(OrderStatus status) {
        if (status == null) {
            return false;
        }
        return status == OrderStatus.PAID
                || status == OrderStatus.CANCELLED
                || status == OrderStatus.REFUNDED
                || status == OrderStatus.PARTIALLY_REFUNDED;
    }

    /**
     * BR-21: Chỉ có thể áp dụng voucher khi đơn hàng đang PENDING và chưa có voucher trước đó.
     */
    public static boolean canApplyVoucher(OrderStatus status, boolean alreadyHasVoucher) {
        return status == OrderStatus.PENDING && !alreadyHasVoucher;
    }

    /**
     * Chỉ có thể gỡ voucher khi đơn hàng đang PENDING.
     */
    public static boolean canRemoveVoucher(OrderStatus status) {
        return status == OrderStatus.PENDING;
    }
}
