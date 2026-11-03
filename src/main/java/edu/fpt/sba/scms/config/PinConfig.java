package edu.fpt.sba.scms.config;

import edu.fpt.sba.scms.config.properties.AppProperties;
import edu.fpt.sba.scms.service.support.PinHasher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;

/**
 * Bean sinh hash PIN kiosk (IMPLEMENTATION.md §7.2). Đặt ở {@code config} vì cần đọc cấu hình
 * ({@code app.kiosk.pin-pepper}); {@link PinHasher} ở {@code service/support} chỉ giữ thuật toán.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class PinConfig {

    /**
     * Pepper bắt buộc khác rỗng và ≥ 16 ký tự: thiếu pepper thì {@code SHA-256(branchId:pin:)} vẫn tính được
     * nhưng lộ cấu trúc PIN 4 số cho mỗi branch, nên chặn ngay lúc khởi động thay vì im lặng sinh hash yếu.
     */
    @Bean
    public PinHasher pinHasher(PasswordEncoder passwordEncoder, AppProperties.Kiosk kiosk) {
        String pepper = kiosk == null ? null : kiosk.pinPepper();
        if (!StringUtils.hasText(pepper) || pepper.length() < 16) {
            throw new IllegalStateException("Thiếu app.kiosk.pin-pepper (KIOSK_PIN_PEPPER) hoặc ngắn hơn 16 ký tự "
                    + "— không thể sinh PIN hash an toàn.");
        }
        return new PinHasher(passwordEncoder, pepper);
    }
}
