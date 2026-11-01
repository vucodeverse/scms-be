package edu.fpt.sba.scms.config;

import edu.fpt.sba.scms.config.properties.AppProperties;
import edu.fpt.sba.scms.config.security.PublicRoutes;
import edu.fpt.sba.scms.security.filter.BranchContextFilter;
import edu.fpt.sba.scms.security.filter.CsrfCookieFilter;
import edu.fpt.sba.scms.security.filter.JwtAuthenticationFilter;
import edu.fpt.sba.scms.security.filter.ScmsAccessDeniedHandler;
import edu.fpt.sba.scms.security.filter.ScmsAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfFilter;

/**
 * Cấu hình bảo mật — stateless JWT (IMPLEMENTATION.md §5.4, §8.5).
 * <ul>
 *   <li>permitAll: login, refresh, health, OPTIONS — cộng thêm api-docs/swagger <b>chỉ khi</b> Swagger bật
 *       <b>và</b> đang ở profile dev/test ({@link DevOnlyEndpoints}). Ở profile khác, Swagger bị chặn 401
 *       kể cả khi lỡ bật springdoc.</li>
 *   <li>Mọi request khác cần Bearer access token; phân quyền ở {@code @PreAuthorize} (method security).</li>
 *   <li>Entry point 401 và handler 403 đều trả envelope {@code ApiErrorResponse}.</li>
 * </ul>
 * <p>
 * Chi tiết từng nhóm cấu hình được tách ra để mỗi file một trách nhiệm:
 * {@code config.security.CsrfConfig} (CSRF), {@code config.security.CorsConfig} (CORS),
 * {@code config.security.HeadersConfig} (security headers), {@code config.security.PublicRoutes} (route public).
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final AppProperties appProperties;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final BranchContextFilter branchContextFilter;
    private final ScmsAuthenticationEntryPoint authenticationEntryPoint;
    private final ScmsAccessDeniedHandler accessDeniedHandler;
    /** Có bean này nghĩa là đang chạy profile dev/test. */
    private final ObjectProvider<DevOnlyEndpoints> devEndpoints;

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            Customizer<CsrfConfigurer<HttpSecurity>> csrfCustomizer,
            Customizer<HeadersConfigurer<HttpSecurity>> headersCustomizer) throws Exception {
        boolean csrfEnabled = appProperties.auth().cookie().refreshEnabled();

        http.csrf(csrfCustomizer)
                .cors(Customizer.withDefaults())
                .headers(headersCustomizer)
                .exceptionHandling(eh -> eh.authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PublicRoutes.PUBLIC_URLS).permitAll()
                        // webhook SePay xác thực bằng chữ ký riêng. CHƯA có controller: khi implement endpoint
                        // bắt buộc verify HMAC-SHA256 với app.sepay.webhook-secret ngay trong controller.
                        .requestMatchers(HttpMethod.POST, "/api/v1/webhooks/sepay").permitAll()
                        .requestMatchers(PublicRoutes.devOnlySwaggerRoutes(
                                appProperties, devEndpoints.getIfAvailable() != null)).permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(branchContextFilter, JwtAuthenticationFilter.class);

        if (csrfEnabled) {
            // Buộc ghi cookie XSRF-TOKEN ngay cả khi client chưa đọc token (deferred loading của Spring Security 6+).
            http.addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class);
        }
        // Không dùng AuthenticationManager: AuthServiceImpl tự kiểm mật khẩu bằng PasswordEncoder.
        return http.build();
    }
}
