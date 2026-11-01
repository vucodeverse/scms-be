package edu.fpt.sba.scms.service.support;

import edu.fpt.sba.scms.common.enums.AuthTokenType;
import edu.fpt.sba.scms.common.util.SecurityUtil;
import edu.fpt.sba.scms.model.AuthToken;
import edu.fpt.sba.scms.repository.AuthTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Lưu refresh token dưới dạng SHA-256 (IMPLEMENTATION.md §7.1 #43, §10.1) và jti access token bị thu hồi.
 * <p>
 * Đặt ở {@code service.support} (không phải {@code security.jwt}) để {@code AuthServiceImpl} phụ thuộc vào tầng
 * service thay vì tầng hạ tầng JWT — {@code AuthServiceImpl} chỉ cần một API trừu tượng "lưu/thu hồi token",
 * khi chuyển sang Redis chỉ thay class này.
 */
@Component
@RequiredArgsConstructor
@Transactional
public class RefreshTokenStore {

    private final AuthTokenRepository authTokenRepository;

    /**
     * Lưu refresh token đã cấp. Chỉ nhận dữ liệu thô (không phụ thuộc kiểu token của tầng JWT) — ArchUnit
     * {@code chi_auth_duoc_dung_security_jwt} giới hạn việc service dùng {@code security.jwt} cho đúng AuthServiceImpl.
     */
    public void saveRefreshToken(Integer userId, String rawToken, String jti, Instant expiresAt) {
        authTokenRepository.save(AuthToken.builder()
                .userId(userId)
                .type(AuthTokenType.REFRESH)
                .tokenHash(SecurityUtil.sha256Hex(rawToken))
                .jti(jti)
                .expiresAt(expiresAt.truncatedTo(ChronoUnit.MILLIS))
                .build());
    }

    /** Bản ghi bất kể trạng thái — dùng để phát hiện token đã bị thu hồi (reuse/replay), không để cấp quyền. */
    @Transactional(readOnly = true)
    public AuthToken findAny(String rawRefreshToken) {
        return findByHash(rawRefreshToken);
    }

    /** Token còn hiệu lực (chưa thu hồi, chưa hết hạn); null nếu không dùng được. */
    @Transactional(readOnly = true)
    public AuthToken findUsable(String rawRefreshToken) {
        AuthToken token = findByHash(rawRefreshToken);
        return token != null && token.isUsable() ? token : null;
    }

    /**
     * Thu hồi token đúng một lần, <b>nguyên tử ở tầng DB</b> — chốt chặn race condition của rotation.
     * Hai request refresh đồng thời cùng một token: chỉ một câu UPDATE thắng (trả {@code true}), request kia
     * nhận {@code false} và phải bị từ chối thay vì cũng được cấp cặp token mới.
     *
     * @return {@code true} nếu lần gọi này thu hồi được token (token còn hiệu lực)
     */
    public boolean revokeIfActive(AuthToken token) {
        // clearAutomatically để bản trong persistence context không còn là snapshot cũ (revokedAt = null) —
        // một lần flush sau đó (vd. saveRefreshToken) không thể ghi đè lại thành "chưa thu hồi".
        return authTokenRepository.revokeIfActive(token.getId(), Instant.now()) == 1;
    }

    public void revokeAllOfUser(Integer userId) {
        authTokenRepository.revokeAllRefreshTokens(userId, Instant.now());
    }

    public void revokeAccessJti(Integer userId, String jti, Instant accessExpiresAt) {
        if (jti == null) {
            return;
        }
        authTokenRepository.save(AuthToken.builder()
                .userId(userId)
                .type(AuthTokenType.ACCESS_REVOKED)
                .tokenHash(SecurityUtil.sha256Hex("revoked:" + jti))
                .jti(jti)
                .expiresAt(accessExpiresAt == null ? null : accessExpiresAt.truncatedTo(ChronoUnit.MILLIS))
                .build());
    }

    /** Dọn token hết hạn mỗi giờ (job nội bộ, không cần scheduler bên ngoài). */
    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
    public void purgeExpired() {
        authTokenRepository.deleteExpired(Instant.now());
    }

    private AuthToken findByHash(String rawRefreshToken) {
        return authTokenRepository
                .findByTokenHashAndType(SecurityUtil.sha256Hex(rawRefreshToken), AuthTokenType.REFRESH)
                .orElse(null);
    }
}
