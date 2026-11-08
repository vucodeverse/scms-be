package edu.fpt.sba.scms.domain.shift;

import edu.fpt.sba.scms.common.enums.ShiftType;

import java.time.Instant;
import java.time.LocalTime;

/**
 * Quy tắc nghiệp vụ phân ca và kiểm tra xung đột lịch làm việc (Pure Java - Domain).
 * Hiện thực quy tắc nghiệp vụ BR-15.
 */
public final class ShiftSchedulePolicy {

    private ShiftSchedulePolicy() {
        // Utility class
    }

    /**
     * Kiểm tra xem 2 khoảng thời gian ca làm việc có bị chồng lấn (conflict) hay không (BR-15).
     * Hai khoảng [start1, end1] và [start2, end2] xung đột khi:
     * start1 < end2 && end1 > start2
     */
    public static boolean hasTimeOverlap(Instant start1, Instant end1, Instant start2, Instant end2) {
        if (start1 == null || end1 == null || start2 == null || end2 == null) {
            return false;
        }
        return start1.isBefore(end2) && end1.isAfter(start2);
    }

    /**
     * Kiểm tra tính hợp lệ của khung thời gian ca làm việc:
     * plannedStart phải trước plannedEnd.
     */
    public static boolean isValidShiftTime(Instant plannedStart, Instant plannedEnd) {
        if (plannedStart == null || plannedEnd == null) {
            return false;
        }
        return plannedStart.isBefore(plannedEnd);
    }

    /**
     * Suy luận loại ca làm việc dựa trên thời gian mở ca theo giờ địa phương.
     * Sáng (06:00 - 14:00) -> MORNING
     * Chiều/Tối (14:00 - 22:00) -> EVENING
     * Đêm (22:00 - 06:00) -> NIGHT
     */
    public static ShiftType inferShiftType(LocalTime time) {
        if (time == null) {
            return ShiftType.MORNING;
        }
        if (time.isBefore(LocalTime.of(6, 0))) {
            return ShiftType.NIGHT;
        }
        if (time.isBefore(LocalTime.of(14, 0))) {
            return ShiftType.MORNING;
        }
        if (time.isBefore(LocalTime.of(22, 0))) {
            return ShiftType.EVENING;
        }
        return ShiftType.NIGHT;
    }
}
