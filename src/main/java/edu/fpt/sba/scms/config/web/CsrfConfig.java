package edu.fpt.sba.scms.config.web;

import edu.fpt.sba.scms.config.properties.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import java.time.Duration;

/**
 * CSRF stateful chỉ khi refresh token nằm trong cookie (trình duyệt tự gửi cookie sang site khác).
 * <ul>
 *   <li>Tắt cookie refresh: tắt CSRF — không có cookie phiên nào để lợi dụng.</li>
 *   <li>Bật cookie refresh: bật CSRF double-submit — token trong cookie {@code XSRF-TOKEN} (JS đọc được)
 *       phải khớp header {@code X-XSRF-TOKEN}. Cookie httpOnly của refresh không bị đọc được, attacker
 *       không lấy được token -> không gửi được request hợp lệ.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class CsrfConfig {

    private final AppProperties appProperties;

    @Bean
    public Customizer<CsrfConfigurer<HttpSecurity>> csrfCustomizer() {
        if (!appProperties.auth().cookie().refreshEnabled()) {
            // Stateless + không cookie phiên: cross-site request không mang danh tính nào để lợi dụng.
            return CsrfConfigurer::disable;
        }
        return csrf -> {
            CookieCsrfTokenRepository repo = CookieCsrfTokenRepository.withHttpOnlyFalse();
            repo.setCookieCustomizer(cookie -> cookie
                    .sameSite("Lax")   // request cross-site đơn giản (POST từ form) không mang cookie
                    .secure(appProperties.auth().cookie().secure())
                    .path("/")
                    .maxAge(Duration.ofMinutes(30))); // hạn cookie CSRF ngắn; server vẫn kiểm token mỗi request
            csrf.csrfTokenRepository(repo)
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                    .ignoringRequestMatchers("/api/v1/webhooks/**");   // webhook xác thực bằng chữ ký riêng
        };
    }
}
