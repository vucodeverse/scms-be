package edu.fpt.sba.scms.repository;

import edu.fpt.sba.scms.common.enums.ShiftStatus;
import edu.fpt.sba.scms.common.enums.ShiftType;
import edu.fpt.sba.scms.model.Shift;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShiftRepository extends JpaRepository<Shift, Integer>, JpaSpecificationExecutor<Shift> {

    Optional<Shift> findFirstByBranchIdAndStatusOrderByOpenedAtDesc(Integer branchId, ShiftStatus status);

    Optional<Shift> findFirstByBranchIdAndStatusOrderByClosedAtDescIdDesc(Integer branchId, ShiftStatus status);

    List<Shift> findByStatus(ShiftStatus status);

    List<Shift> findByStatusOrderByClosedAtDescIdDesc(ShiftStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Shift s WHERE s.id = :id")
    Optional<Shift> findByIdForUpdate(@Param("id") Integer id);

    boolean existsByBranchIdAndStatus(Integer branchId, ShiftStatus status);

    boolean existsByBranchIdAndShiftDateAndShiftTypeAndStatusNot(
            Integer branchId, LocalDate shiftDate, ShiftType shiftType, ShiftStatus status);

    boolean existsByBranchIdAndShiftDateAndShiftTypeAndStatusNotAndIdNot(
            Integer branchId, LocalDate shiftDate, ShiftType shiftType, ShiftStatus status, Integer id);

    Optional<Shift> findFirstByBranchIdAndShiftDateAndShiftTypeAndStatusOrderByIdAsc(
            Integer branchId, LocalDate shiftDate, ShiftType shiftType, ShiftStatus status);

    List<Shift> findByBranchIdAndShiftDateBetweenOrderByShiftDateAscPlannedStartAsc(
            Integer branchId, LocalDate startDate, LocalDate endDate);

    Page<Shift> findByBranchIdOrderByShiftDateDescPlannedStartDesc(Integer branchId, Pageable pageable);

    boolean existsByBranchId(Integer branchId);
}
