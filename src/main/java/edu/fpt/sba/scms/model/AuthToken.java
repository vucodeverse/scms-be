package edu.fpt.sba.scms.model;

import edu.fpt.sba.scms.common.enums.AuthTokenType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

import static jakarta.persistence.GenerationType.IDENTITY;

/**
 * Bảng {@code auth_token} (IMPLEMENTATION.md §7.1 #43).
 * Type REFRESH: hash SHA-256 của refresh token. ACCESS_REVOKED: jti của access token đã logout, dùng để chặn sớm.
 */
@Entity
@Table(name = "auth_token")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthToken {


    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "token_id")
    private Integer id;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private AuthTokenType type;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(name = "jti", length = 64)
    private String jti;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public boolean isUsable() {
        return revokedAt == null && expiresAt.isAfter(Instant.now());
    }
}
