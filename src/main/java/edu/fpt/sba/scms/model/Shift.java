package edu.fpt.sba.scms.model;

import edu.fpt.sba.scms.common.enums.ShiftStatus;
import edu.fpt.sba.scms.common.enums.ShiftType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static jakarta.persistence.GenerationType.IDENTITY;

/**
 * Bảng {@code shift} (SDD 5.1.16).
 * Vừa là phiên ca làm việc, vừa là phiên két tiền (Cash Drawer Session) để đối soát doanh thu.
 */
@Entity
@Table(name = "shift")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Shift {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "shift_id")
    private Integer id;

    @Column(name = "branch_id", nullable = false)
    private Integer branchId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", insertable = false, updatable = false)
    private Branch branch;

    @Column(name = "shift_date", nullable = false)
    private LocalDate shiftDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "shift_type", nullable = false, length = 20)
    private ShiftType shiftType;

    @Column(name = "planned_start", nullable = false)
    private Instant plannedStart;

    @Column(name = "planned_end", nullable = false)
    private Instant plannedEnd;

    @Column(name = "opened_by")
    private Integer openedBy;

    @Column(name = "opened_at")
    private Instant openedAt;

    @Column(name = "closed_by")
    private Integer closedBy;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "opening_cash", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal openingCash = BigDecimal.ZERO;

    @Column(name = "cash_revenue", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal cashRevenue = BigDecimal.ZERO;

    @Column(name = "transfer_revenue", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal transferRevenue = BigDecimal.ZERO;

    @Column(name = "expected_cash", precision = 19, scale = 2)
    private BigDecimal expectedCash;

    @Column(name = "counted_cash", precision = 19, scale = 2)
    private BigDecimal countedCash;

    @Column(name = "variance", precision = 19, scale = 2)
    private BigDecimal variance;

    @Column(name = "discrepancy_reason", length = 500)
    private String discrepancyReason;

    @Column(name = "previous_shift_id")
    private Integer previousShiftId;

    @Column(name = "previous_closing_cash", precision = 19, scale = 2)
    private BigDecimal previousClosingCash;

    @Column(name = "opening_variance", precision = 19, scale = 2)
    private BigDecimal openingVariance;

    @Column(name = "opening_note", length = 500)
    private String openingNote;

    @Column(name = "active_cashier_id")
    private Integer activeCashierId;

    @Column(name = "handover_pending")
    private Boolean handoverPending;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ShiftStatus status;

    @Version
    @Column(name = "row_version")
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
        if (openingCash == null) {
            openingCash = BigDecimal.ZERO;
        }
        if (cashRevenue == null) {
            cashRevenue = BigDecimal.ZERO;
        }
        if (transferRevenue == null) {
            transferRevenue = BigDecimal.ZERO;
        }
        if (status == null) {
            status = ShiftStatus.SCHEDULED;
        }
        if (handoverPending == null) {
            handoverPending = Boolean.FALSE;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isOpen() {
        return status == ShiftStatus.OPEN;
    }

    public boolean isHandoverPending() {
        return Boolean.TRUE.equals(handoverPending);
    }
}
