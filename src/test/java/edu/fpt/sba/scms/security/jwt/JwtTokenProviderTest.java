package edu.fpt.sba.scms.security.jwt;

import edu.fpt.sba.scms.common.enums.ErrorCode;
import edu.fpt.sba.scms.common.exception.UnauthorizedException;
import edu.fpt.sba.scms.config.AppProperties;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private static final String ACCESS_SECRET = "test-access-secret-0123456789-abcdefghij";
    private static final String REFRESH_SECRET = "test-refresh-secret-0123456789-abcdefghij";

    private static JwtTokenProvider provider(String accessSecret) {
        return new JwtTokenProvider(new AppProperties(
                new AppProperties.Jwt(30, 7, accessSecret, REFRESH_SECRET, "scms"),
                new AppProperties.Security(new AppProperties.Security.Login(5, 15, 15),
                        new AppProperties.Security.Cors(new String[]{"http://localhost"})),
                new AppProperties.Auth(new AppProperties.Auth.Cookie(false, "rt", "/api/v1/auth", "Lax"),
                        new AppProperties.Auth.Seed(false, "coo", "x")),
                "./uploads", "", new AppProperties.SePay("sandbox", "")));
    }

    @Test
    void accessTokenCarriesRoleBranchAndJti() {
        JwtTokenProvider provider = provider(ACCESS_SECRET);
        var issued = provider.issueAccessToken(7, "bm01", "Nguyễn Văn A", "BRANCH_MANAGER", 3);

        Claims claims = provider.parseAccess(issued.token());

        assertThat(claims.getSubject()).isEqualTo("bm01");
        assertThat(JwtTokenProvider.userId(claims)).isEqualTo(7);
        assertThat(JwtTokenProvider.branchId(claims)).isEqualTo(3);
        assertThat(claims.get(JwtTokenProvider.CLAIM_ROLE_KEY, String.class)).isEqualTo("BRANCH_MANAGER");
        assertThat(claims.get(JwtTokenProvider.CLAIM_NAME_KEY, String.class)).isEqualTo("Nguyễn Văn A");
        assertThat(claims.getId()).isEqualTo(issued.jti());
        assertThat(JwtTokenProvider.isType(claims, JwtTokenProvider.TYPE_ACCESS)).isTrue();
        // Refresh TTL không ảnh hưởng access TTL
        assertThat(provider.accessTtlSeconds()).isEqualTo(1800);
    }

    @Test
    void cooTokenHasNoBranchClaim() {
        Claims claims = provider(ACCESS_SECRET)
                .parseAccess(provider(ACCESS_SECRET).issueAccessToken(1, "coo", "COO", "COO", null).token());
        assertThat(JwtTokenProvider.branchId(claims)).isNull();
    }

    @Test
    void refreshTokenIsSignedWithSeparateKey() {
        JwtTokenProvider provider = provider(ACCESS_SECRET);
        var refresh = provider.issueRefreshToken(7, "bm01", "A", "BRANCH_MANAGER", 3);

        assertThatThrownBy(() -> provider.parseAccess(refresh.token()))
                .isInstanceOf(UnauthorizedException.class)
                .extracting(e -> ((UnauthorizedException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_TOKEN_INVALID);
        assertThat(JwtTokenProvider.isType(provider.parseRefresh(refresh.token()), JwtTokenProvider.TYPE_REFRESH))
                .isTrue();
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtTokenProvider provider = provider(ACCESS_SECRET);
        String token = provider.issueAccessToken(7, "bm01", "A", "BRANCH_MANAGER", 3).token();
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThatThrownBy(() -> provider.parseAccess(tampered))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void tokenSignedByOtherSecretIsRejected() {
        JwtTokenProvider shortLived = new JwtTokenProvider(new AppProperties(
                // TTL tính theo phút -> dùng sleep ngắn không đủ; kiểm bằng token của provider khác secret
                new AppProperties.Jwt(30, 7, "another-access-secret-0123456789-abcdefgh", REFRESH_SECRET, "scms"),
                new AppProperties.Security(new AppProperties.Security.Login(5, 15, 15),
                        new AppProperties.Security.Cors(new String[]{"http://localhost"})),
                new AppProperties.Auth(new AppProperties.Auth.Cookie(false, "rt", "/api/v1/auth", "Lax"),
                        new AppProperties.Auth.Seed(false, "coo", "x")),
                "./uploads", "", new AppProperties.SePay("sandbox", "")));
        String token = shortLived.issueAccessToken(1, "coo", "C", "COO", null).token();

        assertThatThrownBy(() -> provider(ACCESS_SECRET).parseAccess(token))
                .isInstanceOf(UnauthorizedException.class)
                .extracting(e -> ((UnauthorizedException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_TOKEN_INVALID);
    }

    @Test
    void rejectsSecretShorterThan32Chars() {
        assertThatThrownBy(() -> provider("too-short"))
                .isInstanceOf(IllegalStateException.class);
    }
}
