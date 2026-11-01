package edu.fpt.sba.scms.service.support;

import edu.fpt.sba.scms.common.exception.AccountLockedException;
import edu.fpt.sba.scms.common.util.EphemeralCounter;
import edu.fpt.sba.scms.config.properties.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/**
 * Khóa tài khoản trong bộ nhớ (IMPLEMENTATION.md §10.1): 5 lần sai liên tiếp trong 15 phút -> khóa 15 phút.
 * Đăng nhập thành công -> reset. Không lưu DB.
 * <p>
 * Lưu ý vận hành: in-memory chỉ đúng với 1 instance; khi scale ngang thay bằng store chung (hook sẵn interface hành vi).
 * Map được dọn định kỳ ({@link #evictExpired}) để không phình vô hạn khi có nhiều username khác nhau.
 */
@Slf4j
@Service
public class LoginAttemptService {

    /** {@code expiresAt}: lúc entry không còn ý nghĩa — hết khóa, hoặc hết cửa sổ đếm lần sai. */
    private record Attempt(int count, Instant firstAt, Instant lockedUntil) {

        Instant expiresAt() {
            return lockedUntil != null ? lockedUntil : firstAt;
        }
    }

    private final EphemeralCounter<Attempt> attempts;
    private final int maxFailures;
    private final Duration window;
    private final Duration lockDuration;
    private final Clock clock;

    @Autowired
    public LoginAttemptService(AppProperties props) {
        this(props, Clock.systemUTC());
    }

    LoginAttemptService(AppProperties props, Clock clock) {
        this.maxFailures = props.security().login().loginMaxFailures();
        this.window = Duration.ofMinutes(props.security().login().loginFailureWindowMin());
        this.lockDuration = Duration.ofMinutes(props.security().login().loginLockMin());
        this.clock = clock;
        this.attempts = new EphemeralCounter<>(clock);
    }

    /** Ném ACCOUNT_LOCKED nếu username đang bị khóa. */
    public void checkNotLocked(String username) {
        String key = key(username);
        Attempt attempt = attempts.get(key);
        if (attempt == null || attempt.lockedUntil() == null) {
            return;
        }
        Instant now = clock.instant();
        if (attempt.lockedUntil().isAfter(now)) {
            throw new AccountLockedException(Duration.between(now, attempt.lockedUntil()).toSeconds() + 1);
        }
        attempts.remove(key);
    }

    /** Ghi nhận 1 lần sai; ném ACCOUNT_LOCKED ngay ở lần sai thứ N. */
    public void onFailure(String username) {
        String key = key(username);
        Instant now = clock.instant();
        Attempt attempt = attempts.compute(key,
                this::entryExpiresAt,
                current -> {
                    if (current == null) {
                        return new Attempt(1, now, null);
                    }
                    int count = current.count() + 1;
                    Instant lockedUntil = count >= maxFailures ? now.plus(lockDuration) : null;
                    return new Attempt(count, current.firstAt(), lockedUntil);
                });
        if (attempt.lockedUntil() != null) {
            log.warn("Tài khoản '{}' bị khóa {} phút do sai quá {} lần.", key, lockDuration.toMinutes(), maxFailures);
            throw new AccountLockedException(lockDuration.toSeconds());
        }
    }

    public void onSuccess(String username) {
        attempts.remove(key(username));
    }

    /**
     * Mốc entry hết hạn để {@link EphemeralCounter} coi là rỗng: đang khóa -> lúc hết khóa;
     * đang đếm -> {@code firstAt + window} (vượt cửa sổ thì đếm lại từ đầu).
     */
    private Instant entryExpiresAt(Attempt attempt) {
        return attempt.lockedUntil() != null ? attempt.lockedUntil() : attempt.firstAt().plus(window);
    }

    /** Xoá các bản ghi hết hạn để map không phình mãi (chạy song song với quét theo ngưỡng của EphemeralCounter). */
    @Scheduled(fixedDelayString = "PT10M", initialDelayString = "PT1M")
    public void evictExpired() {
        int removed = attempts.purgeExpired(this::entryExpiresAt);
        if (removed > 0) {
            log.debug("Dọn {} bản ghi đăng nhập hết hạn (còn {})", removed, size());
        }
    }

    /** Chỉ dùng cho test/observability. */
    int size() {
        return attempts.size();
    }

    private static String key(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
