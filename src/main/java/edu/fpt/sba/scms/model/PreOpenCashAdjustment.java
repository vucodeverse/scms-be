package edu.fpt.sba.scms.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.math.BigDecimal;
import java.time.Instant;

import static jakarta.persistence.GenerationType.IDENTITY;

/**
 * Khoản Quản lý nạp thêm tiền mặt vào két trước khi mở ca đầu ngày.
 * Sẽ được tự động gộp thành giao dịch CASH_IN thuộc ca mới ngay khi nhân viên/quản lý mở ca.
 */
@Entity
@Table(name = "pre_open_cash_adjustment", indexes = {
        @Index(name = "idx_pre_open_cash_branch_pending", columnList = "branch_id, applied_shift_id, cancelled_at, created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreOpenCashAdjustment {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "pre_open_cash_adjustment_id")
    private Integer id;

    @Column(name = "branch_id", nullable = false)
    private Integer branchId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "created_by", nullable = false)
    private Integer createdBy;

    @Column(name = "approved_by")
    private Integer approvedBy;

    @Column(name = "applied_shift_id")
    private Integer appliedShiftId;

    @Column(name = "applied_at")
    private Instant appliedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by")
    private Integer cancelledBy;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public boolean isPending() {
        return appliedShiftId == null && cancelledAt == null;
    }
}
