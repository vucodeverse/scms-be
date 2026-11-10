package edu.fpt.sba.scms.repository;

import edu.fpt.sba.scms.common.enums.SecurityAlertStatus;
import edu.fpt.sba.scms.common.enums.SecurityAlertType;
import edu.fpt.sba.scms.model.SecurityAlert;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface SecurityAlertRepository extends JpaRepository<SecurityAlert, Integer> {

    @Query("""
            SELECT a FROM SecurityAlert a
            WHERE (:branchId IS NULL OR a.branchId = :branchId)
              AND (:activeOnly = FALSE OR a.status = edu.fpt.sba.scms.common.enums.SecurityAlertStatus.ACTIVE)
            ORDER BY CASE WHEN a.status = edu.fpt.sba.scms.common.enums.SecurityAlertStatus.ACTIVE THEN 0 ELSE 1 END,
                     a.createdAt DESC, a.id DESC
            """)
    List<SecurityAlert> findRecentAlerts(
            @Param("branchId") Integer branchId,
            @Param("activeOnly") boolean activeOnly,
            Pageable pageable);

    long countByStatus(SecurityAlertStatus status);

    long countByBranchIdAndStatus(Integer branchId, SecurityAlertStatus status);

    List<SecurityAlert> findByTargetUserIdAndAlertTypeAndStatusIn(
            Integer targetUserId,
            SecurityAlertType alertType,
            Collection<SecurityAlertStatus> statuses);

    List<SecurityAlert> findByBranchIdAndTargetUserIdIsNullAndAlertTypeAndStatusIn(
            Integer branchId,
            SecurityAlertType alertType,
            Collection<SecurityAlertStatus> statuses);

    List<SecurityAlert> findByReferenceShiftIdAndAlertTypeAndStatusIn(
            Integer referenceShiftId,
            SecurityAlertType alertType,
            Collection<SecurityAlertStatus> statuses);
}
