package edu.fpt.sba.scms.common.util;

import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Rate-limit dạng "cửa sổ cố định": tối đa {@code maxPerWindow} lần trong {@code window}, vượt thì chặn tới
 * hết cửa sổ (không cộng dồn, không gia hạn) và ném {@code RateLimitExceededException} kèm {@code Retry-After}.
 * <p>
 * Dùng chung cho {@code /auth/login} (theo IP) và {@code /auth/refresh} (theo user hoặc IP) — hai chỗ có cùng
 * công thức nhưng khác nguồn cấu hình và khác khoá, nên tách ở đây để không nhân đôi logic.
 * <p>
 * In-memory, đúng cho 1 instance; khi scale ngang thay nguồn đếm bằng store chung (Redis) — giữ nguyên API.
 */
@Slf4j
public final class FixedWindowRateLimiter {

    /** Một cửa sổ đếm. {@code expiresAt} là lúc cửa sổ kết thúc (và cũng là lúc bị chặn tới). */
    public record Window(int count, Instant startedAt, Instant expiresAt) {
    }

    private final EphemeralCounter<Window> windows;
    private final int maxPerWindow;
    private final Duration window;
    private final Clock clock;

    public FixedWindowRateLimiter(EphemeralCounter<Window> windows, int maxPerWindow, Duration window, Clock clock) {
        this.windows = windows;
        this.maxPerWindow = maxPerWindow;
        this.window = window;
        this.clock = clock;
    }

    /**
     * Ghi nhận 1 lần gọi. Trả về số giây phải chờ nếu <b>đã vượt</b> ngưỡng (caller ném
     * {@code RateLimitExceededException} kèm header {@code Retry-After}); {@code 0} nếu được phép.
     * <p>
     * Không ném ở đây để đơn vị dùng vẫn là {@code common.util} (không phụ thuộc tầng exception).
     */
    public long tryAcquire(String key) {
        Instant now = clock.instant();
        Window current = windows.compute(normalize(key), Window::expiresAt, existing -> advance(existing, now));
        boolean blocked = current.count() > maxPerWindow;
        return blocked ? Math.max(1, Duration.between(now, current.expiresAt()).toSeconds()) : 0;
    }

    /**
     * Bước chuyển của cửa sổ: hết hạn -> mở cửa sổ mới đếm 1; đang bị chặn -> giữ nguyên (không nới thêm);
     * còn trong cửa sổ -> tăng 1 giữ nguyên mốc kết thúc.
     */
    private Window advance(Window existing, Instant now) {
        if (existing == null || !existing.expiresAt().isAfter(now)) {
            return new Window(1, now, now.plus(window));
        }
        if (existing.count() > maxPerWindow) {
            return existing;
        }
        return new Window(existing.count() + 1, existing.startedAt(), existing.expiresAt());
    }

    /** Xoá cửa sổ hết hạn; trả số bản ghi bị xoá (dùng cho job dọn định kỳ). */
    public int evictExpired() {
        return windows.purgeExpired(Window::expiresAt);
    }

    public int size() {
        return windows.size();
    }

    public int maxPerWindow() {
        return maxPerWindow;
    }

    public Duration window() {
        return window;
    }

    private static String normalize(String key) {
        return key == null || key.isBlank() ? "unknown" : key.trim();
    }
}
