package edu.fpt.sba.scms.repository;

import edu.fpt.sba.scms.model.PreOpenCashAdjustment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PreOpenCashAdjustmentRepository extends JpaRepository<PreOpenCashAdjustment, Integer> {

    @Query("""
            SELECT p FROM PreOpenCashAdjustment p
            WHERE p.branchId = :branchId
              AND p.appliedShiftId IS NULL
              AND p.cancelledAt IS NULL
            ORDER BY p.createdAt ASC, p.id ASC
            """)
    List<PreOpenCashAdjustment> findPendingByBranchIdOrderByCreatedAtAsc(@Param("branchId") Integer branchId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT p FROM PreOpenCashAdjustment p
            WHERE p.branchId = :branchId
              AND p.appliedShiftId IS NULL
              AND p.cancelledAt IS NULL
            ORDER BY p.createdAt ASC, p.id ASC
            """)
    List<PreOpenCashAdjustment> findPendingByBranchIdForUpdate(@Param("branchId") Integer branchId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PreOpenCashAdjustment p WHERE p.id = :id")
    Optional<PreOpenCashAdjustment> findByIdForUpdate(@Param("id") Integer id);
}
