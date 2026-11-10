package edu.fpt.sba.scms.common.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Nghiệp vụ ngày dùng Asia/Ho_Chi_Minh; DB/JVM chạy UTC (IMPLEMENTATION.md §8.6). */
public final class DateTimeUtil {

    public static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter HM_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private DateTimeUtil() {
    }

    public static LocalDate todayVN() {
        return LocalDate.now(VN);
    }

    public static LocalDate toVNDate(Instant instant) {
        return instant.atZone(VN).toLocalDate();
    }

    public static String formatHmVN(Instant instant) {
        return instant == null ? "--:--" : instant.atZone(VN).format(HM_FORMATTER);
    }
}
