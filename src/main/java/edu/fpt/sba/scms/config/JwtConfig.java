package edu.fpt.sba.scms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Bean chung cho xác thực.
 * <p>
 * Spring Security 7 (Boot 4) không còn class {@code DaoAuthenticationProvider}; provider mặc định được
 * tạo bởi {@code InitializeUserDetailsBeanManagerConfigurer} từ bean duy nhất
 * {@link edu.fpt.sba.scms.security.user.ScmsUserDetailsService} + {@link PasswordEncoder} bên dưới.
 */
@Configuration
public class JwtConfig {

    /** BCrypt strength 12 cho mật khẩu đăng nhập (IMPLEMENTATION.md §2). */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
