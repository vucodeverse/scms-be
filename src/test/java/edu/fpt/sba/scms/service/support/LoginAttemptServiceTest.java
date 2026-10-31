package edu.fpt.sba.scms.service.support;

import edu.fpt.sba.scms.common.exception.AccountLockedException;
import edu.fpt.sba.scms.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginAttemptServiceTest {

    private static final Instant T0 = Instant.parse("2026-10-30T08:00:00Z");

    /** Đồng hồ điều khiển được để test khóa 15 phút. */
    static final class MutableClock extends Clock {
        private Instant now = T0;

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }
    }

    private static AppProperties props() {
        return new AppProperties(
                new AppProperties.Jwt(30, 7, "a".repeat(40), "b".repeat(40), "scms"),
                new AppProperties.Security(new AppProperties.Security.Login(5, 15, 15),
                        new AppProperties.Security.Cors(new String[]{"http://localhost"})),
                new AppProperties.Auth(new AppProperties.Auth.Cookie(false, "rt", "/api/v1/auth", "Lax"),
                        new AppProperties.Auth.Seed(false, "coo", "x")),
                "./uploads", "", new AppProperties.SePay("sandbox", ""));
    }

    @Test
    void locksAccountOnFifthFailureWithinWindow() {
        MutableClock clock = new MutableClock();
        LoginAttemptService service = new LoginAttemptService(props(), clock);

        assertThatCode(() -> {
            for (int i = 0; i < 4; i++) {
                service.onFailure("coo");
            }
        }).doesNotThrowAnyException();

        assertThatThrownBy(() -> service.onFailure("coo"))
                .isInstanceOf(AccountLockedException.class)
                .extracting("retryAfterSeconds").isEqualTo(900L);

        // Trong thời gian khóa vẫn bị chặn, kể cả tên đăng nhập khác hoa/thường.
        assertThatThrownBy(() -> service.checkNotLocked("COO"))
                .isInstanceOf(AccountLockedException.class);
    }

    @Test
    void unlockAfterLockDuration() {
        MutableClock clock = new MutableClock();
        LoginAttemptService service = new LoginAttemptService(props(), clock);
        for (int i = 0; i < 4; i++) {
            service.onFailure("coo");
        }
        assertThatThrownBy(() -> service.onFailure("coo")).isInstanceOf(AccountLockedException.class);

        clock.advance(Duration.ofMinutes(15).plusSeconds(1));
        assertThatCode(() -> service.checkNotLocked("coo")).doesNotThrowAnyException();
    }

    @Test
    void failureWindowResetsAfter15Minutes() {
        MutableClock clock = new MutableClock();
        LoginAttemptService service = new LoginAttemptService(props(), clock);

        for (int i = 0; i < 4; i++) {
            service.onFailure("coo");
        }
        clock.advance(Duration.ofMinutes(16));

        assertThatCode(() -> {
            for (int i = 0; i < 4; i++) {
                service.onFailure("coo");
            }
        }).doesNotThrowAnyException();
    }

    @Test
    void successResetsCounter() {
        MutableClock clock = new MutableClock();
        LoginAttemptService service = new LoginAttemptService(props(), clock);

        for (int i = 0; i < 4; i++) {
            service.onFailure("coo");
        }
        service.onSuccess("coo");

        assertThatCode(() -> {
            for (int i = 0; i < 4; i++) {
                service.onFailure("coo");
            }
        }).doesNotThrowAnyException();
    }
}
