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

    private static final LocalTime MORNING_START_BOUNDARY = LocalTime.of(6, 0);
    public static final LocalTime MORNING_START = LocalTime.of(7, 0);
    public static final LocalTime MORNING_END = LocalTime.of(12, 30);
    public static final LocalTime EVENING_START = LocalTime.of(12, 30);
    public static final LocalTime EVENING_END = LocalTime.of(18, 0);
    public static final LocalTime NIGHT_START = LocalTime.of(18, 0);
    public static final LocalTime NIGHT_END = LocalTime.of(23, 0);
    private static final Duration AD_HOC_FALLBACK_DURATION = Duration.ofHours(5);
    public static final Duration EARLY_OPEN_GRACE = Duration.ofMinutes(60);
    public static final Duration EARLY_CLOSE_GRACE = Duration.ofMinutes(10);
    public static final Duration PAST_EXPIRE_GRACE = Duration.ofMinutes(30);

    private ShiftSchedulePolicy() {
        // Utility class
    }

    /**
     * Ngày làm việc đã qua so với ngày hiện tại thì không cho phép tạo/chỉnh sửa lịch phân ca.
     */
    public static boolean isPastScheduleDate(LocalDate shiftDate, LocalDate today) {
        if (shiftDate == null || today == null) {
            return false;
        }
        return shiftDate.isBefore(today);
    }

    /**
     * Ca đã lên lịch chỉ được mở sớm hơn giờ bắt đầu tối đa {@link #EARLY_OPEN_GRACE} (60 phút).
     * Nếu thiếu plannedStart thì chặn mọi lịch có ngày làm việc ở tương lai.
     */
    public static boolean isTooEarlyToOpen(LocalDate shiftDate, Instant plannedStart, Instant now, LocalDate today) {
        if (now == null) {
            return false;
        }
        if (plannedStart != null) {
            return now.isBefore(plannedStart.minus(EARLY_OPEN_GRACE));
        }
        return shiftDate != null && today != null && shiftDate.isAfter(today);
    }

    /**
     * Lịch ca thuộc ngày đã qua và đã vượt quá giờ kết thúc dự kiến + {@link #PAST_EXPIRE_GRACE} (30 phút)
     * thì coi như quá hạn, không thể mở mới nữa. (Ca trong hôm nay vẫn cho phép mở nếu mở muộn.)
     */
    public static boolean isExpiredPastSchedule(LocalDate shiftDate, Instant plannedEnd, Instant now, LocalDate today) {
        if (shiftDate == null || today == null || now == null) {
            return false;
        }
        if (!shiftDate.isBefore(today)) {
            return false;
        }
        return plannedEnd == null || now.isAfter(plannedEnd.plus(PAST_EXPIRE_GRACE));
    }

    /**
     * Đóng ca sớm khi thời điểm hiện tại sớm hơn {@code plannedEnd} nhiều hơn
     * {@link #EARLY_CLOSE_GRACE} (10 phút). Trong vòng 10 phút cuối ca hoặc sau giờ kết thúc dự kiến
     * thì được phép đóng ca trực tiếp.
     */
    public static boolean isEarlyClose(Instant plannedEnd, Instant now) {
        if (plannedEnd == null || now == null) {
            return false;
        }
        return now.isBefore(plannedEnd.minus(EARLY_CLOSE_GRACE));
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

    public static LocalTime windowStart(ShiftType shiftType) {
        if (shiftType == null) {
            return MORNING_START;
        }
        return switch (shiftType) {
            case MORNING -> MORNING_START;
            case EVENING -> EVENING_START;
            case NIGHT -> NIGHT_START;
        };
    }

    public static LocalTime windowEnd(ShiftType shiftType) {
        if (shiftType == null) {
            return MORNING_END;
        }
        return switch (shiftType) {
            case MORNING -> MORNING_END;
            case EVENING -> EVENING_END;
            case NIGHT -> NIGHT_END;
        };
    }

    public static String windowLabel(ShiftType shiftType) {
        if (shiftType == null) {
            return "07:00 – 12:30";
        }
        return switch (shiftType) {
            case MORNING -> "07:00 – 12:30";
            case EVENING -> "12:30 – 18:00";
            case NIGHT -> "18:00 – 23:00";
        };
    }

    /**
     * Khung giờ tạo/sửa lịch ca phải nằm trong cùng {@code shiftDate} theo múi giờ {@code zone}
     * (không qua ngày hôm sau), bắt đầu trước kết thúc và nằm gọn trong khung chuẩn của loại ca:
     * MORNING (07:00–12:30), EVENING (12:30–18:00), NIGHT (18:00–23:00).
     */
    public static boolean isWithinShiftTypeWindow(LocalDate shiftDate,
                                                  ShiftType shiftType,
                                                  Instant plannedStart,
                                                  Instant plannedEnd,
                                                  ZoneId zone) {
        if (shiftDate == null || shiftType == null || plannedStart == null || plannedEnd == null || zone == null) {
            return false;
        }
        if (!plannedStart.isBefore(plannedEnd)) {
            return false;
        }
        ZonedDateTime startZdt = ZonedDateTime.ofInstant(plannedStart, zone);
        ZonedDateTime endZdt = ZonedDateTime.ofInstant(plannedEnd, zone);
        if (!shiftDate.equals(startZdt.toLocalDate()) || !shiftDate.equals(endZdt.toLocalDate())) {
            return false;
        }
        LocalTime minStart = windowStart(shiftType);
        LocalTime maxEnd = windowEnd(shiftType);
        return !startZdt.toLocalTime().isBefore(minStart) && !endZdt.toLocalTime().isAfter(maxEnd);
    }

    /**
     * Suy luận loại ca làm việc dựa trên thời gian mở ca theo giờ địa phương.
     * Ca sáng (07:00 – 12:30, mở từ 06:00 đến trước 12:30) -> MORNING
     * Ca chiều (12:30 – 18:00, từ 12:30 đến trước 18:00) -> EVENING
     * Ca tối (18:00 – 23:00, từ 18:00 trở đi và đêm trước 06:00) -> NIGHT
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
     * Tính thời điểm kết thúc dự kiến cho ca mở phát sinh dựa trên khung giờ chuẩn của từng ca:
     * Ca sáng -> 12:30, Ca chiều -> 18:00, Ca tối -> 23:00 trong ngày nếu đang nằm trong giờ hoạt động;
     * trường hợp mở ngoài khung chuẩn (trước 06:00 hoặc từ 23:00 trở đi) dự kiến 5 giờ.
     */
    public static Instant resolveAdHocPlannedEnd(ShiftType shiftType, LocalDate shiftDate, Instant now, ZoneId zone) {
        LocalTime localNow = ZonedDateTime.ofInstant(now, zone).toLocalTime();
        if (localNow.isBefore(MORNING_START_BOUNDARY) || !localNow.isBefore(NIGHT_END)) {
            return now.plus(AD_HOC_FALLBACK_DURATION);
        }
        LocalTime targetEnd = switch (shiftType != null ? shiftType : ShiftType.MORNING) {
            case MORNING -> MORNING_END;
            case EVENING -> EVENING_END;
            case NIGHT -> NIGHT_END;
        };
        Instant candidate = shiftDate.atTime(targetEnd).atZone(zone).toInstant();
        return candidate.isAfter(now) ? candidate : now.plus(AD_HOC_FALLBACK_DURATION);
    }
}
