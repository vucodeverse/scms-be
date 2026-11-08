package edu.fpt.sba.scms.repository;

import edu.fpt.sba.scms.model.ShiftAssignment;
import edu.fpt.sba.scms.model.ShiftAssignmentId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ShiftAssignmentRepository extends JpaRepository<ShiftAssignment, ShiftAssignmentId> {

    List<ShiftAssignment> findByIdShiftId(Integer shiftId);

    void deleteByIdShiftId(Integer shiftId);

    @Query("""
            SELECT sa FROM ShiftAssignment sa
            JOIN sa.shift s
            WHERE sa.id.employeeId = :employeeId
              AND s.id <> :excludeShiftId
              AND s.plannedStart < :plannedEnd
              AND s.plannedEnd > :plannedStart
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
              AND s.plannedStart < :plannedEnd
              AND s.plannedEnd > :plannedStart
            """)
    List<ShiftAssignment> findConflictingAssignmentsForEmployees(
            @Param("employeeIds") List<Integer> employeeIds,
            @Param("excludeShiftId") Integer excludeShiftId,
            @Param("plannedStart") Instant plannedStart,
            @Param("plannedEnd") Instant plannedEnd);
}
