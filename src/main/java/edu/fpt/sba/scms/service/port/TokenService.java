package edu.fpt.sba.scms.service.port;

import java.time.Duration;
import java.time.Instant;

/**
 * Cổng (port) phát/thu hồi token cho tầng service — service không phụ thuộc {@code security.jwt}.
 * Adapter hiện tại: {@code service.support.TokenServiceAdapter} (nối JwtTokenProvider + RefreshTokenStore).
 */
public interface TokenService {

    record IssuedToken(String token, String jti, Instant expiresAt) {
    }

    IssuedToken issueAccessToken(Integer userId, String username, String fullName, String role, Integer branchId);

    /**
     * Phát refresh token với hạn {@code ttl}: login dùng hạn rememberMe/session,
     * refresh dùng phần đời còn lại của token cũ ({@link #remainingTtl(String)}).
     */
    IssuedToken issueRefreshToken(Integer userId, String username, String fullName, String role, Integer branchId,
                                  Duration ttl);

    long accessTtlSeconds();

    /** Hạn dài nhất của refresh token (rememberMe). */
    Duration refreshTtl();

    /** Hạn "phiên" của refresh token khi không rememberMe. */
    Duration sessionRefreshTtl();

    /** Thời gian còn lại tới khi token hết hạn; không đọc được / đã hết hạn -> hạn session. */
    Duration remainingTtl(String rawRefreshToken);

    /** True nếu chuỗi là refresh token hợp lệ, đúng chữ ký và chưa hết hạn (sai -> 401). */
    boolean isRefreshToken(String rawRefreshToken);

    /** User id sở hữu refresh token còn hạn và chưa bị thu hồi; {@code null} nếu không hợp lệ. */
    Integer findUsableRefreshToken(String rawRefreshToken);

    void saveRefreshToken(Integer userId, IssuedToken token);

    void revokeRefreshToken(String rawRefreshToken);

    void revokeAllRefreshTokens(Integer userId);

    void revokeAccessJti(Integer userId, String jti, Instant accessExpiresAt);

    /** True nếu refresh token (đúng chữ ký, đúng loại) đã được cấp và đã bị thu hồi — dấu hiệu dùng lại token. */
    boolean isRevokedRefreshToken(String rawRefreshToken);

    /**
     * Chủ của refresh token (đọc payload nếu chữ ký hợp lệ); {@code null} nếu không đọc được.
     * Chỉ để log / làm key rate-limit, KHÔNG dùng để cấp quyền.
     */
    Integer refreshTokenOwner(String rawRefreshToken);
}
