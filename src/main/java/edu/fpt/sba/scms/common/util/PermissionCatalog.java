package edu.fpt.sba.scms.common.util;

import java.util.List;
import java.util.Map;

/**
 * Quyền cố định theo role (RBAC, không ma trận — IMPLEMENTATION.md §17 #2).
 * Chỉ để FE ẩn/hiện menu; kiểm soát thật nằm ở {@code @PreAuthorize} + branch scope.
 */
public final class PermissionCatalog {

    private static final Map<String, List<String>> BY_ROLE = Map.of(
            "COO", List.of("BRANCH_MANAGE", "MANAGER_MANAGE", "ROLE_ASSIGN", "MENU_MANAGE", "VOUCHER_MANAGE",
                    "SYNC_PUBLISH", "REPORT_VIEW", "SETTINGS_MANAGE", "PAYROLL_APPROVE", "AUDIT_VIEW",
                    "REFUND_APPROVE", "ORDER_VIEW"),
            "BRANCH_MANAGER", List.of("EMPLOYEE_MANAGE", "SCHEDULE_MANAGE", "ATTENDANCE_ADJUST", "INVENTORY_MANAGE",
                    "STOCK_APPROVE", "CASH_APPROVE", "REFUND_REQUEST", "REFUND_APPROVE", "REPORT_VIEW",
                    "PAYROLL_MANAGE", "ORDER_VIEW", "SHIFT_MANAGE"),
            "CASHIER", List.of("POS_OPERATE", "SHIFT_OPEN", "SHIFT_CLOSE", "CASH_IN_OUT", "REFUND_REQUEST",
                    "ORDER_VIEW"),
            "STAFF", List.of("KIOSK_ATTENDANCE"));

    private PermissionCatalog() {
    }

    public static List<String> of(String roleCode) {
        return BY_ROLE.getOrDefault(roleCode, List.of());
    }
}
