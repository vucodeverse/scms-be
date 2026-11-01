package edu.fpt.sba.scms.service.support;

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
 * Chặn "refresh loop" / spam {@code /auth/refresh}: FE gặp 401 rồi gọi refresh liên tục (interceptor cấu hình sai)
 * sẽ tạo ra hàng loạt request và cấp access token mới liên tục.
 * <p>
 * Khoá đếm: {@code user:<id>} khi đọc được chủ token, còn token rác/không đọc được thì đếm <b>theo IP client</b>
 * chứ không gom chung một khoá — gom chung nghĩa là một kẻ spam token rác sẽ khoá luôn đường refresh của mọi người.
 * <p>
 * Vượt {@code app.security.refresh.max-per-window} lần trong {@code window-min} phút -> 429 TOO_MANY_REQUESTS
 * (kèm {@code Retry-After}) tới hết {@code block-min} phút.
 * <p>
 * Lưu in-memory, đủ cho 1 instance (xem {@link EphemeralCounter}); map được dọn định kỳ để không OOM.
 */
@Slf4j
@Service
public class RefreshRateLimiter {

    private final FixedWindowRateLimiter limiter;
    private final int maxPerWindow;
    private final Duration window;
    private final Duration blockDuration;

    @Autowired
    public RefreshRateLimiter(AppProperties props) {
        this(props, Clock.systemUTC());
    }

    RefreshRateLimiter(AppProperties props, Clock clock) {
        AppProperties.Security.Refresh cfg = props.security().refresh();
        this.maxPerWindow = cfg.maxPerWindow();
        this.window = Duration.ofMinutes(cfg.windowMin());
        this.blockDuration = Duration.ofMinutes(cfg.blockMin());
        this.limiter = new FixedWindowRateLimiter(new EphemeralCounter<>(clock), maxPerWindow, window, clock);
    }

    /**
     * Khoá đếm cho một lần refresh: theo user nếu đọc được chủ token, ngược lại theo IP.
     *
     * @param ownerUserId chủ refresh token (null khi không đọc/không tin được token)
     * @param clientIp    IP đã chuẩn hoá ({@link edu.fpt.sba.scms.common.util.ClientIp})
     */
    public static String key(Integer ownerUserId, String clientIp) {
        if (ownerUserId != null) {
            return "user:" + ownerUserId;
        }
        String ip = clientIp == null || clientIp.isBlank() ? "unknown" : clientIp;
        return "ip:" + ip.toLowerCase(Locale.ROOT);
    }

    /**
     * Ghi nhận 1 lần refresh; ném 429 nếu vượt ngưỡng (bị chặn tới hết cửa sổ, không gia hạn thêm).
     * Không bao giờ nhận token làm khoá.
     */
    public void onAttempt(String key) {
        long retryAfter = limiter.tryAcquire(key);
        if (retryAfter == 0) {
            return;
        }
        log.warn("Refresh bị chặn tạm {} giây: '{}' gọi quá {} lần/{} phút. Có thể FE đang refresh loop.",
                retryAfter, key, maxPerWindow, window.toMinutes());
        throw new RateLimitExceededException(retryAfter, blockDuration.toSeconds());
    }

    /** Dọn cửa sổ hết hạn — lưới an toàn song song với việc tự quét theo {@link EphemeralCounter#HARD_LIMIT}. */
    @Scheduled(fixedDelayString = "PT10M", initialDelayString = "PT1M")
    public void evictExpired() {
        int removed = limiter.evictExpired();
        if (removed > 0) {
            log.debug("Dọn {} cửa sổ rate-limit hết hạn (còn {})", removed, limiter.size());
        }
    }

    /** Chỉ dùng cho test/observability. */
    int size() {
        return limiter.size();
    }
}
