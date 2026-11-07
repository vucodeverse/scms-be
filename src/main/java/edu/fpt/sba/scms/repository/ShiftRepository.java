package edu.fpt.sba.scms.repository;

import edu.fpt.sba.scms.common.enums.ShiftStatus;
import edu.fpt.sba.scms.model.Shift;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShiftRepository extends JpaRepository<Shift, Integer>, JpaSpecificationExecutor<Shift> {

    Optional<Shift> findFirstByBranchIdAndStatusOrderByOpenedAtDesc(Integer branchId, ShiftStatus status);

    boolean existsByBranchIdAndStatus(Integer branchId, ShiftStatus status);

    List<Shift> findByBranchIdAndShiftDateBetweenOrderByShiftDateAscPlannedStartAsc(
            Integer branchId, LocalDate startDate, LocalDate endDate);

    Page<Shift> findByBranchIdOrderByShiftDateDescPlannedStartDesc(Integer branchId, Pageable pageable);
}

