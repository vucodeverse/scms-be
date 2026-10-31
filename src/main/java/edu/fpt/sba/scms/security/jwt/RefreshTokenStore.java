package edu.fpt.sba.scms.security.jwt;

import edu.fpt.sba.scms.common.enums.AuthTokenType;
import edu.fpt.sba.scms.common.util.SecurityUtil;
import edu.fpt.sba.scms.model.AuthToken;
import edu.fpt.sba.scms.repository.AuthTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Lưu refresh token dưới dạng SHA-256 (IMPLEMENTATION.md §7.1 #43, §10.1) và jti access token bị thu hồi.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenStore {

    private final AuthTokenRepository authTokenRepository;

    @Transactional
    public void saveRefreshToken(Integer userId, JwtTokenProvider.IssuedToken token) {
        authTokenRepository.save(AuthToken.builder()
                .userId(userId)
                .type(AuthTokenType.REFRESH)
                .tokenHash(SecurityUtil.sha256Hex(token.token()))
                .jti(token.jti())
                .expiresAt(token.expiresAt().truncatedTo(java.time.temporal.ChronoUnit.MILLIS))
                .build());
    }

    public AuthToken findUsable(String rawRefreshToken) {
        return authTokenRepository
                .findByTokenHashAndType(SecurityUtil.sha256Hex(rawRefreshToken), AuthTokenType.REFRESH)
                .filter(AuthToken::isUsable)
                .orElse(null);
    }

    /** Bản ghi bất kể trạng thái — dùng để phát hiện token đã bị thu hồi (reuse/replay), không để cấp quyền. */
    public AuthToken findAny(String rawRefreshToken) {
        return authTokenRepository
                .findByTokenHashAndType(SecurityUtil.sha256Hex(rawRefreshToken), AuthTokenType.REFRESH)
                .orElse(null);
    }

    @Transactional
    public void revoke(AuthToken token) {
        if (token.getRevokedAt() == null) {
            token.setRevokedAt(Instant.now());
            authTokenRepository.save(token);
        }
    }

    @Transactional
    public void revokeAllOfUser(Integer userId) {
        authTokenRepository.revokeAllRefreshTokens(userId, Instant.now());
    }

    @Transactional
    public void revokeAccessJti(Integer userId, String jti, Instant accessExpiresAt) {
        if (jti == null) {
            return;
        }
        authTokenRepository.save(AuthToken.builder()
                .userId(userId)
                .type(AuthTokenType.ACCESS_REVOKED)
                .tokenHash(SecurityUtil.sha256Hex("revoked:" + jti))
                .jti(jti)
                .expiresAt(accessExpiresAt == null ? null : accessExpiresAt.truncatedTo(java.time.temporal.ChronoUnit.MILLIS))
                .build());
    }

    /** Dọn token hết hạn mỗi giờ (job nội bộ, không cần scheduler bên ngoài). */
    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
    @Transactional
    public void purgeExpired() {
        authTokenRepository.deleteExpired(Instant.now());
    }
}
