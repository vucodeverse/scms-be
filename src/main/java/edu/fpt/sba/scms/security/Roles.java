package edu.fpt.sba.scms.security;

/**
 * 4 role cố định (IMPLEMENTATION.md §8.5). Không dùng prefix ROLE_; so khớp {@code role.code}.
 * <p>
 * <b>Chốt quy ước phân quyền:</b> authority = đúng {@code role.code} (vd. {@code COO}), KHÔNG có prefix
 * {@code ROLE_}. Vì vậy trong code phải dùng {@code hasAuthority('COO')},
 * <b>KHÔNG</b> dùng {@code hasRole('COO')} — {@code hasRole} tự ghép {@code ROLE_}
 * nên sẽ không khớp và luôn trả 403.
 * <pre>
 *   @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER')")
 * </pre>
 * Quy ước này được giữ ở một chỗ duy nhất: {@link edu.fpt.sba.scms.dto.UserPrincipal#getAuthorities()}.
 */
public final class Roles {

    public static final String COO = "COO";
    public static final String BRANCH_MANAGER = "BRANCH_MANAGER";
    public static final String CASHIER = "CASHIER";
    public static final String STAFF = "STAFF";
    /** Authority nội bộ cho kiosk (X-Kiosk-Key), không phải role DB. */
    public static final String KIOSK = "KIOSK";

    private Roles() {
    }
}
