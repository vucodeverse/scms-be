package edu.fpt.sba.scms.config.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Header bảo vệ trình duyệt: HSTS, X-Content-Type-Options, X-Frame-Options DENY, CSP, Referrer-Policy,
 * Permissions-Policy (bộ OWASP headers).
 */
@Configuration(proxyBeanMethods = false)
public class HeadersConfig {

    private static final String CSP_VALUE = "default-src 'self'; frame-ancestors 'none'";
    private static final String PERMISSIONS_POLICY = "geolocation=(), microphone=(), camera=()";

    @Bean
    public Customizer<HeadersConfigurer<HttpSecurity>> securityHeadersCustomizer() {
        return headers -> headers
                .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
                .contentTypeOptions(Customizer.withDefaults())
                .frameOptions(frame -> frame.deny())
                .referrerPolicy(referrer -> referrer
                        .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                .permissionsPolicyHeader(permissions -> permissions.policy(PERMISSIONS_POLICY))
                .contentSecurityPolicy(csp -> csp.policyDirectives(CSP_VALUE));
    }
}
