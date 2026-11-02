package edu.fpt.sba.scms.model;

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

import static jakarta.persistence.GenerationType.IDENTITY;

/** Bảng {@code topping} (IMPLEMENTATION.md §7.1 #7) — UC11. */
@Entity
@Table(name = "topping")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Topping extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "topping_id")
    private Integer id;

    @Column(name = "name", nullable = false, length = 100, unique = true)
    private String name;

    /** Giá gốc của topping (VND) khi sản phẩm không đặt giá riêng. */
    @Column(name = "default_price", nullable = false, precision = 19, scale = 0)
    private BigDecimal defaultPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RecordStatus status;
}
