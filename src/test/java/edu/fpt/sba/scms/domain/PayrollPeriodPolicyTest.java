package edu.fpt.sba.scms.domain;

import edu.fpt.sba.scms.common.enums.PayPeriodStatus;
import edu.fpt.sba.scms.domain.payroll.PayrollPeriodPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Domain: PayrollPeriodPolicy Unit Tests")
class PayrollPeriodPolicyTest {

    @Test
    @DisplayName("isEditable: Chỉ cho phép chỉnh sửa phiếu lương khi ở trạng thái DRAFT (BR-53)")
    void isEditable_onlyDraft() {
        assertTrue(PayrollPeriodPolicy.isEditable(PayPeriodStatus.DRAFT));
        assertFalse(PayrollPeriodPolicy.isEditable(PayPeriodStatus.SUBMITTED));
        assertFalse(PayrollPeriodPolicy.isEditable(PayPeriodStatus.APPROVED));
        assertFalse(PayrollPeriodPolicy.isEditable(PayPeriodStatus.PAID));
    }

    @Test
    @DisplayName("Vòng đời trạng thái bảng lương: Submit -> Approve -> Pay")
    void lifecycle_transitions() {
        assertTrue(PayrollPeriodPolicy.canSubmit(PayPeriodStatus.DRAFT));
        assertFalse(PayrollPeriodPolicy.canSubmit(PayPeriodStatus.APPROVED));

        assertTrue(PayrollPeriodPolicy.canApprove(PayPeriodStatus.SUBMITTED));
        assertFalse(PayrollPeriodPolicy.canApprove(PayPeriodStatus.DRAFT));

        assertTrue(PayrollPeriodPolicy.canMarkAsPaid(PayPeriodStatus.APPROVED));
        assertFalse(PayrollPeriodPolicy.canMarkAsPaid(PayPeriodStatus.SUBMITTED));
    }
}

