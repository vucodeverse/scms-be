package edu.fpt.sba.scms.security;

import edu.fpt.sba.scms.common.enums.Roles;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Quyền cố định theo role (RBAC, không ma trận — IMPLEMENTATION.md §17 #2).
 * Chỉ để FE ẩn/hiện menu; kiểm soát thật nằm ở {@code @PreAuthorize} + branch scope.
 * <p>
 * Khoá là {@link Roles} (không phải String) để compiler chặn role "ma" không tồn tại trong enum;
 * thêm role mới mà quên khai báo quyền thì {@code of()} vẫn trả {@link List#of()} an toàn thay vì lỗi.
 */
public final class PermissionCatalog {

    private static final Map<Roles, List<String>> BY_ROLE = build();

    private PermissionCatalog() {
    }

    public static List<String> of(String roleCode) {
        return Roles.fromCode(roleCode).map(BY_ROLE::get).orElse(List.of());
    }

    /** Toàn bộ danh mục quyền hệ thống để render ma trận phân quyền (UC02). */
    public static List<String> allPermissions() {
        return List.of(
                "BRANCH_MANAGE", "MANAGER_MANAGE", "ROLE_ASSIGN", "MENU_MANAGE", "VOUCHER_MANAGE",
                "SYNC_PUBLISH", "REPORT_VIEW", "SETTINGS_MANAGE", "PAYROLL_APPROVE", "AUDIT_VIEW",
                "REFUND_APPROVE", "ORDER_VIEW", "EMPLOYEE_MANAGE", "SCHEDULE_MANAGE", "ATTENDANCE_ADJUST",
                "INVENTORY_MANAGE", "STOCK_APPROVE", "CASH_APPROVE", "REFUND_REQUEST", "PAYROLL_MANAGE",
                "SHIFT_MANAGE", "POS_OPERATE", "SHIFT_OPEN", "SHIFT_CLOSE", "CASH_IN_OUT", "KIOSK_ATTENDANCE"
        );
    }

    private static Map<Roles, List<String>> build() {
        Map<Roles, List<String>> map = new EnumMap<>(Roles.class);
        map.put(Roles.COO, List.of("BRANCH_MANAGE", "MANAGER_MANAGE", "ROLE_ASSIGN", "MENU_MANAGE", "VOUCHER_MANAGE",
                "SYNC_PUBLISH", "REPORT_VIEW", "SETTINGS_MANAGE", "PAYROLL_APPROVE", "AUDIT_VIEW",
                "REFUND_APPROVE", "ORDER_VIEW", "SHIFT_MANAGE", "SHIFT_OPEN", "SHIFT_CLOSE", "CASH_IN_OUT",
                "CASH_APPROVE"));
        map.put(Roles.BRANCH_MANAGER, List.of("EMPLOYEE_MANAGE", "SCHEDULE_MANAGE", "ATTENDANCE_ADJUST",
                "INVENTORY_MANAGE", "STOCK_APPROVE", "CASH_APPROVE", "REFUND_REQUEST", "REFUND_APPROVE",
                "REPORT_VIEW", "PAYROLL_MANAGE", "ORDER_VIEW", "SHIFT_MANAGE", "SHIFT_OPEN", "SHIFT_CLOSE",
                "CASH_IN_OUT"));
        map.put(Roles.CASHIER, List.of("POS_OPERATE", "SHIFT_OPEN", "SHIFT_CLOSE", "REFUND_REQUEST",
                "ORDER_VIEW"));
        // Barista/Waiter chấm công qua Kiosk như nhân viên ca.
        List<String> staff = List.of("KIOSK_ATTENDANCE");
        map.put(Roles.BARISTA, staff);
        map.put(Roles.WAITER, staff);
        return Map.copyOf(map);
    }
}
