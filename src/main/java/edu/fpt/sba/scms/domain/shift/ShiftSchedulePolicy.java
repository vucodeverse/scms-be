package edu.fpt.sba.scms.domain.shift;

import edu.fpt.sba.scms.common.enums.ShiftType;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Quy tắc nghiệp vụ phân ca và kiểm tra xung đột lịch làm việc (Pure Java - Domain).
 * Hiện thực quy tắc nghiệp vụ BR-15.
 */
public final class ShiftSchedulePolicy {

    private static final LocalTime MORNING_START_BOUNDARY = LocalTime.of(5, 0);
    private static final LocalTime MORNING_END = LocalTime.of(12, 30);
    private static final LocalTime EVENING_END = LocalTime.of(18, 0);
    private static final LocalTime NIGHT_END = LocalTime.of(5, 0);
    private static final Duration AD_HOC_FALLBACK_DURATION = Duration.ofHours(5);

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
     * Ca sáng (05:00 – 12:30) -> MORNING
     * Ca chiều (12:30 – 18:00) -> EVENING
     * Ca tối (từ 18:00 đến đêm và 00:00 – 05:00 sáng hôm sau) -> NIGHT
     */
    public static ShiftType inferShiftType(LocalTime time) {
        if (time == null) {
            return ShiftType.MORNING;
        }
        if (time.isBefore(MORNING_START_BOUNDARY)) {
            return ShiftType.NIGHT;
        }
        if (time.isBefore(MORNING_END)) {
            return ShiftType.MORNING;
        }
        if (time.isBefore(EVENING_END)) {
            return ShiftType.EVENING;
        }
        return ShiftType.NIGHT;
    }

    /**
     * Tính thời điểm kết thúc dự kiến cho ca mở phát sinh:
     * Ca sáng -> 12:30, Ca chiều -> 18:00.
     * Ca tối -> 05:00 sáng (nếu mở sau 18:00 thì kết thúc 05:00 sáng hôm sau, nếu mở 00:00–05:00 thì kết thúc 05:00 cùng ngày).
     */
    public static Instant resolveAdHocPlannedEnd(ShiftType shiftType, LocalDate shiftDate, Instant now, ZoneId zone) {
        LocalTime localNow = ZonedDateTime.ofInstant(now, zone).toLocalTime();
        ShiftType resolvedType = shiftType != null ? shiftType : inferShiftType(localNow);
        if (resolvedType == ShiftType.NIGHT) {
            LocalDate targetDate = localNow.isBefore(MORNING_START_BOUNDARY) ? shiftDate : shiftDate.plusDays(1);
            Instant candidate = targetDate.atTime(NIGHT_END).atZone(zone).toInstant();
            return candidate.isAfter(now) ? candidate : now.plus(AD_HOC_FALLBACK_DURATION);
        }
        LocalTime targetEnd = switch (resolvedType) {
            case MORNING -> MORNING_END;
            case EVENING -> EVENING_END;
            default -> MORNING_END;
        };
        Instant candidate = shiftDate.atTime(targetEnd).atZone(zone).toInstant();
        return candidate.isAfter(now) ? candidate : now.plus(AD_HOC_FALLBACK_DURATION);
    }
}
