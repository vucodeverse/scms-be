package edu.fpt.sba.scms.domain;

import edu.fpt.sba.scms.common.enums.ShiftType;
import edu.fpt.sba.scms.domain.shift.ShiftSchedulePolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;
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
        assertEquals(ShiftType.EVENING, ShiftSchedulePolicy.inferShiftType(LocalTime.of(15, 0)));
        assertEquals(ShiftType.NIGHT, ShiftSchedulePolicy.inferShiftType(LocalTime.of(22, 30)));
        assertEquals(ShiftType.MORNING, ShiftSchedulePolicy.inferShiftType(null));
    }
}
