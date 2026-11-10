package edu.fpt.sba.scms.model;

import edu.fpt.sba.scms.common.enums.ShiftHandoverStatus;
import edu.fpt.sba.scms.common.enums.ShiftHandoverType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
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
 * Bảng {@code shift_handover} — Lịch sử kiểm két bàn giao người phụ trách giữa ca (Mid-shift Handover).
 */
@Entity
@Table(name = "shift_handover")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShiftHandover {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "shift_handover_id")
    private Integer id;

    @Column(name = "shift_id", nullable = false)
    private Integer shiftId;

    @Column(name = "branch_id", nullable = false)
    private Integer branchId;

    @Column(name = "from_user_id", nullable = false)
    private Integer fromUserId;

    @Column(name = "to_user_id")
    private Integer toUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "handover_type", length = 30)
    private ShiftHandoverType handoverType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ShiftHandoverStatus status;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "expected_cash_at_out", nullable = false, precision = 19, scale = 2)
    private BigDecimal expectedCashAtOut;

    @Column(name = "outgoing_counted_cash", nullable = false, precision = 19, scale = 2)
    private BigDecimal outgoingCountedCash;

    @Column(name = "outgoing_variance", nullable = false, precision = 19, scale = 2)
    private BigDecimal outgoingVariance;

    @Column(name = "outgoing_note", length = 500)
    private String outgoingNote;

    @Column(name = "outgoing_at", nullable = false)
    private Instant outgoingAt;

    @Column(name = "outgoing_confirmed_by")
    private Integer outgoingConfirmedBy;

    @Column(name = "incoming_counted_cash", precision = 19, scale = 2)
    private BigDecimal incomingCountedCash;

    @Column(name = "incoming_variance_from_out", precision = 19, scale = 2)
    private BigDecimal incomingVarianceFromOut;

    @Column(name = "incoming_variance_from_book", precision = 19, scale = 2)
    private BigDecimal incomingVarianceFromBook;

    @Column(name = "incoming_note", length = 500)
    private String incomingNote;

    @Column(name = "incoming_at")
    private Instant incomingAt;

    @Column(name = "incoming_confirmed_by")
    private Integer incomingConfirmedBy;

    @Column(name = "withdraw_amount", precision = 19, scale = 2)
    private BigDecimal withdrawAmount;

    @Column(name = "withdraw_reason", length = 500)
    private String withdrawReason;

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
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (outgoingAt == null) {
            outgoingAt = now;
        }
        if (status == null) {
            status = ShiftHandoverStatus.PENDING_RECEIVE;
        }
        if (handoverType == null) {
            handoverType = ShiftHandoverType.MID_SHIFT;
        }
    }

    public boolean isManagerClose() {
        return handoverType == ShiftHandoverType.MANAGER_CLOSE;
    }

    public ShiftHandoverType effectiveHandoverType() {
        return handoverType != null ? handoverType : ShiftHandoverType.MID_SHIFT;
    }
}
