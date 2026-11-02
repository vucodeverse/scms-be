package edu.fpt.sba.scms.model;

import edu.fpt.sba.scms.common.enums.DiscountType;
import edu.fpt.sba.scms.common.enums.RecordStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
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
 * Bảng {@code voucher} (IMPLEMENTATION.md §7.1 #27) — UC13.
 * {@code code} là mã khách nhập; {@code usedCount} tăng khi áp voucher vào đơn (UC17).
 */
@Entity
@Table(name = "voucher")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Voucher extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "voucher_id")
    private Integer id;

    @Column(name = "code", nullable = false, length = 15, unique = true)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 10)
    private DiscountType discountType;

    /** PERCENT: 0 < value ≤ 100; FIXED: VND > 0. */
    @Column(name = "discount_value", nullable = false, precision = 19, scale = 0)
    private BigDecimal discountValue;

    @Column(name = "min_order_amount", precision = 19, scale = 0)
    private BigDecimal minOrderAmount;

    @Column(name = "start_date", nullable = false)
    private Instant startDate;

    @Column(name = "end_date", nullable = false)
    private Instant endDate;

    /** null = không giới hạn lượt dùng. */
    @Column(name = "usage_limit")
    private Integer usageLimit;

    @Column(name = "used_count", nullable = false)
    private Integer usedCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RecordStatus status;
}
