package edu.fpt.sba.scms.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

import static jakarta.persistence.GenerationType.IDENTITY;

/**
 * Bảng {@code order_voucher} (SDD 5.1.28) — Voucher áp dụng cho đơn hàng (UC17, BR-21).
 */
@Entity
@Table(name = "order_voucher",
        uniqueConstraints = @UniqueConstraint(name = "uk_order_voucher", columnNames = {"order_id"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderVoucher {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "order_voucher_id")
    private Integer id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Integer orderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", insertable = false, updatable = false)
    private Order order;

    @Column(name = "voucher_id", nullable = false)
    private Integer voucherId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voucher_id", insertable = false, updatable = false)
    private Voucher voucher;

    @Column(name = "discount_amount_applied", nullable = false, precision = 19, scale = 2)
    private BigDecimal discountAmountApplied;
}
