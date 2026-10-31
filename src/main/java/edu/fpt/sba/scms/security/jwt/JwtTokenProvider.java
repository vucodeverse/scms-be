package edu.fpt.sba.scms.security.jwt;

import edu.fpt.sba.scms.common.enums.ErrorCode;
import edu.fpt.sba.scms.common.exception.UnauthorizedException;
import edu.fpt.sba.scms.config.AppProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Sinh/giải mã JWT HS256 (jjwt 0.12). Access 30 phút, refresh 7 ngày (IMPLEMENTATION.md §2, §10.1).
 * <p>
 * Claims: {@code sub}=username, {@code uid}, {@code name}, {@code role}, {@code bid} (branchId, bỏ qua nếu null),
 * {@code typ} (ACCESS|REFRESH), {@code jti}.
 */
@Slf4j
@Component
public class JwtTokenProvider {

    public static final String TYPE_ACCESS = "ACCESS";
    public static final String TYPE_REFRESH = "REFRESH";
    public static final String CLAIM_ROLE_KEY = "role";
    public static final String CLAIM_NAME_KEY = "name";

    private static final String CLAIM_UID = "uid";
    private static final String CLAIM_BRANCH = "bid";
    private static final String CLAIM_TYPE = "typ";

    private final SecretKey accessKey;
    private final SecretKey refreshKey;
    private final Duration accessTtl;
    private final Duration refreshTtl;
    private final Duration sessionRefreshTtl;
    private final String issuer;

    public JwtTokenProvider(AppProperties props) {
        this.accessKey = keyFrom(props.jwt().accessSecret());
        this.refreshKey = keyFrom(props.jwt().refreshSecret());
        this.accessTtl = Duration.ofMinutes(props.jwt().accessTtlMin());
        this.refreshTtl = Duration.ofDays(props.jwt().refreshTtlDays());
        this.sessionRefreshTtl = Duration.ofHours(props.jwt().sessionRefreshTtlHours());
        if (sessionRefreshTtl.compareTo(refreshTtl) > 0) {
            throw new IllegalStateException(
                    "app.jwt.session-refresh-ttl-hours không được lớn hơn hạn rememberMe (refresh-ttl-days).");
        }
        this.issuer = props.jwt().issuer();

        if (props.jwt().isDevSecret(props.jwt().accessSecret())) {
            log.warn("JWT đang dùng secret mặc định của DEV — không dùng cho môi trường thật.");
        }
    }

    public record IssuedToken(String token, String jti, Instant expiresAt) {
    }

    public IssuedToken issueAccessToken(Integer userId, String username, String fullName, String role,
                                        Integer branchId) {
        return issue(accessKey, TYPE_ACCESS, username, userId, fullName, role, branchId, accessTtl);
    }

    /** Refresh token hạn rememberMe (dùng khi login rememberMe=true). */
    public IssuedToken issueRefreshToken(Integer userId, String username, String fullName, String role,
                                         Integer branchId) {
        return issueRefreshToken(userId, username, fullName, role, branchId, refreshTtl);
    }

    public IssuedToken issueRefreshToken(Integer userId, String username, String fullName, String role,
                                         Integer branchId, Duration ttl) {
        return issue(refreshKey, TYPE_REFRESH, username, userId, fullName, role, branchId, ttl);
    }

    public long accessTtlSeconds() {
        return accessTtl.toSeconds();
    }

    public Duration refreshTtl() {
        return refreshTtl;
    }

    public Duration sessionRefreshTtl() {
        return sessionRefreshTtl;
    }

    /**
     * Đọc payload refresh token <b>không ném exception</b> — chỉ dùng để biết ai đang gọi khi token đã
     * bị thu hồi/bị dùng lại (đối soát log). Không dùng kết quả này để cấp quyền.
     */
    public Claims tryParseRefresh(String token) {
        try {
            return parse(token, refreshKey);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public Claims parseAccess(String token) {
        return parse(token, accessKey);
    }

    public Claims parseRefresh(String token) {
        return parse(token, refreshKey);
    }

    public static Integer userId(Claims claims) {
        Number n = claims.get(CLAIM_UID, Number.class);
        return n == null ? null : n.intValue();
    }

    public static Integer branchId(Claims claims) {
        Number n = claims.get(CLAIM_BRANCH, Number.class);
        return n == null ? null : n.intValue();
    }

    public static boolean isType(Claims claims, String type) {
        return type.equals(claims.get(CLAIM_TYPE, String.class));
    }

    private IssuedToken issue(SecretKey key, String type, String subject, Integer userId, String fullName,
                              String role, Integer branchId, Duration ttl) {
        Instant now = Instant.now();
        Instant exp = now.plus(ttl);
        String jti = UUID.randomUUID().toString();
        JwtBuilder builder = Jwts.builder()
                .issuer(issuer)
                .subject(subject)
                .id(jti)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .claim(CLAIM_TYPE, type)
                .claim(CLAIM_UID, userId)
                .claim(CLAIM_NAME_KEY, fullName)
                .claim(CLAIM_ROLE_KEY, role);
        if (branchId != null) {
            builder.claim(CLAIM_BRANCH, branchId);
        }
        return new IssuedToken(builder.signWith(key).compact(), jti, exp);
    }

    private Claims parse(String token, SecretKey key) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new UnauthorizedException(ErrorCode.AUTH_TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT không hợp lệ: {}", e.getMessage());
            throw new UnauthorizedException(ErrorCode.AUTH_TOKEN_INVALID);
        }
    }

    private static SecretKey keyFrom(String secret) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("JWT secret phải dài tối thiểu 32 ký tự (HS256).");
        }
        byte[] raw = secret.getBytes(StandardCharsets.UTF_8);
        byte[] bytes = raw;
        try {
            byte[] decoded = Decoders.BASE64.decode(secret);
            // Chấp nhận secret dạng Base64 chỉ khi giải mã ra key >= 256 bit.
            if (decoded.length >= 32) {
                bytes = decoded;
            }
        } catch (RuntimeException ignored) {
            // không phải Base64 hợp lệ -> dùng ASCII/UTF-8 trực tiếp
        }
        return Keys.hmacShaKeyFor(bytes);
    }
}
