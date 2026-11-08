package edu.fpt.sba.scms.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Entity: StocktakeLine (Khớp SDD §5.1.14 — đúng 5 cột).
 * Chi tiết từng mặt hàng trong phiên kiểm kê.
 */
@Entity
@Table(name = "stocktake_line")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StocktakeLine {

    @EmbeddedId
    private StocktakeLineId id; // 1, 2. Composite PK (stocktake_id, ingredient_id)

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("stocktakeId")
    @JoinColumn(name = "stocktake_id")
    private Stocktake stocktake;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("ingredientId")
    @JoinColumn(name = "ingredient_id")
    private Ingredient ingredient;

    @Column(name = "book_quantity", precision = 19, scale = 4, nullable = false)
    private BigDecimal bookQuantity; // 3. book_quantity DECIMAL(19,4)

    @Column(name = "counted_quantity", precision = 19, scale = 4)
    private BigDecimal countedQuantity; // 4. counted_quantity DECIMAL(19,4)

    @Column(name = "variance", precision = 19, scale = 4)
    private BigDecimal variance; // 5. variance DECIMAL(19,4) (counted - book)
}
