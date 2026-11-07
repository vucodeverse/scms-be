package edu.fpt.sba.scms.domain.payroll;

import edu.fpt.sba.scms.common.enums.PayPeriodStatus;

/**
 * Quy tắc vòng đời và trạng thái kỳ lương chi nhánh (Pure Java - Domain).
 * Hiện thực quy tắc nghiệp vụ BR-53:
 * - Bảng lương đã được phê duyệt (APPROVED) hoặc chi trả (PAID) bị khóa, không được sửa.
 */
public final class PayrollPeriodPolicy {

    private PayrollPeriodPolicy() {
        // Utility class
    }

    /**
     * BR-53: Kiểm tra xem kỳ lương có được phép chỉnh sửa phiếu lương hay không.
     * Chỉ được sửa khi đang ở trạng thái DRAFT.
     */
    public static boolean isEditable(PayPeriodStatus status) {
        return status == PayPeriodStatus.DRAFT;
    }

    /**
     * Kiểm tra xem kỳ lương có thể trình duyệt lên cấp trên (COO) hay không.
     */
    public static boolean canSubmit(PayPeriodStatus status) {
        return status == PayPeriodStatus.DRAFT;
    }

    /**
     * Kiểm tra xem kỳ lương có thể được phê duyệt bởi COO hay không.
     */
    public static boolean canApprove(PayPeriodStatus status) {
        return status == PayPeriodStatus.SUBMITTED;
    }

    /**
     * Kiểm tra xem kỳ lương có thể đánh dấu đã thanh toán hay không.
     */
    public static boolean canMarkAsPaid(PayPeriodStatus status) {
        return status == PayPeriodStatus.APPROVED;
    }
}

