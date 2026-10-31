package edu.fpt.sba.scms.config;

import edu.fpt.sba.scms.security.filter.JwtAuthenticationFilter;
import edu.fpt.sba.scms.security.filter.ScmsAccessDeniedHandler;
import edu.fpt.sba.scms.security.filter.ScmsAuthenticationEntryPoint;
import edu.fpt.sba.scms.security.filter.BranchContextFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.ProviderNotFoundException;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

import static edu.fpt.sba.scms.config.SwaggerRoutes.PATHS;

/**
 * Cấu hình bảo mật — stateless JWT (IMPLEMENTATION.md §5.4, §8.5).
 * <ul>
 *   <li>permitAll: login, refresh, health, OPTIONS — cộng thêm api-docs/swagger <b>chỉ khi</b> Swagger bật
 *       <b>và</b> đang ở profile dev/test ({@link DevOnlyEndpoints}). Ở profile khác, Swagger bị chặn 401
 *       kể cả khi lỡ bật springdoc.</li>
 *   <li>Mọi request khác cần Bearer access token; phân quyền ở {@code @PreAuthorize} (method security).</li>
 *   <li>Entry point 401 và handler 403 đều trả envelope {@code ApiErrorResponse}.</li>
 *   <li>Header bảo vệ trình duyệt: HSTS, X-Content-Type-Options, X-Frame-Options DENY, CSP,
 *       Referrer-Policy, Permissions-Policy (mượn nguyên bộ OWASP headers của dự án RestAPI).</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /** Public không điều kiện. */
    public static final String[] PUBLIC_URLS = {
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/actuator/health",
            "/actuator/health/**",
            "/error",
            "/favicon.ico"
    };

    private static final String CSP_VALUE = "default-src 'self'; frame-ancestors 'none'";
    private static final String PERMISSIONS_POLICY = "geolocation=(), microphone=(), camera=()";

    private final AppProperties appProperties;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final BranchContextFilter branchContextFilter;
    private final ScmsAuthenticationEntryPoint authenticationEntryPoint;
    private final ScmsAccessDeniedHandler accessDeniedHandler;
    /** Có bean này nghĩa là đang chạy profile dev/test. */
    private final ObjectProvider<DevOnlyEndpoints> devEndpoints;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .headers(headers -> headers
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
                        .contentTypeOptions(contentType -> {
                        })
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .permissionsPolicyHeader(permissions -> permissions.policy(PERMISSIONS_POLICY))
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CSP_VALUE)))
                .exceptionHandling(eh -> eh.authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_URLS).permitAll()
                        // webhook SePay xác thực bằng chữ ký riêng (IMPLEMENTATION.md §5.4)
                        .requestMatchers(HttpMethod.POST, "/api/v1/webhooks/sepay").permitAll()
                        .requestMatchers(devOnlySwaggerRoutes()).permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(branchContextFilter, JwtAuthenticationFilter.class);
        // Tầng service tự kiểm mật khẩu (AuthServiceImpl + PasswordEncoder) để phân biệt chính xác
        // user không tồn tại / sai mật khẩu / tài khoản bị khóa. Nếu để một UserDetailsService bean
        // trong context, Spring Security 7 sẽ tự dựng thêm DaoAuthenticationProvider không cần thiết.
        http.authenticationProvider(new AuthenticationProvider() {
            @Override
            public Authentication authenticate(Authentication authentication) {
                throw new ProviderNotFoundException("Chỉ xác thực qua AuthService.");
            }

            @Override
            public boolean supports(Class<?> authentication) {
                return false;
            }
        });
        return http.build();
    }

    /**
     * Route Swagger chỉ được permitAll khi: Swagger được bật ({@code springdoc.api-docs.enabled=true})
     * <b>và</b> profile hiện tại là dev/test. Ở prod, mảng rỗng → request vào Swagger rơi vào
     * {@code anyRequest().authenticated()} → 401.
     */
    private String[] devOnlySwaggerRoutes() {
        boolean swaggerOn = appProperties.openapi().enabled();
        boolean devLike = devEndpoints.getIfAvailable() != null;
        if (!(swaggerOn && devLike)) {
            return new String[0];
        }
        return PATHS.clone();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList(appProperties.security().cors().allowedOrigins()));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Branch-Id", "X-Trace-Id",
                "Idempotency-Key", "X-Kiosk-Key"));
        config.setExposedHeaders(List.of("X-Trace-Id"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
