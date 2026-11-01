package edu.fpt.sba.scms.config.security;

import edu.fpt.sba.scms.config.SwaggerRoutes;
import edu.fpt.sba.scms.config.properties.AppProperties;

/**
 * Route không cần xác thực. Tách khỏi {@code SecurityConfig} để có một nguồn duy nhất cho test + filter chain.
 */
public final class PublicRoutes {

    /** Public không điều kiện. */
    public static final String[] PUBLIC_URLS = {
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/actuator/health",
            "/actuator/health/**",
            "/error",
            "/favicon.ico"
    };

    private PublicRoutes() {
    }

    /**
     * Route Swagger chỉ được permitAll khi: Swagger được bật ({@code app.openapi.enabled=true})
     * <b>và</b> profile hiện tại là dev/test. Ở prod, mảng rỗng → request vào Swagger rơi vào
     * {@code anyRequest().authenticated()} → 401.
     */
    public static String[] devOnlySwaggerRoutes(AppProperties props, boolean devLikeProfile) {
        if (!props.openapi().enabled() || !devLikeProfile) {
            return new String[0];
        }
        return SwaggerRoutes.PATHS.clone();
    }
}
