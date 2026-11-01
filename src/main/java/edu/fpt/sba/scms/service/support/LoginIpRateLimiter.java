package edu.fpt.sba.scms.service.support;

import edu.fpt.sba.scms.common.enums.ErrorCode;
import edu.fpt.sba.scms.common.exception.RateLimitExceededException;
import edu.fpt.sba.scms.common.util.EphemeralCounter;
import edu.fpt.sba.scms.common.util.FixedWindowRateLimiter;
import edu.fpt.sba.scms.config.properties.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.Locale;

/**
 * Giới hạn số lần đăng nhập <b>theo IP</b> — lớp bảo vệ bổ sung cho {@link LoginAttemptService} (đếm theo username).
 * <p>
 * Cần cả hai vì khoá theo username có 2 lỗ hổng thực tế:
 * <ul>
 *   <li>DoS "hash BCrypt": mỗi lần thử sai tốn ~250ms CPU (strength 12); thử hàng nghìn username khác nhau
 *       sẽ làm cạn CPU mà không bị khoá username nào.</li>
 *   <li>Khoá oan: gửi 5 lần sai vào username của người khác là khoá được tài khoản họ.</li>
 * </ul>
 * Vượt {@code app.security.login-ip.max-per-window} lần trong {@code window-min} phút -> 429 TOO_MANY_REQUESTS
 * (kèm {@code Retry-After}) tới hết {@code block-min} phút.
 * <p>
 * In-memory, đúng cho 1 instance (xem {@link EphemeralCounter}); đổi sang Redis chỉ cần sửa class này,
 * caller không đổi.
 */
@Slf4j
@Service
public class LoginIpRateLimiter {

    private final FixedWindowRateLimiter limiter;
    private final int maxPerWindow;
    private final Duration window;
    private final Duration blockDuration;

    @Autowired
    public LoginIpRateLimiter(AppProperties props) {
        this(props, Clock.systemUTC());
    }

    LoginIpRateLimiter(AppProperties props, Clock clock) {
        AppProperties.Security.LoginIp cfg = props.security().loginIp();
        this.maxPerWindow = cfg.maxPerWindow();
        this.window = Duration.ofMinutes(cfg.windowMin());
        this.blockDuration = Duration.ofMinutes(cfg.blockMin());
        this.limiter = new FixedWindowRateLimiter(new EphemeralCounter<>(clock), maxPerWindow, window, clock);
    }

    /**
     * Ghi nhận 1 lần gọi login từ IP; ném 429 nếu vượt ngưỡng.
     *
     * @param clientIp IP đã chuẩn hoá ({@link edu.fpt.sba.scms.common.util.ClientIp})
     */
    public void onAttempt(String clientIp) {
        long retryAfter = limiter.tryAcquire(key(clientIp));
        if (retryAfter == 0) {
            return;
        }
        log.warn("Login từ IP '{}' bị chặn {} giây: quá {} lần/{} phút.", clientIp, retryAfter,
                maxPerWindow, window.toMinutes());
        throw new RateLimitExceededException(ErrorCode.TOO_MANY_REQUESTS, retryAfter, blockDuration.toSeconds());
    }

    static String key(String clientIp) {
        return "ip:" + (clientIp == null ? "" : clientIp.toLowerCase(Locale.ROOT));
    }

    /** Dọn cửa sổ hết hạn — lưới an toàn song song với việc tự quét theo {@link EphemeralCounter#HARD_LIMIT}. */
    @Scheduled(fixedDelayString = "PT10M", initialDelayString = "PT2M")
    public void evictExpired() {
        int removed = limiter.evictExpired();
        if (removed > 0) {
            log.debug("Dọn {} cửa sổ rate-limit IP hết hạn (còn {})", removed, limiter.size());
        }
    }

    /** Chỉ dùng cho test/observability. */
    int size() {
        return limiter.size();
    }
}
