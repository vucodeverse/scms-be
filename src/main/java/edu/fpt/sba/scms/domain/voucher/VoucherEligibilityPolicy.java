package edu.fpt.sba.scms.domain.voucher;

import edu.fpt.sba.scms.common.enums.RecordStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;

/**
 * Quy tắc kiểm tra tính hợp lệ của voucher khi áp dụng vào đơn hàng (Pure Java - Domain).
 * Hiện thực ràng buộc nghiệp vụ UC13, BR-20, BR-21.
 */
public final class VoucherEligibilityPolicy {

    private VoucherEligibilityPolicy() {
        // Utility class
    }

    /**
     * Voucher phải ở trạng thái ACTIVE.
     */
    public static boolean isActive(RecordStatus status) {
        return status == RecordStatus.ACTIVE;
    }

    /**
     * Thời điểm kiểm tra phải nằm trong khoảng [startDate, endDate].
     */
    public static boolean isWithinPeriod(Instant now, Instant startDate, Instant endDate) {
        if (now == null) {
            return false;
        }
        if (startDate != null && now.isBefore(startDate)) {
            return false;
        }
        if (endDate != null && now.isAfter(endDate)) {
            return false;
        }
        return true;
    }

    /**
     * Kiểm tra số lần đã sử dụng có vượt quá hoặc bằng giới hạn cho phép hay không.
     */
    public static boolean isUsageLimitReached(Integer usageLimit, int usedCount) {
        return usageLimit != null && usedCount >= usageLimit;
    }

    /**
     * Kiểm tra giá trị đơn hàng có đạt giá trị tối thiểu để áp dụng voucher hay không.
     */
    public static boolean meetsMinimumOrderAmount(BigDecimal orderTotal, BigDecimal minOrderAmount) {
        if (minOrderAmount == null) {
            return true;
        }
        if (orderTotal == null) {
            return false;
        }
        return orderTotal.compareTo(minOrderAmount) >= 0;
    }

    /**
     * Kiểm tra phạm vi sản phẩm/danh mục của voucher: nếu cả hai tập cho phép đều rỗng thì áp toàn menu;
     * ngược lại đơn hàng phải chứa ít nhất một sản phẩm hoặc danh mục thuộc phạm vi cho phép.
     */
    public static boolean isScopeEligible(Collection<Integer> allowedProductIds,
                                          Collection<Integer> allowedCategoryIds,
                                          Collection<Integer> orderProductIds,
                                          Collection<Integer> orderCategoryIds) {
        boolean hasProductScope = allowedProductIds != null && !allowedProductIds.isEmpty();
        boolean hasCategoryScope = allowedCategoryIds != null && !allowedCategoryIds.isEmpty();
        if (!hasProductScope && !hasCategoryScope) {
            return true;
        }
        if (hasProductScope && orderProductIds != null) {
            for (Integer pid : orderProductIds) {
                if (pid != null && allowedProductIds.contains(pid)) {
                    return true;
                }
            }
        }
        if (hasCategoryScope && orderCategoryIds != null) {
            for (Integer cid : orderCategoryIds) {
                if (cid != null && allowedCategoryIds.contains(cid)) {
                    return true;
                }
            }
        }
        return false;
    }
}
