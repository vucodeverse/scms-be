package edu.fpt.sba.scms.config.properties;

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
         * Pepper cho {@code pin_lookup_hash = SHA-256(branchId:pin:pepper)} — IMPLEMENTATION.md §7.2.
         * Bean thuộc tính con ({@code @ConfigurationProperties}), khác các record lồng trên (constructor binding):
         * {@code app.kiosk} nằm ở nhánh khác của cây yml nên bind riêng.
         * <p>
         * Bắt buộc ≥ 16 ký tự; {@code PinConfig} fail-fast khi khởi động nếu thiếu.
         */
        @ConfigurationProperties(prefix = "app.kiosk")
        public record Kiosk(@NotBlank @Size(min = 16) String pinPepper,
                            @Min(1) Integer pinMaxFailures,
                            @Min(1) Integer pinFailureWindowMin,
                            @Min(1) Integer pinLockMin) {

            public Kiosk {
                pinMaxFailures = pinMaxFailures == null ? 3 : pinMaxFailures;
                pinFailureWindowMin = pinFailureWindowMin == null ? 15 : pinFailureWindowMin;
                pinLockMin = pinLockMin == null ? 15 : pinLockMin;
            }

            @Override
            public String toString() {
                return "Kiosk[pinPepper=******]";
            }
        }

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
            @NotBlank String issuer) {

        public boolean isDevSecret(String secret) {
            return StringUtils.startsWithIgnoreCase(secret, "dev-only-");
        }
    }

    public record Security(@Valid Login login, @Valid LoginIp loginIp, @Valid Refresh refresh, @Valid Cors cors) {

        /**
         * Rate-limit theo IP cho {@code /auth/login} — bù cho khoá theo username: chặn DoS "hash BCrypt" bằng cách
         * thử hàng nghìn username khác nhau và chặn việc khoá tài khoản người khác.
         */
        public record LoginIp(@Min(1) int maxPerWindow,
                              @Min(1) int windowMin,
                              @Min(1) int blockMin) {
        }

        public record Login(@Min(1) int loginMaxFailures,
                            @Min(1) int loginFailureWindowMin,
                            @Min(1) int loginLockMin) {
        }

        /** Chặn "refresh loop": quá {@code maxPerWindow} lần gọi refresh trong {@code windowMin} phút -> chặn {@code blockMin} phút. */
        public record Refresh(@Min(1) int maxPerWindow,
                              @Min(1) int windowMin,
                              @Min(1) int blockMin,
                              // true khi chạy sau reverse proxy đã ghi đè X-Forwarded-For; mặc định false vì header client tự bịa được.
                              boolean trustForwardedFor) {
        }

        public record Cors(@NotEmpty String[] allowedOrigins) {
        }
    }

    public record Auth(@Valid Cookie cookie, @Valid Seed seed) {

        /**
         * Bật khi FE dùng cookie httpOnly cho refresh token (mặc định tắt — §10.1: refresh trả trong body).
         *
         * @param secure gắn cờ Secure cho cookie — bật ở prod (HTTPS), có thể tắt ở dev (HTTP localhost).
         *               {@link edu.fpt.sba.scms.security.web.RefreshCookieSupport} từ chối khởi động nếu
         *               {@code sameSite=None} mà {@code secure=false}.
         */
        public record Cookie(boolean refreshEnabled, String refreshName, String refreshPath, String sameSite,
                             boolean secure) {
        }

        /** Chỉ dev: tạo role + COO mặc định nếu DB trống. */
        public record Seed(boolean enabled, String cooUsername, String cooPassword) {
        }
    }

    public record SePay(String mode, String webhookSecret) {

        /** Ở prod bắt buộc có secret để verify chữ ký webhook — kiểm bởi {@code ProdConfigGuard}. */
        @Override
        public String toString() {
            return "SePay[mode=" + mode + ", webhookSecret=" + (webhookSecret == null || webhookSecret.isBlank()
                    ? "" : "******") + "]";
        }
    }
}
