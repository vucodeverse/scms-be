package edu.fpt.sba.scms.common.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Nghiệp vụ ngày dùng Asia/Ho_Chi_Minh; DB/JVM chạy UTC (IMPLEMENTATION.md §8.6). */
public final class DateTimeUtil {

    public static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private DateTimeUtil() {
    }

    public static LocalDate todayVN() {
        return LocalDate.now(VN);
    }

    public static LocalDate toVNDate(Instant instant) {
        return instant.atZone(VN).toLocalDate();
    }
}
