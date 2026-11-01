package edu.fpt.sba.scms.repository;

import edu.fpt.sba.scms.common.enums.AuthTokenType;
import edu.fpt.sba.scms.model.AuthToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface AuthTokenRepository extends JpaRepository<AuthToken, Integer> {

    Optional<AuthToken> findByTokenHashAndType(String tokenHash, AuthTokenType type);

    @Query("select count(t) > 0 from AuthToken t where t.jti = :jti and t.type = edu.fpt.sba.scms.common.enums.AuthTokenType.ACCESS_REVOKED")
    boolean existsRevokedAccessJti(@Param("jti") String jti);

    /**
     * Thu hồi <b>nguyên tử</b> đúng 1 refresh token: chỉ cập nhật khi token còn hiệu lực
     * ({@code revoked_at is null} và {@code expires_at > now}).
     * <p>
     * Đây là chốt chặn race condition của rotation: hai request refresh đồng thời cùng một token thì DB chỉ cho
     * đúng một câu UPDATE trả về {@code 1}; request còn lại nhận {@code 0} và bị từ chối. Không cần {@code @Version}
     * trên entity và không phụ thuộc việc đọc-rồi-ghi ở tầng service.
     *
     * @return số dòng bị cập nhật (1 = giành được token, 0 = token đã bị dùng/hết hạn)
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AuthToken t set t.revokedAt = :now where t.id = :id "
            + "and t.type = edu.fpt.sba.scms.common.enums.AuthTokenType.REFRESH "
            + "and t.revokedAt is null and t.expiresAt > :now")
    int revokeIfActive(@Param("id") Integer id, @Param("now") Instant now);

    @Modifying
    @Query("update AuthToken t set t.revokedAt = :now where t.userId = :userId "
            + "and t.type = edu.fpt.sba.scms.common.enums.AuthTokenType.REFRESH and t.revokedAt is null")
    int revokeAllRefreshTokens(@Param("userId") Integer userId, @Param("now") Instant now);

    /** Ranh giới transaction do {@code RefreshTokenStore} quyết định — repository không tự commit. */
    @Modifying
    @Query("delete from AuthToken t where t.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
