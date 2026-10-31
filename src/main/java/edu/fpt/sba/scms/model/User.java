package edu.fpt.sba.scms.model;

import edu.fpt.sba.scms.common.enums.UserPosition;
import edu.fpt.sba.scms.common.enums.UserStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

import static jakarta.persistence.GenerationType.IDENTITY;

/**
 * Bảng {@code app_user} (IMPLEMENTATION.md §7.1 #3).
 * Hash không bao giờ được trả ra controller/serialize JSON.
 */
@Entity
@Table(name = "app_user")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "user_id")
    private Integer id;

    /** NULL chỉ với COO. */
    @Column(name = "branch_id")
    private Integer branchId;

    /** Chi nhánh (chỉ cần cho hiển thị branchName); quan hệ read-only, tính branch_id ở @JoinColumn. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", insertable = false, updatable = false)
    private Branch branch;

    @Column(name = "role_id", nullable = false)
    private Integer roleId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", insertable = false, updatable = false)
    private Role role;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "username", length = 50)
    private String username;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "pin_hash", length = 100)
    private String pinHash;

    /** SHA-256(branchId + ':' + pin + pepper) — dùng cho unique index filtered (IMPLEMENTATION.md §7.2). */
    @Column(name = "pin_lookup_hash", length = 64)
    private String pinLookupHash;

    @Column(name = "pin_set_at")
    private Instant pinSetAt;

    @Column(name = "card_code", length = 50)
    private String cardCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "position", length = 20)
    private UserPosition position;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status;

    /** MySQL không có rowversion -> dùng @Version kiểu long. */
    @Version
    @Column(name = "row_version", nullable = false)
    private Long rowVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (status == null) {
            status = UserStatus.ACTIVE;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public String roleCode() {
        return role == null ? null : role.getCode();
    }
}
