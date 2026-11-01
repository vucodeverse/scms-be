package edu.fpt.sba.scms.service.support;

import edu.fpt.sba.scms.common.exception.RateLimitExceededException;
import edu.fpt.sba.scms.config.properties.AppProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Rate-limit theo IP cho /auth/login: chặn sau N lần, tự mở lại sau khi hết cửa sổ. */
class LoginIpRateLimiterTest {

    private static LoginIpRateLimiter limiter(LoginAttemptServiceTest.MutableClock clock, int max) {
        AppProperties props = new AppProperties(
                new AppProperties.Jwt(30, 7, 24, "a".repeat(40), "b".repeat(40), "scms"),
                new AppProperties.Security(new AppProperties.Security.Login(5, 15, 15),
                        new AppProperties.Security.LoginIp(max, 5, 5),
                        new AppProperties.Security.Refresh(10, 5, 15, false),
                        new AppProperties.Security.Cors(new String[]{"http://localhost"})),
                new AppProperties.Auth(new AppProperties.Auth.Cookie(false, "rt", "/api/v1/auth", "Lax", false),
                        new AppProperties.Auth.Seed(false, "coo", "x")),
                new AppProperties.OpenApi(true),
                "./uploads", "", new AppProperties.SePay("sandbox", ""));
        return new LoginIpRateLimiter(props, clock);
    }

    @Test
    void blocksAfterMaxAttemptsFromSameIp() {
        LoginIpRateLimiter limiter = limiter(new LoginAttemptServiceTest.MutableClock(), 3);

        assertThatCode(() -> {
            limiter.onAttempt("203.0.113.7");
            limiter.onAttempt("203.0.113.7");
            limiter.onAttempt("203.0.113.7");
        }).doesNotThrowAnyException();

        assertThatThrownBy(() -> limiter.onAttempt("203.0.113.7"))
                .isInstanceOf(RateLimitExceededException.class)
                .extracting("retryAfterSeconds").isEqualTo(300L);
    }

    @Test
    void differentIpsCountSeparately() {
        LoginIpRateLimiter limiter = limiter(new LoginAttemptServiceTest.MutableClock(), 1);
        limiter.onAttempt("203.0.113.7");

        assertThatCode(() -> limiter.onAttempt("203.0.113.8")).doesNotThrowAnyException();
    }

    @Test
    void allowsAgainAfterWindowExpires() {
        LoginAttemptServiceTest.MutableClock clock = new LoginAttemptServiceTest.MutableClock();
        LoginIpRateLimiter limiter = limiter(clock, 1);
        limiter.onAttempt("203.0.113.7");
        assertThatThrownBy(() -> limiter.onAttempt("203.0.113.7")).isInstanceOf(RateLimitExceededException.class);

        clock.advance(Duration.ofMinutes(5).plusSeconds(1));
        assertThatCode(() -> limiter.onAttempt("203.0.113.7")).doesNotThrowAnyException();
    }
}
