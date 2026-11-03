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
}

