package edu.fpt.sba.scms.domain;

import edu.fpt.sba.scms.common.enums.OrderStatus;
import edu.fpt.sba.scms.domain.order.OrderStatePolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Domain: OrderStatePolicy Unit Tests")
class OrderStatePolicyTest {

    @Test
    @DisplayName("canCancel: Chỉ cho phép hủy khi ở trạng thái PENDING")
    void canCancel_onlyPending() {
        assertTrue(OrderStatePolicy.canCancel(OrderStatus.PENDING));
        assertFalse(OrderStatePolicy.canCancel(OrderStatus.PAID));
        assertFalse(OrderStatePolicy.canCancel(OrderStatus.CANCELLED));
        assertFalse(OrderStatePolicy.canCancel(OrderStatus.REFUNDED));
    }

    @Test
    @DisplayName("isFrozen: Kiểm tra trạng thái đóng băng đơn hàng (BR-26)")
    void isFrozen_checksTerminalStates() {
        assertFalse(OrderStatePolicy.isFrozen(OrderStatus.PENDING));
        assertTrue(OrderStatePolicy.isFrozen(OrderStatus.PAID));
        assertTrue(OrderStatePolicy.isFrozen(OrderStatus.CANCELLED));
        assertTrue(OrderStatePolicy.isFrozen(OrderStatus.REFUNDED));
        assertTrue(OrderStatePolicy.isFrozen(OrderStatus.PARTIALLY_REFUNDED));
        assertFalse(OrderStatePolicy.isFrozen(null));
    }

    @Test
    @DisplayName("canApplyVoucher: Chỉ áp dụng khi PENDING và chưa có voucher trước đó")
    void canApplyVoucher_rules() {
        assertTrue(OrderStatePolicy.canApplyVoucher(OrderStatus.PENDING, false));
        assertFalse(OrderStatePolicy.canApplyVoucher(OrderStatus.PENDING, true));
        assertFalse(OrderStatePolicy.canApplyVoucher(OrderStatus.PAID, false));
        assertFalse(OrderStatePolicy.canApplyVoucher(OrderStatus.CANCELLED, false));
    }

    @Test
    @DisplayName("canRemoveVoucher: Chỉ cho phép gỡ khi đơn hàng đang PENDING")
    void canRemoveVoucher_onlyPending() {
        assertTrue(OrderStatePolicy.canRemoveVoucher(OrderStatus.PENDING));
        assertFalse(OrderStatePolicy.canRemoveVoucher(OrderStatus.PAID));
        assertFalse(OrderStatePolicy.canRemoveVoucher(OrderStatus.CANCELLED));
    }
}
