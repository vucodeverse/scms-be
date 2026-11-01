package edu.fpt.sba.scms.security.util;

import edu.fpt.sba.scms.common.util.ClientIp;
import edu.fpt.sba.scms.config.properties.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Đọc IP client theo cấu hình {@code app.security.refresh.trust-forwarded-for}.
 * <p>
 * Không nằm ở package {@code common} vì cần đọc thuộc tính ứng dụng ({@code AppProperties}), mà tầng common
 * không được phụ thuộc config.
 */
@Component
@RequiredArgsConstructor
public class ClientIpResolver {

    private final AppProperties appProperties;

    public String of(HttpServletRequest request) {
        boolean trustForwarded = appProperties.security().refresh().trustForwardedFor();
        return ClientIp.fromHeader(request.getRemoteAddr(), request.getHeader("X-Forwarded-For"), trustForwarded);
    }
}
