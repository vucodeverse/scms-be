package edu.fpt.sba.scms.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Bean chung cho xác thực + bật validation cho {@link AppProperties}.
 * <p>
 * {@code @EnableConfigurationProperties(AppProperties.class)} (khác {@code @ConfigurationPropertiesScan}) sẽ
 * gắn validator vào binding, nên các ràng buộc {@code @Min}/{@code @Size(32)} trên secret JWT được kiểm lúc
 * khởi động — app fail-fast nếu ENV thiếu/ngắn thay vì chạy với cấu hình sai.
 */
@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class JwtConfig {

    /** BCrypt strength 12 cho mật khẩu đăng nhập (IMPLEMENTATION.md §2). */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
