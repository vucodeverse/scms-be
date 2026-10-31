package edu.fpt.sba.scms.repository;

import edu.fpt.sba.scms.common.enums.AuthTokenType;
import edu.fpt.sba.scms.model.AuthToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public interface AuthTokenRepository extends JpaRepository<AuthToken, Integer> {

    Optional<AuthToken> findByTokenHashAndType(String tokenHash, AuthTokenType type);

    @Query("select count(t) > 0 from AuthToken t where t.jti = :jti and t.type = edu.fpt.sba.scms.common.enums.AuthTokenType.ACCESS_REVOKED")
    boolean existsRevokedAccessJti(@Param("jti") String jti);

    @Modifying
    @Transactional
    @Query("update AuthToken t set t.revokedAt = :now where t.userId = :userId "
            + "and t.type = edu.fpt.sba.scms.common.enums.AuthTokenType.REFRESH and t.revokedAt is null")
    int revokeAllRefreshTokens(@Param("userId") Integer userId, @Param("now") Instant now);

    @Modifying
    @Transactional
    @Query("delete from AuthToken t where t.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
