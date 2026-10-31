package edu.fpt.sba.scms.service.support;

import edu.fpt.sba.scms.common.exception.AccountLockedException;
import edu.fpt.sba.scms.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chặn "refresh loop" phía client: FE gặp 401 rồi gọi {@code /auth/refresh} liên tục (interceptor cấu hình sai)
 * sẽ tạo ra hàng loạt request và cấp access token mới liên tục.
 * <p>
 * Đếm theo <b>key người dùng</b>: mỗi lần gọi refresh bất kể thành công hay lỗi đều tính; vượt quá
 * {@code app.security.refresh.max-per-window} trong {@code refresh.window-min} phút thì ném
 * {@link AccountLockedException} (kèm header {@code Retry-After}) trong khoảng thời gian khoá.
 * <p>
 * Tương tự {@link LoginAttemptService}: lưu in-memory, đủ cho 1 instance; khi scale ngang thì thay bằng store chung.
 */
@Slf4j
@Service
public class RefreshRateLimiter {

    private record Window(int count, Instant startedAt) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final int maxPerWindow;
    private final Duration window;
    private final Duration blockDuration;
    private final Clock clock;

    @Autowired
    public RefreshRateLimiter(AppProperties props) {
        this(props, Clock.systemUTC());
    }

    RefreshRateLimiter(AppProperties props, Clock clock) {
        AppProperties.Security.Refresh cfg = props.security().refresh();
        this.maxPerWindow = cfg.maxPerWindow();
        this.window = Duration.ofMinutes(cfg.windowMin());
        this.blockDuration = Duration.ofMinutes(cfg.blockMin());
        this.clock = clock;
    }

    /**
     * Ghi nhận 1 lần refresh; ném ACCOUNT_LOCKED (chỉ mang nghĩa "tạm chặn") nếu vượt ngưỡng.
     * Key nên là username hoặc userId — không bao giờ là token.
     */
    public void onAttempt(String key) {
        String normalized = key == null || key.isBlank() ? "anonymous" : key.trim().toLowerCase(Locale.ROOT);
        Instant now = clock.instant();
        Window current = windows.compute(normalized, (k, existing) -> {
            if (existing == null || Duration.between(existing.startedAt(), now).compareTo(window) > 0) {
                return new Window(1, now);
            }
            return new Window(existing.count() + 1, existing.startedAt());
        });
        if (current.count() > maxPerWindow) {
            log.warn("Refresh bị chặn tạm {} phút: '{}' gọi quá {} lần/{} phút. Có thể FE đang refresh loop.",
                    blockDuration.toMinutes(), normalized, maxPerWindow, window.toMinutes());
            throw new AccountLockedException(blockDuration.toSeconds());
        }
    }

    /** Xoá cửa sổ hết hạn để map không phình. */
    void evictExpired() {
        Instant now = clock.instant();
        windows.entrySet().removeIf(e -> Duration.between(e.getValue().startedAt(), now).compareTo(window) > 0);
    }
}
