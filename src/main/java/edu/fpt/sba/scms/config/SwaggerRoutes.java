package edu.fpt.sba.scms.config;

/**
 * Đường dẫn tài liệu API — chỉ dùng để {@link SecurityConfig} mở public khi Swagger đang bật
 * ({@link DevOnlyEndpoints}). Tách khỏi {@link SecurityConfig} để test prod dùng chính cùng một nguồn.
 */
public final class SwaggerRoutes {

    public static final String[] PATHS = {
            "/v3/api-docs/**",
            "/v3/api-docs",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    private SwaggerRoutes() {
    }
}
