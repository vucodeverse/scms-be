package edu.fpt.sba.scms.config.security;

import edu.fpt.sba.scms.config.properties.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/** CORS cho FE SPA. Danh sách origin lấy từ {@code app.security.cors.allowed-origins}. */
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class CorsConfig {

    private final AppProperties appProperties;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        String[] origins = appProperties.security().cors().allowedOrigins();
        // allowCredentials=true + wildcard origin = mọi site gửi được cookie/credential sang API này.
        // Spring sẽ echo lại origin thay vì từ chối -> phải chặn ngay lúc khởi động.
        if (Arrays.stream(origins).anyMatch(o -> "*".equals(o.trim()))) {
            throw new IllegalStateException(
                    "CORS: không được dùng '*' trong app.security.cors.allowed-origins khi bật credentials. "
                            + "Liệt kê origin cụ thể (vd. http://localhost:5173).");
        }
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList(origins));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Branch-Id", "X-Trace-Id",
                "Idempotency-Key", "X-Kiosk-Key"));
        config.setExposedHeaders(List.of("X-Trace-Id", "Retry-After"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
