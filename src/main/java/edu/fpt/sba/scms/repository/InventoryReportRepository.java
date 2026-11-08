package edu.fpt.sba.scms.repository;

import edu.fpt.sba.scms.common.enums.StockTransactionType;
import edu.fpt.sba.scms.common.enums.StocktakeStatus;
import edu.fpt.sba.scms.model.Inventory;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Truy vấn chỉ đọc cho báo cáo xuất - nhập - tồn - hao hụt (UC38) và cảnh báo tồn thấp của dashboard (UC34).
 */
public interface InventoryReportRepository extends Repository<Inventory, Long> {

    interface StockItemRow {
        Long getIngredientId();

        String getName();

        String getBaseUnit();

        String getPackUnit();

        BigDecimal getUnitsPerPack();

        BigDecimal getCurrentQty();

        BigDecimal getSafetyStockLevel();
    }

    interface MovementTotalRow {
        Long getIngredientId();

        StockTransactionType getType();

        BigDecimal getQuantity();
    }

    interface StockCountRow {
        Long getIngredientId();

        Instant getCountedAt();

        BigDecimal getBookQuantity();

        BigDecimal getCountedQuantity();
    }

    @Query("""
            select ing.id as ingredientId, ing.name as name, ing.baseUnit as baseUnit, ing.packUnit as packUnit,
                   ing.unitsPerPack as unitsPerPack, i.currentQtyBase as currentQty,
                   i.safetyStockLevel as safetyStockLevel
            from Inventory i
            join i.ingredient ing
            where i.branch.id = :branchId
            order by ing.name, ing.id""")
    List<StockItemRow> findStockItems(@Param("branchId") Integer branchId);

    /** Tổng {@code quantity_base} theo (nguyên liệu, loại) trong {@code [start, end)}. */
    @Query("""
            select t.ingredient.id as ingredientId, t.type as type, sum(t.quantityBase) as quantity
            from StockTransaction t
            where t.branch.id = :branchId and t.createdAt >= :start and t.createdAt < :end
            group by t.ingredient.id, t.type""")
    List<MovementTotalRow> sumMovements(@Param("branchId") Integer branchId,
                                        @Param("start") Instant start, @Param("end") Instant end);

    /** Tổng {@code quantity_base} theo (nguyên liệu, loại) từ {@code since} tới nay — để lùi tồn hiện tại về cuối kỳ. */
    @Query("""
            select t.ingredient.id as ingredientId, t.type as type, sum(t.quantityBase) as quantity
            from StockTransaction t
            where t.branch.id = :branchId and t.createdAt >= :since
            group by t.ingredient.id, t.type""")
    List<MovementTotalRow> sumMovementsSince(@Param("branchId") Integer branchId, @Param("since") Instant since);

    /** Dòng đã đếm của các phiên kiểm kê đã cân đối, mới nhất trước (service lấy dòng đầu tiên mỗi nguyên liệu). */
    @Query("""
            select l.id.ingredientId as ingredientId, coalesce(s.closedAt, s.startedAt) as countedAt,
                   l.bookQuantity as bookQuantity, l.countedQuantity as countedQuantity
            from StocktakeLine l
            join l.stocktake s
            where s.branch.id = :branchId and s.status in :statuses and l.countedQuantity is not null
              and s.startedAt < :end
            order by s.startedAt desc, s.id desc""")
    List<StockCountRow> findCountsBefore(@Param("branchId") Integer branchId,
                                         @Param("statuses") Collection<StocktakeStatus> statuses,
                                         @Param("end") Instant end);
}
