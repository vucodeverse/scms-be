package edu.fpt.sba.scms.security.web;

import edu.fpt.sba.scms.config.properties.AppProperties;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * Bọc refresh token vào cookie httpOnly khi bật {@code app.auth.cookie.refresh-enabled}.
 * <p>
 * Vì cookie được gửi tự động nên bật chế độ này kéo theo nghĩa vụ chống CSRF (xem {@code CsrfCookieFilter}):
 * mọi request đổi trạng thái phải kèm header {@code X-XSRF-TOKEN} khớp cookie {@code XSRF-TOKEN}.
 * <p>
 * Đặt ở tầng service; controller chỉ đọc cờ qua package {@code security.util}.
 */
@Component
@RequiredArgsConstructor
public class RefreshCookieSupport {

    /** Tên cookie/header CSRF cố định — Spring Security đọc đúng hai tên này. */
    public static final String CSRF_COOKIE = "XSRF-TOKEN";
    public static final String CSRF_HEADER = "X-XSRF-TOKEN";

    private static final Set<String> ALLOWED_SAME_SITE = Set.of("lax", "strict", "none");

    private final AppProperties appProperties;

    /** Fail-fast lúc khởi động: cấu hình cookie sai thì không chạy, thay vì lỗi im lặng lúc runtime. */
    @PostConstruct
    void validate() {
        AppProperties.Auth.Cookie cfg = appProperties.auth().cookie();
        String sameSite = cfg.sameSite() == null ? "" : cfg.sameSite().trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_SAME_SITE.contains(sameSite)) {
            throw new IllegalStateException(
                    "app.auth.cookie.same-site phải là Lax, Strict hoặc None (nhận: " + cfg.sameSite() + ").");
        }
        if ("none".equals(sameSite) && !cfg.secure()) {
            // Trình duyệt sẽ huỷ cookie SameSite=None không Secure -> phiên âm thầm không hoạt động.
            throw new IllegalStateException(
                    "app.auth.cookie.same-site=None bắt buộc app.auth.cookie.secure=true.");
        }
        if (cfg.refreshEnabled() && !cfg.refreshPath().startsWith("/")) {
            throw new IllegalStateException("app.auth.cookie.refresh-path phải bắt đầu bằng '/'.");
        }
    }

    public boolean enabled() {
        return appProperties.auth().cookie().refreshEnabled();
    }

    /** Cookie xoá (logout): value rỗng, maxAge 0. */
    public void write(HttpServletResponse response, String refreshToken) {
        write(response, refreshToken, refreshToken == null ? 0 : maxAgeSeconds());
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
        return readCookie(request, appProperties.auth().cookie().refreshName());
    }

    private static String readCookie(HttpServletRequest request, String name) {
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
