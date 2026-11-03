package edu.fpt.sba.scms.service.support;

import edu.fpt.sba.scms.common.enums.Roles;
import edu.fpt.sba.scms.common.exception.InvalidFieldValueException;
import edu.fpt.sba.scms.model.Role;
import edu.fpt.sba.scms.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/**
 * Quy tắc "role nào được gán cho ai" của module user (IMPLEMENTATION.md §10.2.1, §7.2) — gom một chỗ để
 * service create/update/assign-role không lặp điều kiện và không lệch nhau.
 * <p>
 * Chỉ COO và BRANCH_MANAGER dùng username + password; Barista/Waiter/Cashier chấm công bằng PIN.
 * {@code position} và {@code role} của user luôn trùng nhau (userId.position = role.code) — xem {@code User}.
 */
@Component
@RequiredArgsConstructor
public class UserDirectory {

    /** Role cần username/password (đăng nhập web). */
    public static final Set<Roles> LOGIN_ROLES = EnumSet.of(Roles.COO, Roles.BRANCH_MANAGER);
    /** Role branch-manager được tạo/sửa trong chi nhánh của mình. */
    public static final Set<Roles> BRANCH_STAFF_ROLES = EnumSet.of(Roles.CASHIER, Roles.BARISTA, Roles.WAITER);
    /** Role có PIN chấm công Kiosk (tất cả role trong chi nhánh). */
    public static final Set<Roles> PIN_ROLES = EnumSet.copyOf(BRANCH_STAFF_ROLES);

    private final RoleRepository roleRepository;

    /** Dòng {@code role} tương ứng; chưa có role thì báo lỗi cấu hình chứ không tự tạo. */
    public Role roleOf(Roles position) {
        return roleRepository.findByCode(position.code())
                .orElseThrow(() -> new InvalidFieldValueException("position",
                        "Chưa cấu hình role " + position.code() + " trong bảng role."));
    }

    public static boolean requiresLoginAccount(Roles position) {
        return LOGIN_ROLES.contains(position);
    }

    public static boolean requiresPin(Roles position) {
        return PIN_ROLES.contains(position);
    }
}
