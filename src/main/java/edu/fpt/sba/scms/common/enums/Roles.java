package edu.fpt.sba.scms.common.enums;

import java.util.Arrays;
import java.util.Optional;

/**
 * 4 role cố định (= {@code app_user.position}, IMPLEMENTATION.md §8.5). Không dùng prefix ROLE_;
 * so khớp {@code role.code}.
 * <p>
 * <b>Chốt quy ước phân quyền:</b> authority = đúng {@code role.code} (vd. {@code COO}), KHÔNG có prefix
 * {@code ROLE_}. Vì vậy trong code phải dùng {@code hasAuthority('COO')},
 * <b>KHÔNG</b> dùng {@code hasRole('COO')} — {@code hasRole} tự ghép {@code ROLE_}
 * nên sẽ không khớp và luôn trả 403.
 * <pre>
 *   @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER')")
 * </pre>
 * Quy ước này được giữ ở một chỗ duy nhất: {@link edu.fpt.sba.scms.security.principal.UserPrincipal#getAuthorities()}.
 * <p>
 * Mỗi role tự khai báo màn hình landing (Open/Closed: thêm role mới chỉ sửa enum, không sửa service).
 */
public enum Roles {

    COO("SCR-10"),
    BRANCH_MANAGER("SCR-07"),
    CASHIER("SCR-04"),
    BARISTA("SCR-04"),
    WAITER("SCR-04");

    /** Authority nội bộ cho kiosk (X-Kiosk-Key), không phải role DB. */
    public static final String KIOSK = "KIOSK";

    private final String landing;

    Roles(String landing) {
        this.landing = landing;
    }

    /** Mã màn hình FE mở mặc định sau khi đăng nhập. */
    public String landing() {
        return landing;
    }

    /** Tên dùng trong JWT / authority (giữ nguyên tên enum). */
    public String code() {
        return name();
    }

    public static Optional<Roles> fromCode(String code) {
        return Arrays.stream(values()).filter(r -> r.name().equals(code)).findFirst();
    }

    /** Landing theo {@code role.code}; role lạ/null -> màn hình POS mặc định. */
    public static String landingFor(String code) {
        return fromCode(code).map(Roles::landing).orElse(CASHIER.landing);
    }
}
