package edu.fpt.sba.scms.service.support;

import edu.fpt.sba.scms.common.exception.AccountLockedException;
import edu.fpt.sba.scms.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Khóa tài khoản trong bộ nhớ (IMPLEMENTATION.md §10.1): 5 lần sai liên tiếp trong 15 phút -> khóa 15 phút.
 * Đăng nhập thành công -> reset. Không lưu DB.
 * <p>
 * Lưu ý vận hành: in-memory chỉ đúng với 1 instance; khi scale ngang thay bằng store chung (hook sẵn interface hành vi).
 */
@Slf4j
@Service
public class LoginAttemptService {

    private record Attempt(int count, Instant firstAt, Instant lockedUntil) {
    }

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();
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
        Attempt attempt = attempts.compute(key, (k, current) -> {
            boolean expired = current == null
                    || (current.lockedUntil() == null && Duration.between(current.firstAt(), now).compareTo(window) > 0)
                    || (current.lockedUntil() != null && !current.lockedUntil().isAfter(now));
            if (expired) {
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

    /** Xoá các bản ghi hết hạn để map không phình mãi. */
    void evictExpired() {
        Instant now = clock.instant();
        attempts.entrySet().removeIf(e -> {
            Attempt a = e.getValue();
            if (a.lockedUntil() != null) {
                return !a.lockedUntil().isAfter(now);
            }
            return Duration.between(a.firstAt(), now).compareTo(window) > 0;
        });
    }

    private static String key(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    static Instant truncateToMillis(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MILLIS);
    }
}
