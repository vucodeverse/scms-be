package edu.fpt.sba.scms.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * Cấu hình riêng của ứng dụng (prefix {@code app}). Secrets đọc từ ENV; profile prod không có default.
 */
// @Validated trên record là no-op: validation của @ConfigurationProperties áp lên JavaBean; với record,
// constructor-bound object bị chặn validate. Bật bằng @EnableConfigurationProperties(AppProperties.class)
// (class thường) ở JwtConfig.
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @Valid Jwt jwt,
        @Valid Security security,
        @Valid Auth auth,
        @Valid OpenApi openapi,
        String uploadDir,
        String kioskApiKey,
        SePay sepay) {

    /**
     * Cờ Swagger/OpenAPI. Là "mong muốn"; quyết định mở thật vẫn cần thêm điều kiện profile dev/test
     * ({@link edu.fpt.sba.scms.config.DevOnlyEndpoints}) — xem {@code SecurityConfig}.
     */
    public record OpenApi(boolean enabled) {
    }

    public record Jwt(
            @Min(1) long accessTtlMin,
            @Min(1) long refreshTtlDays,
            // Hạn refresh token khi không rememberMe (phiên ngắn).
            @Min(1) long sessionRefreshTtlHours,
            @NotBlank @Size(min = 32) String accessSecret,
            @NotBlank @Size(min = 32) String refreshSecret,
            String issuer) {

        public boolean isDevSecret(String secret) {
            return StringUtils.startsWithIgnoreCase(secret, "dev-only-");
        }
    }

    public record Security(@Valid Login login, @Valid Refresh refresh, @Valid Cors cors) {

        public record Login(@Min(1) int loginMaxFailures,
                            @Min(1) int loginFailureWindowMin,
                            @Min(1) int loginLockMin) {
        }

        /** Chặn "refresh loop": quá {@code maxPerWindow} lần gọi refresh trong {@code windowMin} phút -> chặn {@code blockMin} phút. */
        public record Refresh(@Min(1) int maxPerWindow,
                              @Min(1) int windowMin,
                              @Min(1) int blockMin) {
        }

        public record Cors(@NotEmpty String[] allowedOrigins) {
        }
    }

    public record Auth(@Valid Cookie cookie, @Valid Seed seed) {

        /**
         * Bật khi FE dùng cookie httpOnly cho refresh token (mặc định tắt — §10.1: refresh trả trong body).
         *
         * @param secure gắn cờ Secure cho cookie — bật ở prod (HTTPS), có thể tắt ở dev (HTTP localhost).
         */
        public record Cookie(boolean refreshEnabled, String refreshName, String refreshPath, String sameSite,
                             boolean secure) {
        }

        /** Chỉ dev: tạo role + COO mặc định nếu DB trống. */
        public record Seed(boolean enabled, String cooUsername, String cooPassword) {
        }
    }

    public record SePay(String mode, String webhookSecret) {
    }
}
