package edu.fpt.sba.scms.config;

import edu.fpt.sba.scms.config.properties.AppProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Fail-fast khi chạy profile {@code prod} mà cấu hình vẫn ở chế độ dev.
 * <p>
 * Bật ở mọi profile nhưng chỉ có hiệu lực khi {@code prod} là profile đang chạy. Mục đích: quên
 * {@code SPRING_PROFILES_ACTIVE=prod} hoặc quên thay ENV thì app <b>không khởi động</b>, thay vì lặng lẽ
 * chạy với secret mặc định / seed tài khoản mẫu / webhook không verify.
 * <p>
 * Mỗi điều kiện là một hàm nhỏ trả về <i>mô tả lỗi</i> hoặc {@code null} — thêm điều kiện mới chỉ cần thêm
 * một dòng vào {@link #problems()}, không phải sửa chuỗi if dài.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProdConfigGuard {

    private static final String PROD = "prod";

    /** MySQL: chỉ các chế độ xác thực chứng chỉ mới đủ an toàn cho credential DB. */
    private static final Set<String> SECURE_SSL_MODES = Set.of("REQUIRED", "VERIFY_CA", "VERIFY_IDENTITY");

    private final Environment environment;
    private final AppProperties appProperties;

    @PostConstruct
    void check() {
        if (!environment.matchesProfiles(PROD)) {
            return;
        }
        List<String> problems = problems();
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Cấu hình prod chưa hợp lệ:\n - " + String.join("\n - ", problems));
        }
        log.info("Prod config guard: OK");
    }

    /** Mọi vi phạm hiện có; rỗng khi cấu hình hợp lệ. */
    List<String> problems() {
        List<String> problems = new ArrayList<>();
        addIfPresent(problems, devJwtSecretProblem());
        addIfPresent(problems, webhookSecretProblem());
        addIfPresent(problems, cookieSecureProblem());
        addIfPresent(problems, seedProblem());
        addIfPresent(problems, dbTlsProblem());
        addIfPresent(problems, trustForwardedForProblem());
        addIfPresent(problems, kioskKeyProblem());
        return problems;
    }

    private String devJwtSecretProblem() {
        AppProperties.Jwt jwt = appProperties.jwt();
        if (jwt.isDevSecret(jwt.accessSecret()) || jwt.isDevSecret(jwt.refreshSecret())) {
            return "JWT secret không được để dev-only — đặt JWT_ACCESS_SECRET / JWT_REFRESH_SECRET.";
        }
        // File base không có default -> thiếu ENV thì đã bị @NotBlank chặn; nhưng base config cũ vẫn có thể
        // để trống qua biến môi trường rỗng. Chặn cả trường hợp đó ở đây.
        return StringUtils.hasText(jwt.accessSecret()) && StringUtils.hasText(jwt.refreshSecret())
                ? null : "JWT_ACCESS_SECRET / JWT_REFRESH_SECRET đang rỗng.";
    }

    private String webhookSecretProblem() {
        if (appProperties.sepay() != null && !StringUtils.hasText(appProperties.sepay().webhookSecret())) {
            return "SEPAY_WEBHOOK_SECRET rỗng — webhook SePay sẽ không verify được chữ ký.";
        }
        return null;
    }

    private String cookieSecureProblem() {
        AppProperties.Auth.Cookie cookie = appProperties.auth().cookie();
        if (cookie.refreshEnabled() && !cookie.secure()) {
            return "app.auth.cookie.secure=false ở prod — cookie refresh phải Secure.";
        }
        return null;
    }

    private String seedProblem() {
        if (appProperties.auth().seed().enabled()) {
            return "app.auth.seed.enabled=true ở prod — tài khoản seed không được bật trên prod.";
        }
        return null;
    }

    private String dbTlsProblem() {
        String url = environment.getProperty("spring.datasource.url", "");
        if (!url.startsWith("jdbc:mysql:")) {
            return null; // H2/test hoặc dialect khác: không phải mối lo của guard này
        }
        String mode = sslModeOf(url);
        if (mode.isEmpty() || !SECURE_SSL_MODES.contains(mode)) {
            return "Kết nối MySQL phải dùng TLS: đặt DB_SSL_MODE=VERIFY_IDENTITY (nhận: " + mode + ").";
        }
        return null;
    }

    /**
     * Đọc {@code sslMode=...} từ JDBC URL; nếu không có thì suy ra từ {@code useSSL=false/true}
     * (cấu hình kiểu cũ) để không bị lách bằng cách bỏ hẳn tham số.
     */
    private static String sslModeOf(String url) {
        for (String param : url.substring(url.indexOf('?') + 1).split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            if ("sslMode".equalsIgnoreCase(kv[0])) {
                return kv[1].trim().toUpperCase(Locale.ROOT);
            }
            if ("useSSL".equalsIgnoreCase(kv[0]) && "false".equalsIgnoreCase(kv[1].trim())) {
                return "DISABLED";
            }
        }
        return "";
    }

    private String trustForwardedForProblem() {
        if (appProperties.security().refresh().trustForwardedFor()) {
            return "TRUST_FORWARDED_FOR=true — chỉ bật khi chắc chắn app nằm sau reverse proxy đã ghi đè X-Forwarded-For.";
        }
        return null;
    }

    private String kioskKeyProblem() {
        if (!StringUtils.hasText(appProperties.kioskApiKey())) {
            return "KIOSK_API_KEY rỗng — route kiosk sẽ bị chặn; đặt key hoặc tắt route kiosk.";
        }
        return null;
    }

    private static void addIfPresent(List<String> problems, String problem) {
        if (problem != null) {
            problems.add(problem);
        }
    }
}
