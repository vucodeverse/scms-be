package edu.fpt.sba.scms.security.filter;

import jakarta.servlet.Filter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Tắt đăng ký tự động của các filter bean vào servlet container.
 * <p>
 * Một filter gắn {@code @Component} và cài bằng {@code addFilterBefore/After} sẽ chạy <b>trong</b>
 * Spring Security chain; nhưng Spring Boot còn tự đăng ký nó như servlet filter ở {@code REQUEST} order.
 * Kết quả: filter chạy <b>hai lần</b> mỗi request (BAD_REQUEST/response bị ghi hai lần, auth chạy lại,
 * MDC clear 2 lần...). Ở Boot 4 hành vi này cũng đã rõ hơn, nhưng để chắc chắn ta tắt tường minh —
 * lúc đó thứ tự filter chỉ nằm ở đúng một chỗ: {@link edu.fpt.sba.scms.config.SecurityConfig}.
 */
@Configuration(proxyBeanMethods = false)
public class FilterRegistrations {

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(
            JwtAuthenticationFilter filter) {
        return disabled(filter);
    }

    @Bean
    public FilterRegistrationBean<BranchContextFilter> branchContextFilterRegistration(
            BranchContextFilter filter) {
        return disabled(filter);
    }

    private static <F extends Filter> FilterRegistrationBean<F> disabled(F filter) {
        FilterRegistrationBean<F> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
