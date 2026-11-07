package edu.fpt.sba.scms.model;

import edu.fpt.sba.scms.common.enums.StockTransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Entity: StockTransaction (Khớp SDD §5.1.15 — đúng 16 cột).
 * Quản lý biến động xuất / nhập / hủy / điều chỉnh kho.
 * Thông tin hóa đơn, mã phiếu được lưu trong trường reason để giữ nguyên vẹn 16 cột.
 */
@Entity
@Table(name = "stock_transaction", indexes = {
        @Index(name = "idx_stock_tx_branch_created", columnList = "branch_id, created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stock_transaction_id")
    private Long id; // 1. stock_transaction_id INT PK

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch; // 2. branch_id INT FK branch

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient; // 3. ingredient_id INT FK ingredient

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 20, nullable = false)
    private StockTransactionType type; // 4. type VARCHAR(20) NOT NULL (RECEIVE, ISSUE, DISPOSAL, ADJUST)

    @Column(name = "shift_id")
    private Long shiftId; // 5. shift_id INT FK shift (nullable)

    @Column(name = "stocktake_id")
    private Long stocktakeId; // 6. stocktake_id INT FK stocktake (nullable, khi type = ADJUST)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy; // 7. created_by INT FK user

    @Column(name = "created_at", nullable = false)
    private Instant createdAt; // 8. created_at TIMESTAMP

    @Column(name = "cartons", precision = 19, scale = 4)
    private BigDecimal cartons; // 9. cartons DECIMAL(19,4) (BR-40)

    @Column(name = "loose_units", precision = 19, scale = 4)
    private BigDecimal looseUnits; // 10. loose_units DECIMAL(19,4)

    @Column(name = "purchase_price", precision = 19, scale = 2)
    private BigDecimal purchasePrice; // 11. purchase_price DECIMAL(19,2)

    @Column(name = "line_total", precision = 19, scale = 2)
    private BigDecimal lineTotal; // 12. line_total DECIMAL(19,2)

    @Column(name = "quantity_packs", precision = 19, scale = 4)
    private BigDecimal quantityPacks; // 13. quantity_packs DECIMAL(19,4)

    @Column(name = "quantity_base", precision = 19, scale = 4, nullable = false)
    private BigDecimal quantityBase; // 14. quantity_base DECIMAL(19,4) NOT NULL

    @Column(name = "reason", length = 500)
    private String reason; // 15. reason VARCHAR(500)

    @Column(name = "evidence_image_url", length = 500)
    private String evidenceImageUrl; // 16. evidence_image_url VARCHAR(500)
}
