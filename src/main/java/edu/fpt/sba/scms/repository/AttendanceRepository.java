package edu.fpt.sba.scms.repository;

import edu.fpt.sba.scms.model.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance, Integer> {

    /**
     * Tìm lượt check-in mở gần nhất (chưa check-out) của nhân viên.
     */
    Optional<Attendance> findFirstByUserIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(Integer userId);

    /**
     * Tìm lượt check-in mở của nhân viên gắn với ca cụ thể.
     */
    Optional<Attendance> findFirstByUserIdAndShiftIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(Integer userId, Integer shiftId);

    /**
     * Tìm lượt chấm công gần nhất của nhân viên gắn với ca cụ thể.
     */
    Optional<Attendance> findFirstByUserIdAndShiftIdOrderByCheckInTimeDesc(Integer userId, Integer shiftId);

    /**
     * Danh sách tất cả lượt check-in đang mở của một ca làm việc.
     */
    List<Attendance> findAllByShiftIdAndCheckOutTimeIsNull(Integer shiftId);

    /**
     * Danh sách tất cả lượt check-in đang mở trên toàn hệ thống (phục vụ tự động quét quên check-out).
     */
    List<Attendance> findAllByCheckOutTimeIsNull();

    /**
     * Danh sách tất cả lượt check-in đang mở tại một chi nhánh.
     */
    List<Attendance> findAllByBranchIdAndCheckOutTimeIsNull(Integer branchId);

    /**
     * Lịch sử chấm công của một nhân viên.
     */
    List<Attendance> findAllByUserIdOrderByCheckInTimeDesc(Integer userId);

    /**
     * Lịch sử chấm công của một chi nhánh (kèm thông tin user).
     */
    @Query("select a from Attendance a join fetch a.user where a.branchId = :branchId order by a.checkInTime desc")
    List<Attendance> findAllWithUserByBranchId(@Param("branchId") Integer branchId);

    /**
     * Danh sách chấm công đã hoàn tất trong khoảng thời gian phục vụ tính lương (UC45).
     */
    @Query("select a from Attendance a where a.branchId = :branchId and a.checkInTime >= :startTime and a.checkInTime <= :endTime and a.checkOutTime is not null")
    List<Attendance> findCompletedAttendancesInPeriod(
            @Param("branchId") Integer branchId,
            @Param("startTime") java.time.Instant startTime,
            @Param("endTime") java.time.Instant endTime);
}

