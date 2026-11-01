package edu.fpt.sba.scms.security.jwt;

import edu.fpt.sba.scms.common.enums.ErrorCode;
import edu.fpt.sba.scms.common.exception.UnauthorizedException;
import edu.fpt.sba.scms.config.properties.AppProperties;
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

    private static final int MIN_SECRET_LENGTH = 32;
    /** Secret dạng Base64 phải ghi rõ tiền tố này; không đọc được tiền tố thì coi là UTF-8. */
    public static final String BASE64_PREFIX = "base64:";

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
        if (props.jwt().accessSecret().equals(props.jwt().refreshSecret())) {
            throw new IllegalStateException(
                    "JWT_ACCESS_SECRET và JWT_REFRESH_SECRET phải khác nhau: dùng chung thì refresh token "
                            + "có thể bị dùng thay access token (chỉ khác claim typ).");
        }
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

    public Claims parseAccess(String token) {
        return parse(token, accessKey);
    }

    /**
     * Đọc payload refresh token <b>không ném exception</b> — chỉ dùng để biết ai đang gọi khi token đã bị
     * thu hồi/bị dùng lại (chọn khoá rate-limit). Không dùng kết quả này để cấp quyền.
     *
     * @return claims, hoặc {@code null} nếu token sai chữ ký / hết hạn / không phải refresh token
     */
    public Claims tryParseRefresh(String token) {
        try {
            return parseRefresh(token);
        } catch (UnauthorizedException e) {
            return null;
        }
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

    /**
     * Secret là UTF-8 thuần, trừ khi có tiền tố tường minh {@value #BASE64_PREFIX} (khi đó phần sau là Base64).
     * Không "tự đoán" Base64 theo độ dài: cùng một chuỗi sẽ mang nghĩa khác nhau ở từng môi trường.
     */
    private static SecretKey keyFrom(String secret) {
        if (secret == null || secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "JWT secret phải dài tối thiểu " + MIN_SECRET_LENGTH + " ký tự (HS256).");
        }
        if (secret.startsWith(BASE64_PREFIX)) {
            byte[] decoded;
            try {
                decoded = Decoders.BASE64.decode(secret.substring(BASE64_PREFIX.length()));
            } catch (RuntimeException e) {
                throw new IllegalStateException("JWT secret có tiền tố " + BASE64_PREFIX
                        + " nhưng phần sau không phải Base64 hợp lệ.", e);
            }
            if (decoded.length < MIN_SECRET_LENGTH) {
                throw new IllegalStateException("JWT secret Base64 phải giải mã ra tối thiểu "
                        + MIN_SECRET_LENGTH + " byte (HS256).");
            }
            return Keys.hmacShaKeyFor(decoded);
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
