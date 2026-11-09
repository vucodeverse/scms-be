package edu.fpt.sba.scms.repository;

import edu.fpt.sba.scms.model.ShiftAssignment;
import edu.fpt.sba.scms.model.ShiftAssignmentId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface ShiftAssignmentRepository extends JpaRepository<ShiftAssignment, ShiftAssignmentId> {

    List<ShiftAssignment> findByIdShiftId(Integer shiftId);

    void deleteByIdShiftId(Integer shiftId);

    /**
     * Phát hiện phân công trùng giờ theo BR-15:
     * <ul>
     *   <li>Ca {@code SCHEDULED} / {@code OPEN}: kiểm tra theo khung dự kiến {@code [plannedStart, plannedEnd]}.</li>
     *   <li>Ca {@code CLOSED}: kiểm tra theo thời gian vận hành thực tế {@code [openedAt, closedAt]}
     *   để sau khi đóng ca sớm vẫn có thể phân công nhân sự sang ca nối tiếp mới.</li>
     * </ul>
     */
    @Query("""
            SELECT sa FROM ShiftAssignment sa
            JOIN sa.shift s
            WHERE sa.id.employeeId = :employeeId
              AND s.id <> :excludeShiftId
              AND (
                (s.status <> edu.fpt.sba.scms.common.enums.ShiftStatus.CLOSED
                  AND s.plannedStart < :plannedEnd
                  AND s.plannedEnd > :plannedStart)
                OR
                (s.status = edu.fpt.sba.scms.common.enums.ShiftStatus.CLOSED
                  AND COALESCE(s.openedAt, s.plannedStart) < :plannedEnd
                  AND COALESCE(s.closedAt, s.plannedEnd) > :plannedStart)
              )
            """)
    List<ShiftAssignment> findConflictingAssignments(
            @Param("employeeId") Integer employeeId,
            @Param("excludeShiftId") Integer excludeShiftId,
            @Param("plannedStart") Instant plannedStart,
            @Param("plannedEnd") Instant plannedEnd);

    @Query("""
            SELECT sa FROM ShiftAssignment sa
            JOIN sa.shift s
            WHERE sa.id.employeeId IN :employeeIds
              AND s.id <> :excludeShiftId
              AND (
                (s.status <> edu.fpt.sba.scms.common.enums.ShiftStatus.CLOSED
                  AND s.plannedStart < :plannedEnd
                  AND s.plannedEnd > :plannedStart)
                OR
                (s.status = edu.fpt.sba.scms.common.enums.ShiftStatus.CLOSED
                  AND COALESCE(s.openedAt, s.plannedStart) < :plannedEnd
                  AND COALESCE(s.closedAt, s.plannedEnd) > :plannedStart)
              )
            """)
    List<ShiftAssignment> findConflictingAssignmentsForEmployees(
            @Param("employeeIds") List<Integer> employeeIds,
            @Param("excludeShiftId") Integer excludeShiftId,
            @Param("plannedStart") Instant plannedStart,
            @Param("plannedEnd") Instant plannedEnd);

    /**
     * Lấy phân công ca phục vụ kiểm tra quyền check-in/check-out Kiosk tại chi nhánh.
     */
    @Query("""
            SELECT sa FROM ShiftAssignment sa
            JOIN FETCH sa.shift s
            WHERE s.branchId = :branchId
              AND (
                s.status <> edu.fpt.sba.scms.common.enums.ShiftStatus.CLOSED
                OR s.shiftDate >= :fromDate
              )
            ORDER BY s.shiftDate DESC, s.plannedStart DESC, s.id DESC
            """)
    List<ShiftAssignment> findAttendanceRelevantByBranch(
            @Param("branchId") Integer branchId,
            @Param("fromDate") LocalDate fromDate);

    /**
     * Lấy phân công ca của một nhân viên tại chi nhánh để đối chiếu check-in/check-out.
     */
    @Query("""
            SELECT sa FROM ShiftAssignment sa
            JOIN FETCH sa.shift s
            WHERE sa.id.employeeId = :employeeId
              AND s.branchId = :branchId
              AND (
                s.status <> edu.fpt.sba.scms.common.enums.ShiftStatus.CLOSED
                OR s.shiftDate >= :fromDate
              )
            ORDER BY s.shiftDate DESC, s.plannedStart DESC, s.id DESC
            """)
    List<ShiftAssignment> findAttendanceRelevantForEmployee(
            @Param("employeeId") Integer employeeId,
            @Param("branchId") Integer branchId,
            @Param("fromDate") LocalDate fromDate);
}
