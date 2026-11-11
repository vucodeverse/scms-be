package edu.fpt.sba.scms.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Entity: Ingredient (Khớp SDD §5.1.11 — 7 cột).
 * Không có cột 'code' hay 'category' theo đúng định nghĩa CSDL.
 */
@Entity
@Table(name = "ingredient")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ingredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ingredient_id")
    private Long id; // 1. ingredient_id INT PK

    @Column(name = "name", length = 150, nullable = false)
    private String name; // 2. name VARCHAR(150) NOT NULL

    @Column(name = "base_unit", length = 20, nullable = false)
    private String baseUnit; // 3. base_unit VARCHAR(20) NOT NULL (ml, g, can)

    @Column(name = "pack_unit", length = 50)
    private String packUnit; // 4. pack_unit VARCHAR(50) (carton, bag) - BR-40

    @Column(name = "units_per_pack", precision = 19, scale = 4)
    private BigDecimal unitsPerPack; // 5. units_per_pack DECIMAL(19,4)

    @Column(name = "unit_cost", precision = 19, scale = 2)
    private BigDecimal unitCost; // 6. unit_cost DECIMAL(19,2)

    @Column(name = "status", length = 20, nullable = false)
    private String status; // 7. status VARCHAR(20) NOT NULL (ACTIVE / INACTIVE)
}
