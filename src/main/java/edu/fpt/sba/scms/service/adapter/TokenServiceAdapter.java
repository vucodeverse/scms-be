package edu.fpt.sba.scms.service.adapter;

import edu.fpt.sba.scms.service.port.TokenService;
import edu.fpt.sba.scms.model.AuthToken;
import edu.fpt.sba.scms.security.jwt.JwtTokenProvider;
import edu.fpt.sba.scms.service.support.RefreshTokenStore;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/** Adapter nối {@link JwtTokenProvider} + {@link RefreshTokenStore} cho cổng {@link TokenService}. */
@Component
@RequiredArgsConstructor
public class TokenServiceAdapter implements TokenService {

    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenStore refreshTokenStore;

    @Override
    public IssuedToken issueAccessToken(Integer userId, String username, String fullName, String role,
                                        Integer branchId) {
        return convert(tokenProvider.issueAccessToken(userId, username, fullName, role, branchId));
    }

    @Override
    public IssuedToken issueRefreshToken(Integer userId, String username, String fullName, String role,
                                         Integer branchId, Duration ttl) {
        return convert(tokenProvider.issueRefreshToken(userId, username, fullName, role, branchId, ttl));
    }

    @Override
    public long accessTtlSeconds() {
        return tokenProvider.accessTtlSeconds();
    }

    @Override
    public Duration refreshTtl() {
        return tokenProvider.refreshTtl();
    }

    @Override
    public Duration sessionRefreshTtl() {
        return tokenProvider.sessionRefreshTtl();
    }

    @Override
    public Duration remainingTtl(String rawRefreshToken) {
        try {
            Instant exp = tokenProvider.parseRefresh(rawRefreshToken).getExpiration().toInstant();
            Duration left = Duration.between(Instant.now(), exp);
            return left.isNegative() || left.isZero() ? sessionRefreshTtl() : left;
        } catch (RuntimeException e) {
            return sessionRefreshTtl();
        }
    }

    @Override
    public boolean isRefreshToken(String rawRefreshToken) {
        tokenProvider.parseRefresh(rawRefreshToken);
        return true;
    }

    @Override
    public Integer findUsableRefreshToken(String rawRefreshToken) {
        AuthToken token = refreshTokenStore.findUsable(rawRefreshToken);
        return token == null ? null : token.getUserId();
    }

    @Override
    public void saveRefreshToken(Integer userId, IssuedToken token) {
        refreshTokenStore.saveRefreshToken(userId, token.token(), token.jti(), token.expiresAt());
    }

    @Override
    public void revokeRefreshToken(String rawRefreshToken) {
        AuthToken token = refreshTokenStore.findUsable(rawRefreshToken);
        if (token != null) {
            // Thu hồi nguyên tử: hai request revoke đồng thời chỉ một bên thành công — bên thua bỏ qua.
            refreshTokenStore.revokeIfActive(token);
        }
    }

    @Override
    public void revokeAllRefreshTokens(Integer userId) {
        refreshTokenStore.revokeAllOfUser(userId);
    }

    @Override
    public void revokeAccessJti(Integer userId, String jti, Instant accessExpiresAt) {
        refreshTokenStore.revokeAccessJti(userId, jti, accessExpiresAt);
    }

    @Override
    public boolean isRevokedRefreshToken(String rawRefreshToken) {
        AuthToken token = refreshTokenStore.findAny(rawRefreshToken);
        return token != null && token.getRevokedAt() != null;
    }

    @Override
    public Integer refreshTokenOwner(String rawRefreshToken) {
        Claims claims = tokenProvider.tryParseRefresh(rawRefreshToken);
        return claims == null ? null : JwtTokenProvider.userId(claims);
    }

    private static IssuedToken convert(JwtTokenProvider.IssuedToken token) {
        return new IssuedToken(token.token(), token.jti(), token.expiresAt());
    }
}
