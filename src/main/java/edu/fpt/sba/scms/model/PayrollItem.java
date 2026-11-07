package edu.fpt.sba.scms.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
 * Bảng {@code payroll_item} (SDD 5.1.22, UC45, BR-53).
 * Chi tiết phiếu lương của một nhân viên trong kỳ tính lương.
 */
@Entity
@Table(name = "payroll_item")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayrollItem {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "payroll_item_id")
    private Integer id;

    @Column(name = "pay_period_id", nullable = false)
    private Integer payPeriodId;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @Column(name = "total_hours", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalHours;

    @Column(name = "hourly_rate", nullable = false, precision = 19, scale = 2)
    private BigDecimal hourlyRate;

    @Column(name = "base_salary", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal baseSalary = BigDecimal.ZERO;

    @Column(name = "bonus", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal bonus = BigDecimal.ZERO;

    @Column(name = "deduction", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal deduction = BigDecimal.ZERO;

    @Column(name = "net_salary", nullable = false, precision = 19, scale = 2)
    private BigDecimal netSalary;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (bonus == null) {
            bonus = BigDecimal.ZERO;
        }
        if (deduction == null) {
            deduction = BigDecimal.ZERO;
        }
        if (baseSalary == null) {
            baseSalary = BigDecimal.ZERO;
        }
    }
}

