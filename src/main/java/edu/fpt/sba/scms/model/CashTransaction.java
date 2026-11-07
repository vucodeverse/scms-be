package edu.fpt.sba.scms.model;

import edu.fpt.sba.scms.common.enums.CashTransactionType;
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
 * Bảng {@code cash_transaction} (SDD 5.1.31, BR-37, BR-39).
 * Lưu vết mọi biến động tiền mặt trong két (Nạp thêm, Rút bớt, Hoàn tiền, Đóng/Mở ca).
 */
@Entity
@Table(name = "cash_transaction")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashTransaction {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "cash_transaction_id")
    private Integer id;

    @Column(name = "shift_id", nullable = false)
    private Integer shiftId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private CashTransactionType type;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "created_by", nullable = false)
    private Integer createdBy;

    @Column(name = "approved_by")
    private Integer approvedBy;

    @Column(name = "is_self_initiated", nullable = false)
    @Builder.Default
    private Boolean isSelfInitiated = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (isSelfInitiated == null) {
            isSelfInitiated = false;
        }
    }
}

