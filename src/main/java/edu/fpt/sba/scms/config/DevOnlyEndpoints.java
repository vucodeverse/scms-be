package edu.fpt.sba.scms.config;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Cờ "môi trường này có cho phép endpoint chỉ dành cho dev hay không" — dev/test: có, prod: không.
 * <p>
 * {@link SecurityConfig} dùng cờ này để quyết định có {@code permitAll} các route Swagger hay không,
 * <b>và</b> để chặn hẳn (401 ngay cả khi biết URL) ở môi trường thật. Nhờ vậy, dù lỡ đặt
 * {@code OPENAPI_ENABLED=true} trên prod, {@code /v3/api-docs} vẫn không lộ.
 */
@Component
@Profile({"dev", "test"})
public class DevOnlyEndpoints {

    /** True khi profile hiện tại có nạp lớp này (dev, test). */
    public boolean enabled() {
        return true;
    }
}
