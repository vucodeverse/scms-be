package edu.fpt.sba.scms.service.support;

import edu.fpt.sba.scms.config.AppProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Bọc refresh token vào cookie httpOnly khi bật {@code app.auth.cookie.refresh-enabled}.
 * Đặt ở tầng service để controller không phụ thuộc {@code config} (IMPLEMENTATION.md §5.1).
 */
@Component
@RequiredArgsConstructor
public class RefreshCookieSupport {

    private final AppProperties appProperties;

    public boolean enabled() {
        return appProperties.auth().cookie().refreshEnabled();
    }

    /** Cookie xoá (logout): value rỗng, maxAge 0. */
    public void write(HttpServletResponse response, String refreshToken) {
        write(response, refreshToken, maxAgeSeconds());
    }

    /**
     * Ghi cookie với {@code maxAgeSeconds} tường minh — controller truyền theo thời gian sống còn lại của
     * refresh token (login: hạn rememberMe/session; refresh: hạn mới sau rotation) để cookie và token
     * không lệch nhau.
     */
    public void write(HttpServletResponse response, String refreshToken, long maxAgeSeconds) {
        AppProperties.Auth.Cookie cfg = appProperties.auth().cookie();
        Cookie cookie = new Cookie(cfg.refreshName(), refreshToken == null ? "" : refreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(cfg.secure());
        cookie.setPath(cfg.refreshPath());
        cookie.setAttribute("SameSite", cfg.sameSite());
        cookie.setMaxAge(refreshToken == null ? 0 : Math.toIntExact(Math.max(0, maxAgeSeconds)));
        response.addCookie(cookie);
    }

    /** Hạn dài nhất của refresh token (rememberMe) — dùng cho cookie khi biết chắc token phát ra hạn này. */
    public long maxAgeSeconds() {
        return appProperties.jwt().refreshTtlDays() * 24 * 3600;
    }

    public String read(HttpServletRequest request) {
        String name = appProperties.auth().cookie().refreshName();
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (name.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }
}
