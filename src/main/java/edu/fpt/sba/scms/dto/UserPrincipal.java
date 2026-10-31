package edu.fpt.sba.scms.dto;

import edu.fpt.sba.scms.security.Roles;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Principal đặt vào SecurityContext bởi {@code JwtAuthenticationFilter}; cũng là {@link UserDetails}
 * cho luồng kiểm mật khẩu qua {@code AuthenticationManager} (nếu dùng).
 *
 * @param userId   app_user.user_id
 * @param username tên đăng nhập
 * @param fullName họ tên hiển thị (claim {@code name})
 * @param role     role.code (COO | BRANCH_MANAGER | CASHIER | STAFF)
 * @param branchId chi nhánh; null với COO
 * @param jti      jti của access token (null khi không từ JWT) — dùng khi logout
 */
public record UserPrincipal(
        Integer userId,
        String username,
        String fullName,
        String role,
        Integer branchId,
        String jti) implements UserDetails {

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return role == null ? List.of() : List.of(new SimpleRoleAuthority(role));
    }

    @Override
    public String getPassword() {
        // Không giữ password trong principal của request JWT.
        return null;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    public boolean isCoo() {
        return Roles.COO.equals(role);
    }

    /** Authority = đúng {@code role.code}, không prefix ROLE_ (IMPLEMENTATION.md §8.5). */
    private record SimpleRoleAuthority(String authority) implements GrantedAuthority {

        @Override
        public String getAuthority() {
            return authority;
        }
    }
}
