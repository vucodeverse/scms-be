package edu.fpt.sba.scms.model;

import edu.fpt.sba.scms.common.enums.ProductSizeName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Entity: Recipe (Khớp SDD §5.1.10 — Định nghĩa định lượng tiêu hao nguyên vật liệu BOM cho sản phẩm).
 * Ràng buộc Unique: (product_id, ingredient_id, size)
 */
@Entity
@Table(name = "recipe", uniqueConstraints = {
        @UniqueConstraint(name = "uk_recipe_product_ingredient_size", columnNames = {"product_id", "ingredient_id", "size"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Recipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recipe_id")
    private Integer id; // 1. recipe_id INT PK AUTO_INCREMENT

    @Column(name = "product_id", nullable = false)
    private Integer productId; // 2. product_id INT NOT NULL

    @Column(name = "ingredient_id", nullable = false)
    private Integer ingredientId; // 3. ingredient_id INT NOT NULL

    @Enumerated(EnumType.STRING)
    @Column(name = "size", length = 10)
    private ProductSizeName size; // 4. size VARCHAR(10) (NULL = áp dụng chung cho mọi size)

    @Column(name = "quantity_base", precision = 19, scale = 4, nullable = false)
    private BigDecimal quantityBase; // 5. quantity_base DECIMAL(19,4) NOT NULL (định lượng đơn vị cơ sở)
}

