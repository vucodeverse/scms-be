package edu.fpt.sba.scms.repository;

import edu.fpt.sba.scms.model.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long>, JpaSpecificationExecutor<Inventory> {

    @Query("SELECT i FROM Inventory i WHERE i.branch.id = :branchId AND i.ingredient.id = :ingredientId")
    Optional<Inventory> findByBranchIdAndIngredientId(@Param("branchId") Integer branchId,
                                                     @Param("ingredientId") Long ingredientId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.branch.id = :branchId AND i.ingredient.id = :ingredientId")
    Optional<Inventory> findForUpdate(@Param("branchId") Integer branchId,
                                      @Param("ingredientId") Long ingredientId);

    default Optional<Inventory> findForUpdate(Long branchId, Long ingredientId) {
        return findForUpdate(branchId != null ? branchId.intValue() : null, ingredientId);
    }

    default Optional<Inventory> findByBranchIdAndIngredientId(Long branchId, Long ingredientId) {
        return findByBranchIdAndIngredientId(branchId != null ? branchId.intValue() : null, ingredientId);
    }

    @Query("SELECT i FROM Inventory i JOIN FETCH i.ingredient WHERE i.branch.id = :branchId AND i.lowStockFlag = true ORDER BY i.ingredient.name ASC")
    List<Inventory> findByBranchIdAndLowStockFlagTrue(@Param("branchId") Integer branchId);

    default List<Inventory> findByBranchIdAndLowStockFlagTrue(Long branchId) {
        return findByBranchIdAndLowStockFlagTrue(branchId != null ? branchId.intValue() : null);
    }

    boolean existsByBranch_IdAndIngredient_Id(Integer branchId, Long ingredientId);
}
