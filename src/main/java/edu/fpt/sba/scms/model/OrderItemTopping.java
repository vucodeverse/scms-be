package edu.fpt.sba.scms.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
 * Bảng {@code order_item_topping} (SDD 5.1.25) — Topping được chọn cho dòng món trong đơn hàng.
 */
@Entity
@Table(name = "order_item_topping",
        uniqueConstraints = @UniqueConstraint(name = "uk_order_line_topping", columnNames = {"order_line_id", "topping_id"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemTopping {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "order_item_topping_id")
    private Integer id;

    @Column(name = "order_line_id", nullable = false)
    private Integer orderLineId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_line_id", insertable = false, updatable = false)
    private OrderLine orderLine;

    @Column(name = "topping_id", nullable = false)
    private Integer toppingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topping_id", insertable = false, updatable = false)
    private Topping topping;

    @Column(name = "extra_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal extraPrice;
}
