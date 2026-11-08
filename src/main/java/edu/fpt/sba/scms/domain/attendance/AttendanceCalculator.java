package edu.fpt.sba.scms.domain.attendance;

import java.time.Duration;
import java.time.Instant;

/**
 * Thuật toán tính toán thời gian làm việc và kiểm tra khoảng cách ca chấm công (Pure Java - Domain).
 * Hiện thực quy tắc nghiệp vụ BR-07.
 */
public final class AttendanceCalculator {

    /**
     * Khoảng cách tối thiểu giữa lần Check-in và Check-out: 15 phút (BR-07).
     */
    public static final long MIN_CHECKOUT_INTERVAL_MINUTES = 15;

    private AttendanceCalculator() {
        // Utility class
    }

    /**
     * Tính tổng số phút làm việc giữa lúc check-in và check-out:
     * workedMinutes = max(0, Duration.between(checkIn, checkOut).toMinutes())
     */
    public static long calculateWorkedMinutes(Instant checkIn, Instant checkOut) {
        if (checkIn == null || checkOut == null) {
            return 0;
        }
        return Math.max(0, Duration.between(checkIn, checkOut).toMinutes());
    }

    /**
     * BR-07: Kiểm tra xem khoảng cách giữa Check-in và Check-out có đạt tối thiểu 15 phút hay không.
     */
    public static boolean isMinIntervalSatisfied(long workedMinutes) {
        return workedMinutes >= MIN_CHECKOUT_INTERVAL_MINUTES;
    }
}

