package edu.fpt.sba.scms.repository;

import edu.fpt.sba.scms.common.enums.StocktakeStatus;
import edu.fpt.sba.scms.model.Stocktake;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface StocktakeRepository extends JpaRepository<Stocktake, Long>, JpaSpecificationExecutor<Stocktake> {

    boolean existsByBranch_IdAndStatus(Integer branchId, StocktakeStatus status);

    Optional<Stocktake> findByBranch_IdAndStatus(Integer branchId, StocktakeStatus status);

    Optional<Stocktake> findByIdAndBranch_Id(Long id, Integer branchId);

    @Query("SELECT COUNT(s) FROM Stocktake s WHERE s.branch.id = :branchId AND s.startedAt >= :startOfDay AND s.id <= :id")
    long countTodayBranchStocktakesUpToId(@Param("branchId") Integer branchId,
                                         @Param("startOfDay") Instant startOfDay,
                                         @Param("id") Long id);

    @Query("SELECT COUNT(s) FROM Stocktake s WHERE s.branch.id = :branchId AND s.startedAt >= :startOfDay")
    long countTodayBranchStocktakes(@Param("branchId") Integer branchId,
                                    @Param("startOfDay") Instant startOfDay);
}
