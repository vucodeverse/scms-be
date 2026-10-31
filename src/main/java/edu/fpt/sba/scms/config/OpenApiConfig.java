package edu.fpt.sba.scms.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * OpenAPI / Swagger UI (IMPLEMENTATION.md §18.2).
 * Chỉ tạo khi {@code app.openapi.enabled=true} — dev bật, prod tắt. Đây chỉ là "sinh tài liệu";
 * việc route Swagger có public hay không do {@code SecurityConfig} + {@link DevOnlyEndpoints} quyết định
 * (thêm điều kiện profile) để lỡ bật trên prod cũng không lộ.
 * <ul>
 *   <li>JSON: {@code /v3/api-docs}</li>
 *   <li>UI: {@code /swagger-ui.html}</li>
 *   <li>Nút "Authorize" dùng Bearer access token; cột chung {@code X-Trace-Id} / {@code X-Branch-Id} khai báo ở đây.</li>
 * </ul>
 */
@Validated
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.openapi.enabled", havingValue = "true")
public class OpenApiConfig {

    public static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI scmsOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SCMS API")
                        .version("v1")
                        .description("""
                                API quản lý chuỗi quán cà phê (SCMS).

                                - Mọi response thành công bọc trong `ApiResponse<T>`: `{success, status, code, message, data, timestamp, traceId}`.
                                - Mọi lỗi bọc trong `ApiErrorResponse`: `{success, status, errorCode, detailCode?, message, path, timestamp, traceId, fieldErrors?}`.
                                - Mọi endpoint có tiền tố `/api/v1`. Phân trang bắt đầu từ `page=1`.
                                - Đăng nhập: `POST /api/v1/auth/login` → dán `accessToken` vào nút Authorize.
                                """)
                        .contact(new Contact().name("SCMS Team"))
                        .license(new License().name("Internal")))
                .servers(List.of(new Server().url("/").description("Current host")))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Access token trả từ POST /api/v1/auth/login"))
                        .addSecuritySchemes("traceId", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Trace-Id")
                                .description("Tùy chọn: UUID FE sinh cho mỗi request; server dùng để đối soát log"))
                        .addSecuritySchemes("branchId", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Branch-Id")
                                .description("Chỉ COO: chọn chi nhánh đang xem. BM/Cashier bỏ qua header này.")));
    }
}
