package edu.fpt.sba.scms.model;

import edu.fpt.sba.scms.common.enums.ProductSizeName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

import static jakarta.persistence.GenerationType.IDENTITY;

/** Bảng {@code product_size} (IMPLEMENTATION.md §7.1 #6) — UC11. UNIQUE(product_id, size). */
@Entity
@Table(name = "product_size",
        uniqueConstraints = @UniqueConstraint(name = "uk_product_size", columnNames = {"product_id", "size"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSize extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "product_size_id")
    private Integer id;

    @Column(name = "product_id", nullable = false)
    private Integer productId;

    @Enumerated(EnumType.STRING)
    @Column(name = "size", nullable = false, length = 5)
    private ProductSizeName size;

    /** VND — không âm (validation tầng service). */
    @Column(name = "price", nullable = false, precision = 19, scale = 0)
    private BigDecimal price;
}
