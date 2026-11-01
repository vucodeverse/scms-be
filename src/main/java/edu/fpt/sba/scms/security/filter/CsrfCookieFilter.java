package edu.fpt.sba.scms.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Kích hoạt việc <b>ghi cookie CSRF</b> bằng cách đọc {@link CsrfToken#getToken()}.
 * <p>
 * Khi {@code SecurityConfig} bật CSRF với {@code CookieCsrfTokenRepository}, Spring Security 6+ (BREACH
 * deferred loading) chỉ ghi cookie {@code XSRF-TOKEN} khi có ai đó đọc token. SPA gọi {@code GET} đầu tiên
 * sẽ không nhận được cookie -> không có gì để gửi trong header {@code X-XSRF-TOKEN} ở request tiếp theo.
 * Filter này buộc việc render đó xảy ra trên mọi request trong chain CSRF.
 * <p>
 * Chỉ được đăng ký vào chain khi chế độ refresh cookie bật — xem {@code SecurityConfig#csrfConfigurer}.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken csrfToken) {
            csrfToken.getToken();
        }
        chain.doFilter(request, response);
    }
}
