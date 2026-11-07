package edu.fpt.sba.scms.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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
import java.math.RoundingMode;
import java.time.Instant;

/**
 * Entity: Inventory (Khớp SDD §5.1.12 — 8 cột).
 * Quản lý sổ kho nguyên vật liệu theo chi nhánh.
 */
@Entity
@Table(name = "inventory", uniqueConstraints = {
        @UniqueConstraint(name = "uk_inventory_branch_ingredient", columnNames = {"branch_id", "ingredient_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inventory_id")
    private Long id; // 1. inventory_id INT PK

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch; // 2. branch_id INT FK branch

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient; // 3. ingredient_id INT FK ingredient

    @Column(name = "current_qty_base", precision = 19, scale = 4, nullable = false)
    private BigDecimal currentQtyBase; // 4. current_qty_base DECIMAL(19,4)

    @Column(name = "current_pack_units", precision = 19, scale = 4, nullable = false)
    private BigDecimal currentPackUnits; // 5. current_pack_units DECIMAL(19,4)

    @Column(name = "safety_stock_level", precision = 19, scale = 4, nullable = false)
    private BigDecimal safetyStockLevel; // 6. safety_stock_level DECIMAL(19,4) (BR-45)

    @Column(name = "low_stock_flag", nullable = false)
    private Boolean lowStockFlag; // 7. low_stock_flag BIT

    @Column(name = "last_updated", nullable = false)
    private Instant lastUpdated; // 8. last_updated TIMESTAMP

    public void recalculateStockAndFlag(BigDecimal unitsPerPack) {
        if (this.currentQtyBase == null) {
            this.currentQtyBase = BigDecimal.ZERO;
        }
        if (this.safetyStockLevel == null) {
            this.safetyStockLevel = BigDecimal.ZERO;
        }
        if (unitsPerPack != null && unitsPerPack.compareTo(BigDecimal.ZERO) > 0) {
            this.currentPackUnits = this.currentQtyBase.divide(unitsPerPack, 2, RoundingMode.HALF_UP);
        } else {
            this.currentPackUnits = BigDecimal.ZERO;
        }
        this.lowStockFlag = this.currentQtyBase.compareTo(this.safetyStockLevel) <= 0;
        this.lastUpdated = Instant.now();
    }
}
