package edu.fpt.sba.scms.domain;

import edu.fpt.sba.scms.domain.attendance.AttendanceCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Domain: AttendanceCalculator Unit Tests")
class AttendanceCalculatorTest {

    @Test
    @DisplayName("calculateWorkedMinutes: Tính đúng số phút giữa check-in và check-out")
    void calculateWorkedMinutes_calculatesCorrectly() {
        Instant checkIn = Instant.now();
        Instant checkOut = checkIn.plus(75, ChronoUnit.MINUTES);

        assertEquals(75, AttendanceCalculator.calculateWorkedMinutes(checkIn, checkOut));
        assertEquals(0, AttendanceCalculator.calculateWorkedMinutes(null, checkOut));
        assertEquals(0, AttendanceCalculator.calculateWorkedMinutes(checkIn, null));
    }

    @Test
    @DisplayName("isMinIntervalSatisfied: Khoảng cách tối thiểu >= 15 phút (BR-07)")
    void isMinIntervalSatisfied_checksThreshold() {
        assertTrue(AttendanceCalculator.isMinIntervalSatisfied(15));
        assertTrue(AttendanceCalculator.isMinIntervalSatisfied(16));
        assertTrue(AttendanceCalculator.isMinIntervalSatisfied(480));
        assertFalse(AttendanceCalculator.isMinIntervalSatisfied(14));
        assertFalse(AttendanceCalculator.isMinIntervalSatisfied(0));
    }
}

