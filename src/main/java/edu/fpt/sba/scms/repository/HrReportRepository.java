package edu.fpt.sba.scms.repository;

import edu.fpt.sba.scms.common.enums.UserStatus;
import edu.fpt.sba.scms.model.Attendance;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * Truy vấn chỉ đọc cho báo cáo nhân sự &amp; ca (UC39) và widget chấm công của dashboard (UC34).
 */
public interface HrReportRepository extends Repository<Attendance, Integer> {

    interface AttendanceRow {
        Integer getUserId();

        Instant getCheckIn();

        Instant getCheckOut();

        Long getWorkedMinutes();
    }

    interface AssignmentRow {
        Integer getEmployeeId();

        Integer getShiftId();

        Instant getPlannedStart();

        Instant getPlannedEnd();

        Boolean getIsReplacement();
    }

    interface EmployeeRow {
        Integer getUserId();

        String getFullName();

        String getRoleCode();
    }

    @Query("""
            select a.userId as userId, a.checkInTime as checkIn, a.checkOutTime as checkOut,
                   a.workedMinutes as workedMinutes
            from Attendance a
            where a.branchId = :branchId and a.checkInTime >= :start and a.checkInTime < :end
            order by a.checkInTime, a.id""")
    List<AttendanceRow> findAttendances(@Param("branchId") Integer branchId,
                                        @Param("start") Instant start, @Param("end") Instant end);

    @Query("""
            select sa.id.employeeId as employeeId, s.id as shiftId,
                   s.plannedStart as plannedStart, s.plannedEnd as plannedEnd,
                   sa.isReplacement as isReplacement
            from ShiftAssignment sa
            join sa.shift s
            where s.branchId = :branchId and s.shiftDate between :from and :to
            order by s.plannedStart, s.id""")
    List<AssignmentRow> findAssignments(@Param("branchId") Integer branchId,
                                        @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Nhân sự đang hoạt động của chi nhánh + những người có dữ liệu trong kỳ (kể cả đã nghỉ / điều chuyển). */
    @Query("""
            select u.id as userId, u.fullName as fullName, r.code as roleCode
            from User u
            join u.role r
            where (u.branchId = :branchId and u.status = :status) or u.id in :userIds
            order by u.fullName, u.id""")
    List<EmployeeRow> findEmployees(@Param("branchId") Integer branchId, @Param("status") UserStatus status,
                                    @Param("userIds") Collection<Integer> userIds);
}
