package edu.fpt.sba.scms.domain;

import edu.fpt.sba.scms.common.enums.ShiftType;
import edu.fpt.sba.scms.domain.shift.ShiftSchedulePolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Domain: ShiftSchedulePolicy Unit Tests")
class ShiftSchedulePolicyTest {

    @Test
    @DisplayName("hasTimeOverlap: Phát hiện chính xác trùng lịch làm việc (BR-15)")
    void hasTimeOverlap_detectsConflicts() {
        Instant t0 = Instant.parse("2026-11-08T08:00:00Z");
        Instant t1 = t0.plus(4, ChronoUnit.HOURS); // 12:00
        Instant t2 = t0.plus(6, ChronoUnit.HOURS); // 14:00
        Instant t3 = t0.plus(8, ChronoUnit.HOURS); // 16:00

        // Ca 1: 08:00 - 14:00, Ca 2: 12:00 - 16:00 => Chồng lấn từ 12:00 đến 14:00
        assertTrue(ShiftSchedulePolicy.hasTimeOverlap(t0, t2, t1, t3));
        assertTrue(ShiftSchedulePolicy.hasTimeOverlap(t1, t3, t0, t2));

        // Ca nối tiếp liền kề: 08:00 - 12:00 và 12:00 - 16:00 => Không chồng lấn
        assertFalse(ShiftSchedulePolicy.hasTimeOverlap(t0, t1, t1, t3));
        assertFalse(ShiftSchedulePolicy.hasTimeOverlap(t1, t3, t0, t1));

        // Hai ca tách biệt hoàn toàn
        assertFalse(ShiftSchedulePolicy.hasTimeOverlap(t0, t1, t2, t3));

        // Ca con nằm hoàn toàn trong ca lớn
        assertTrue(ShiftSchedulePolicy.hasTimeOverlap(t0, t3, t1, t2));
    }

    @Test
    @DisplayName("isValidShiftTime: Kiểm tra thời gian bắt đầu trước kết thúc")
    void isValidShiftTime_validatesStartBeforeEnd() {
        Instant start = Instant.now();
        Instant end = start.plus(4, ChronoUnit.HOURS);

        assertTrue(ShiftSchedulePolicy.isValidShiftTime(start, end));
        assertFalse(ShiftSchedulePolicy.isValidShiftTime(end, start));
        assertFalse(ShiftSchedulePolicy.isValidShiftTime(start, start));
        assertFalse(ShiftSchedulePolicy.isValidShiftTime(null, end));
    }

    @Test
    @DisplayName("inferShiftType: Suy luận loại ca dựa trên khung giờ địa phương")
    void inferShiftType_infersCorrectType() {
        assertEquals(ShiftType.NIGHT, ShiftSchedulePolicy.inferShiftType(LocalTime.of(3, 0)));
        assertEquals(ShiftType.MORNING, ShiftSchedulePolicy.inferShiftType(LocalTime.of(7, 30)));
        assertEquals(ShiftType.MORNING, ShiftSchedulePolicy.inferShiftType(LocalTime.of(12, 29)));
        assertEquals(ShiftType.EVENING, ShiftSchedulePolicy.inferShiftType(LocalTime.of(12, 30)));
        assertEquals(ShiftType.EVENING, ShiftSchedulePolicy.inferShiftType(LocalTime.of(15, 0)));
        assertEquals(ShiftType.EVENING, ShiftSchedulePolicy.inferShiftType(LocalTime.of(17, 59)));
        assertEquals(ShiftType.NIGHT, ShiftSchedulePolicy.inferShiftType(LocalTime.of(18, 0)));
        assertEquals(ShiftType.NIGHT, ShiftSchedulePolicy.inferShiftType(LocalTime.of(22, 30)));
        assertEquals(ShiftType.MORNING, ShiftSchedulePolicy.inferShiftType(null));
    }

    @Test
    @DisplayName("resolveAdHocPlannedEnd: Trả đúng giờ kết thúc chuẩn theo ca hoặc +5h khi ngoài giờ")
    void resolveAdHocPlannedEnd_returnsExpectedEnd() {
        ZoneId vn = ZoneId.of("Asia/Ho_Chi_Minh");
        LocalDate day = LocalDate.of(2026, 11, 9);

        Instant morningNow = day.atTime(7, 15).atZone(vn).toInstant();
        assertEquals(day.atTime(12, 30).atZone(vn).toInstant(),
                ShiftSchedulePolicy.resolveAdHocPlannedEnd(ShiftType.MORNING, day, morningNow, vn));

        Instant eveningNow = day.atTime(13, 0).atZone(vn).toInstant();
        assertEquals(day.atTime(18, 0).atZone(vn).toInstant(),
                ShiftSchedulePolicy.resolveAdHocPlannedEnd(ShiftType.EVENING, day, eveningNow, vn));

        Instant nightNow = day.atTime(19, 0).atZone(vn).toInstant();
        assertEquals(day.atTime(23, 0).atZone(vn).toInstant(),
                ShiftSchedulePolicy.resolveAdHocPlannedEnd(ShiftType.NIGHT, day, nightNow, vn));

        Instant lateNightNow = day.atTime(23, 15).atZone(vn).toInstant();
        assertEquals(lateNightNow.plus(5, ChronoUnit.HOURS),
                ShiftSchedulePolicy.resolveAdHocPlannedEnd(ShiftType.NIGHT, day, lateNightNow, vn));
    }

    @Test
    @DisplayName("isPastScheduleDate: Chặn lịch ca thuộc ngày đã qua")
    void isPastScheduleDate_detectsPastDates() {
        LocalDate today = LocalDate.of(2026, 11, 10);
        assertTrue(ShiftSchedulePolicy.isPastScheduleDate(today.minusDays(1), today));
        assertFalse(ShiftSchedulePolicy.isPastScheduleDate(today, today));
        assertFalse(ShiftSchedulePolicy.isPastScheduleDate(today.plusDays(1), today));
    }

    @Test
    @DisplayName("isTooEarlyToOpen: Chỉ cho phép mở sớm tối đa 60 phút trước giờ bắt đầu")
    void isTooEarlyToOpen_enforces60MinuteWindow() {
        ZoneId vn = ZoneId.of("Asia/Ho_Chi_Minh");
        LocalDate today = LocalDate.of(2026, 11, 10);
        Instant eveningStart = today.atTime(12, 30).atZone(vn).toInstant();

        assertTrue(ShiftSchedulePolicy.isTooEarlyToOpen(today, eveningStart,
                today.atTime(11, 29).atZone(vn).toInstant(), today));
        assertFalse(ShiftSchedulePolicy.isTooEarlyToOpen(today, eveningStart,
                today.atTime(11, 30).atZone(vn).toInstant(), today));
        assertFalse(ShiftSchedulePolicy.isTooEarlyToOpen(today, eveningStart,
                today.atTime(13, 0).atZone(vn).toInstant(), today));

        LocalDate tomorrow = today.plusDays(1);
        assertTrue(ShiftSchedulePolicy.isTooEarlyToOpen(tomorrow, null,
                today.atTime(10, 0).atZone(vn).toInstant(), today));
    }

    @Test
    @DisplayName("isExpiredPastSchedule: Chặn mở lịch ca ngày đã qua đã quá giờ, nhưng giữ ca xuyên đêm đang diễn ra")
    void isExpiredPastSchedule_detectsExpiredPastShifts() {
        ZoneId vn = ZoneId.of("Asia/Ho_Chi_Minh");
        LocalDate today = LocalDate.of(2026, 11, 10);
        LocalDate yesterday = today.minusDays(1);

        Instant yesterdayEveningEnd = yesterday.atTime(18, 0).atZone(vn).toInstant();
        assertTrue(ShiftSchedulePolicy.isExpiredPastSchedule(yesterday, yesterdayEveningEnd,
                today.atTime(8, 0).atZone(vn).toInstant(), today));

        // Ca qua đêm từ 22:00 hôm qua tới 06:00 hôm nay, tại 01:00 hôm nay vẫn chưa hết hạn
        Instant overnightEnd = today.atTime(6, 0).atZone(vn).toInstant();
        assertFalse(ShiftSchedulePolicy.isExpiredPastSchedule(yesterday, overnightEnd,
                today.atTime(1, 0).atZone(vn).toInstant(), today));

        // Ca thuộc hôm nay mở muộn không bị coi là past-date expired
        assertFalse(ShiftSchedulePolicy.isExpiredPastSchedule(today, today.atTime(12, 30).atZone(vn).toInstant(),
                today.atTime(15, 0).atZone(vn).toInstant(), today));
    }

    @Test
    @DisplayName("isWithinShiftTypeWindow: Chỉ cho phép trong khung ca cùng ngày (vd Ca tối 18:00-23:00 nhận 20:00-22:00, chặn 22:00-21:00 hôm sau)")
    void isWithinShiftTypeWindow_enforcesSameDayShiftTypeBounds() {
        ZoneId vn = ZoneId.of("Asia/Ho_Chi_Minh");
        LocalDate day = LocalDate.of(2026, 11, 10);

        // Ca tối chuẩn 18:00-23:00 & rút gọn hợp lệ 20:00-22:00 -> true
        assertTrue(ShiftSchedulePolicy.isWithinShiftTypeWindow(day, ShiftType.NIGHT,
                day.atTime(18, 0).atZone(vn).toInstant(), day.atTime(23, 0).atZone(vn).toInstant(), vn));
        assertTrue(ShiftSchedulePolicy.isWithinShiftTypeWindow(day, ShiftType.NIGHT,
                day.atTime(20, 0).atZone(vn).toInstant(), day.atTime(22, 0).atZone(vn).toInstant(), vn));

        // 22:00 hôm nay -> 21:00 hôm sau -> chặn (qua ngày hôm sau)
        assertFalse(ShiftSchedulePolicy.isWithinShiftTypeWindow(day, ShiftType.NIGHT,
                day.atTime(22, 0).atZone(vn).toInstant(), day.plusDays(1).atTime(21, 0).atZone(vn).toInstant(), vn));

        // Ngoài biên Ca tối (< 18:00 hoặc > 23:00) -> chặn
        assertFalse(ShiftSchedulePolicy.isWithinShiftTypeWindow(day, ShiftType.NIGHT,
                day.atTime(17, 59).atZone(vn).toInstant(), day.atTime(22, 0).atZone(vn).toInstant(), vn));
        assertFalse(ShiftSchedulePolicy.isWithinShiftTypeWindow(day, ShiftType.NIGHT,
                day.atTime(19, 0).atZone(vn).toInstant(), day.atTime(23, 1).atZone(vn).toInstant(), vn));

        // Ca sáng 07:00-12:30 & Ca chiều 12:30-18:00
        assertTrue(ShiftSchedulePolicy.isWithinShiftTypeWindow(day, ShiftType.MORNING,
                day.atTime(8, 0).atZone(vn).toInstant(), day.atTime(12, 0).atZone(vn).toInstant(), vn));
        assertFalse(ShiftSchedulePolicy.isWithinShiftTypeWindow(day, ShiftType.MORNING,
                day.atTime(6, 59).atZone(vn).toInstant(), day.atTime(12, 0).atZone(vn).toInstant(), vn));
        assertFalse(ShiftSchedulePolicy.isWithinShiftTypeWindow(day, ShiftType.MORNING,
                day.atTime(8, 0).atZone(vn).toInstant(), day.atTime(13, 0).atZone(vn).toInstant(), vn));

        assertTrue(ShiftSchedulePolicy.isWithinShiftTypeWindow(day, ShiftType.EVENING,
                day.atTime(13, 0).atZone(vn).toInstant(), day.atTime(17, 30).atZone(vn).toInstant(), vn));
        assertFalse(ShiftSchedulePolicy.isWithinShiftTypeWindow(day, ShiftType.EVENING,
                day.atTime(12, 0).atZone(vn).toInstant(), day.atTime(17, 0).atZone(vn).toInstant(), vn));
        assertFalse(ShiftSchedulePolicy.isWithinShiftTypeWindow(day, ShiftType.EVENING,
                day.atTime(13, 0).atZone(vn).toInstant(), day.atTime(18, 1).atZone(vn).toInstant(), vn));
    }

    @Test
    @DisplayName("isEarlyClose: Chỉ yêu cầu duyệt khi đóng sớm hơn plannedEnd > 10 phút; trong 10 phút cuối hoặc overtime cho đóng ngay")
    void isEarlyClose_respects10MinuteGraceWindow() {
        ZoneId vn = ZoneId.of("Asia/Ho_Chi_Minh");
        LocalDate day = LocalDate.of(2026, 11, 10);
        Instant plannedEnd = day.atTime(12, 30).atZone(vn).toInstant();

        // 12:15 (còn 15 phút > 10 phút) -> đóng ca sớm
        assertTrue(ShiftSchedulePolicy.isEarlyClose(plannedEnd, day.atTime(12, 15).atZone(vn).toInstant()));
        // 12:19:59 (còn 10p01s) -> đóng ca sớm
        assertTrue(ShiftSchedulePolicy.isEarlyClose(plannedEnd, day.atTime(12, 19, 59).atZone(vn).toInstant()));

        // Đúng mốc 12:20 (còn đúng 10 phút) -> được đóng trực tiếp
        assertFalse(ShiftSchedulePolicy.isEarlyClose(plannedEnd, day.atTime(12, 20, 0).atZone(vn).toInstant()));
        // 12:25 (trong 10 phút cuối) -> được đóng trực tiếp
        assertFalse(ShiftSchedulePolicy.isEarlyClose(plannedEnd, day.atTime(12, 25).atZone(vn).toInstant()));
        // 12:40 (sau giờ kết thúc ca) -> được đóng trực tiếp
        assertFalse(ShiftSchedulePolicy.isEarlyClose(plannedEnd, day.atTime(12, 40).atZone(vn).toInstant()));

        // Thiếu plannedEnd hoặc now -> false
        assertFalse(ShiftSchedulePolicy.isEarlyClose(null, plannedEnd));
        assertFalse(ShiftSchedulePolicy.isEarlyClose(plannedEnd, null));
    }
}
