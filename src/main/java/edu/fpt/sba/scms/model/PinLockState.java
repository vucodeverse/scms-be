package edu.fpt.sba.scms.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

import static jakarta.persistence.GenerationType.IDENTITY;

/**
 * Lưu trạng thái đếm số lần nhập sai PIN và thời điểm tạm khóa (UC03/UC04).
 * Tách khỏi bảng {@code app_user} để có thể cập nhật trong {@code REQUIRES_NEW} không gây xung đột khóa hàng.
 */
@Entity
@Table(name = "pin_lock_state")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PinLockState {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "pin_lock_id")
    private Integer id;

    @Column(name = "lock_key", nullable = false, unique = true, length = 64)
    private String lockKey;

    @Column(name = "branch_id", nullable = false)
    private Integer branchId;

    @Column(name = "employee_id")
    private Integer employeeId;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    @Column(name = "window_start_at")
    private Instant windowStartAt;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_failed_at")
    private Instant lastFailedAt;

    @Column(name = "unlocked_by")
    private Integer unlockedBy;

    @Column(name = "unlocked_at")
    private Instant unlockedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public boolean isLockedAt(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }
}
