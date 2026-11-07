package edu.fpt.sba.scms.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Bảng {@code wage_rate} (SDD 5.1.20, BR-14).
 * Lưu lịch sử mức lương theo giờ / lương cứng của nhân sự.
 */
@Entity
@Table(name = "wage_rate")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WageRate {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "wage_rate_id")
    private Integer id;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "hourly_rate", nullable = false, precision = 19, scale = 2)
    private BigDecimal hourlyRate;

    @Column(name = "base_salary", precision = 19, scale = 2)
    private BigDecimal baseSalary;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (effectiveFrom == null) {
            effectiveFrom = Instant.now();
        }
    }
}

