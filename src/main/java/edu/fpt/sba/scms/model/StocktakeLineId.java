package edu.fpt.sba.scms.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Composite PK cho StocktakeLine (stocktake_id, ingredient_id).
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class StocktakeLineId implements Serializable {

    @Column(name = "stocktake_id")
    private Long stocktakeId; // PK 1

    @Column(name = "ingredient_id")
    private Long ingredientId; // PK 2
}
