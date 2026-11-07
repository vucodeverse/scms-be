package edu.fpt.sba.scms.domain;

import edu.fpt.sba.scms.common.enums.OrderStatus;
import edu.fpt.sba.scms.domain.refund.RefundPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Domain: RefundPolicy Unit Tests")
class RefundPolicyTest {

    @Test
    @DisplayName("requiresCooApproval: Hoàn tiền > 500.000 đ bắt buộc COO duyệt (BR-35)")
    void requiresCooApproval_threshold500k() {
        // Đúng 500k => Branch Manager duyệt được
        assertFalse(RefundPolicy.requiresCooApproval(BigDecimal.valueOf(500000)));

        // 500.001 đ => Bắt buộc COO
        assertTrue(RefundPolicy.requiresCooApproval(BigDecimal.valueOf(500001)));

        // 1.000.000 đ => Bắt buộc COO
        assertTrue(RefundPolicy.requiresCooApproval(BigDecimal.valueOf(1000000)));

        // 200.000 đ => Branch Manager duyệt được
        assertFalse(RefundPolicy.requiresCooApproval(BigDecimal.valueOf(200000)));

        assertFalse(RefundPolicy.requiresCooApproval(null));
    }

    @Test
    @DisplayName("isExceedsPaidAmount: Chặn hoàn tiền vượt quá số tiền đơn hàng (BR-34)")
    void isExceedsPaidAmount_validatesAmount() {
        assertTrue(RefundPolicy.isExceedsPaidAmount(BigDecimal.valueOf(100001), BigDecimal.valueOf(100000)));
        assertFalse(RefundPolicy.isExceedsPaidAmount(BigDecimal.valueOf(100000), BigDecimal.valueOf(100000)));
        assertFalse(RefundPolicy.isExceedsPaidAmount(BigDecimal.valueOf(80000), BigDecimal.valueOf(100000)));
    }

    @Test
    @DisplayName("determinePostRefundStatus: Phân loại REFUNDED và PARTIALLY_REFUNDED")
    void determinePostRefundStatus_determinesStatus() {
        assertEquals(OrderStatus.REFUNDED,
                RefundPolicy.determinePostRefundStatus(BigDecimal.valueOf(100000), BigDecimal.valueOf(100000)));
        assertEquals(OrderStatus.REFUNDED,
                RefundPolicy.determinePostRefundStatus(BigDecimal.valueOf(120000), BigDecimal.valueOf(100000)));
        assertEquals(OrderStatus.PARTIALLY_REFUNDED,
                RefundPolicy.determinePostRefundStatus(BigDecimal.valueOf(80000), BigDecimal.valueOf(100000)));
    }

    @Test
    @DisplayName("calculateRemainingAmount: Tính chuẩn xác số tiền còn lại sau khi hoàn")
    void calculateRemainingAmount_calculatesCorrectly() {
        assertEquals(BigDecimal.valueOf(40000),
                RefundPolicy.calculateRemainingAmount(BigDecimal.valueOf(100000), BigDecimal.valueOf(60000)));
        assertEquals(BigDecimal.ZERO,
                RefundPolicy.calculateRemainingAmount(BigDecimal.valueOf(100000), BigDecimal.valueOf(100000)));
    }
}

