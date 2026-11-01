package edu.fpt.sba.scms.config;

import edu.fpt.sba.scms.config.properties.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Prod phải fail-fast nếu cấu hình vẫn ở chế độ dev. */
class ProdConfigGuardTest {

    private static final String DEV_SECRET = "dev-only-access-secret-change-me-32bytes-min-x";
    private static final String REAL_SECRET = "real-production-secret-0123456789abcdef";

    @Test
    void prodProfileBoQuaKhiCauHinhDung() {
        ProdConfigGuard guard = new ProdConfigGuard(sslEnv("prod"), props(REAL_SECRET, "whsec", true, false));
        assertThatCode(guard::check).doesNotThrowAnyException();
    }

    @Test
    void prodProfileChanDevSecret() {
        ProdConfigGuard guard = new ProdConfigGuard(env("prod"), props(DEV_SECRET, "whsec", true, false));
        assertThatThrownBy(guard::check).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dev-only");
    }

    @Test
    void prodProfileChanWebhookSecretRong() {
        ProdConfigGuard guard = new ProdConfigGuard(env("prod"), props(REAL_SECRET, "", true, false));
        assertThatThrownBy(guard::check).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SEPAY_WEBHOOK_SECRET");
    }

    @Test
    void prodProfileChanSeedBat() {
        ProdConfigGuard guard = new ProdConfigGuard(env("prod"), props(REAL_SECRET, "whsec", true, true));
        assertThatThrownBy(guard::check).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("seed");
    }

    @Test
    void devProfileKhongKiemTra() {
        ProdConfigGuard guard = new ProdConfigGuard(env("dev"), props(DEV_SECRET, "", true, true));
        assertThatCode(guard::check).doesNotThrowAnyException();
    }

    @Test
    void prodProfileChanDbKhongTls() {
        ProdConfigGuard guard = new ProdConfigGuard(
                env("prod").withProperty("spring.datasource.url",
                        MYSQL_URL + "?useSSL=false&allowPublicKeyRetrieval=true"),
                props(REAL_SECRET, "whsec", true, false));
        assertThatThrownBy(guard::check).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TLS");
    }

    @Test
    void prodProfileChanTrustForwardedFor() {
        ProdConfigGuard guard = new ProdConfigGuard(sslEnv("prod"),
                props(REAL_SECRET, "whsec", true, false, true));
        assertThatThrownBy(guard::check).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TRUST_FORWARDED_FOR");
    }

    @Test
    void prodProfileChanCookieKhongSecure() {
        ProdConfigGuard guard = new ProdConfigGuard(sslEnv("prod"), props(REAL_SECRET, "whsec", false, false));
        assertThatThrownBy(guard::check).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cookie refresh phải Secure");
    }

    @Test
    void prodProfileChanKioskKeyRong() {
        ProdConfigGuard guard = new ProdConfigGuard(sslEnv("prod"),
                props(REAL_SECRET, "whsec", true, false, false, ""));
        assertThatThrownBy(guard::check).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("KIOSK_API_KEY");
    }

    private static final String MYSQL_URL = "jdbc:mysql://db:3306/scms";

    /** Môi trường prod "đủ tốt" — TLS chuẩn để không che lấp lỗi của điều kiện khác. */
    private static MockEnvironment sslEnv(String... profiles) {
        return env(profiles).withProperty("spring.datasource.url", MYSQL_URL + "?sslMode=VERIFY_IDENTITY");
    }

    private static MockEnvironment env(String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return environment;
    }

    private static AppProperties props(String secret, String webhookSecret, boolean cookieSecure, boolean seedOn) {
        return props(secret, webhookSecret, cookieSecure, seedOn, false, "kiosk-key-prod");
    }

    private static AppProperties props(String secret, String webhookSecret, boolean cookieSecure, boolean seedOn,
                                       boolean trustForwardedFor) {
        return props(secret, webhookSecret, cookieSecure, seedOn, trustForwardedFor, "kiosk-key-prod");
    }

    private static AppProperties props(String secret, String webhookSecret, boolean cookieSecure, boolean seedOn,
                                       boolean trustForwardedFor, String kioskKey) {
        return new AppProperties(
                new AppProperties.Jwt(30, 7, 12, secret, secret + "-refresh-different-x", "scms"),
                new AppProperties.Security(
                        new AppProperties.Security.Login(5, 15, 15),
                        new AppProperties.Security.LoginIp(10, 5, 5),
                        new AppProperties.Security.Refresh(20, 5, 5, trustForwardedFor),
                        new AppProperties.Security.Cors(new String[]{"http://localhost:5173"})),
                new AppProperties.Auth(
                        new AppProperties.Auth.Cookie(true, "scms_rt", "/api/v1/auth", "Lax", cookieSecure),
                        new AppProperties.Auth.Seed(seedOn, "coo", "Admin@123")),
                new AppProperties.OpenApi(false),
                "./uploads",
                kioskKey,
                new AppProperties.SePay("sandbox", webhookSecret));
    }
}
