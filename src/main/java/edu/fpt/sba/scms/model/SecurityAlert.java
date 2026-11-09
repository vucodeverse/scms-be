package edu.fpt.sba.scms.model;

import edu.fpt.sba.scms.common.enums.SecurityAlertSeverity;
import edu.fpt.sba.scms.common.enums.SecurityAlertStatus;
import edu.fpt.sba.scms.common.enums.SecurityAlertType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

import static jakarta.persistence.GenerationType.IDENTITY;

/**
 * Cảnh báo bảo mật gửi Quản lý chi nhánh &amp; COO (ví dụ: khóa tạm PIN Kiosk sau nhiều lần nhập sai).
 */
@Entity
@Table(name = "security_alert", indexes = {
        @Index(name = "idx_security_alert_branch_status_created", columnList = "branch_id, status, created_at"),
        @Index(name = "idx_security_alert_created", columnList = "created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityAlert {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "alert_id")
    private Integer id;

    @Column(name = "branch_id", nullable = false)
    private Integer branchId;

    @Column(name = "branch_name", length = 150)
    private String branchName;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 40)
    private SecurityAlertType alertType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    private SecurityAlertSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SecurityAlertStatus status;

    @Column(name = "target_user_id")
    private Integer targetUserId;

    @Column(name = "target_user_name", length = 120)
    private String targetUserName;

    @Column(name = "target_role", length = 30)
    private String targetRole;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "message", nullable = false, length = 500)
    private String message;

    @Column(name = "resolved_by")
    private Integer resolvedBy;

    @Column(name = "resolved_by_name", length = 120)
    private String resolvedByName;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolve_note", length = 255)
    private String resolveNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = SecurityAlertStatus.ACTIVE;
        }
    }
}
