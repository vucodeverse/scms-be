package edu.fpt.sba.scms.service.report;

import edu.fpt.sba.scms.common.exception.InvalidFieldValueException;
import edu.fpt.sba.scms.common.util.DateTimeUtil;
import edu.fpt.sba.scms.dto.request.query.report.ReportPeriodFilter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * Khoảng ngày của một report, tính theo <b>giờ VN</b>, đóng hai đầu {@code [from, to]}.
 * <p>
 * DB lưu thời điểm theo UTC ({@code serverTimezone=UTC}, {@code hibernate.jdbc.time_zone=UTC}), nên mọi truy vấn
 * dùng khoảng <b>nửa mở</b> {@code [startInclusive, endExclusive)} bằng {@link Instant}:
 * {@code from 00:00 VN} tới {@code (to + 1) 00:00 VN}. Ví dụ ngày 2026-11-05 VN = {@code [2026-11-04T17:00Z, 2026-11-05T17:00Z)}.
 * <p>
 * Mặc định khi thiếu tham số: cả hai trống → hôm nay (UC37 "defaults to current date"); thiếu một đầu → bằng đầu còn lại.
 */
public record ReportPeriod(LocalDate from, LocalDate to) {

    public static final String MSG_FROM_AFTER_TO = "'Từ ngày' không được lớn hơn 'Đến ngày'.";
    public static final String MSG_RANGE_TOO_LONG_FORMAT = "Khoảng thời gian báo cáo tối đa %d ngày.";

    public ReportPeriod {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.isAfter(to)) {
            throw new InvalidFieldValueException("from", MSG_FROM_AFTER_TO);
        }
    }

    /**
     * Dựng khoảng từ tham số client.
     *
     * @param maxRangeDays số ngày tối đa (bao gồm hai đầu) — {@code app.report.max-range-days}
     * @param today        "hôm nay" theo giờ VN; truyền vào để test không phụ thuộc đồng hồ máy
     * @throws InvalidFieldValueException 400 khi {@code from > to} (UC35 AT1) hoặc khoảng dài hơn {@code maxRangeDays}
     */
    public static ReportPeriod of(LocalDate from, LocalDate to, int maxRangeDays, LocalDate today) {
        Objects.requireNonNull(today, "today");
        LocalDate f = from != null ? from : (to != null ? to : today);
        LocalDate t = to != null ? to : f;
        ReportPeriod period = new ReportPeriod(f, t);
        if (period.dayCount() > maxRangeDays) {
            throw new InvalidFieldValueException("to", MSG_RANGE_TOO_LONG_FORMAT.formatted(maxRangeDays));
        }
        return period;
    }

    public static ReportPeriod of(ReportPeriodFilter filter, int maxRangeDays, LocalDate today) {
        return of(filter.from(), filter.to(), maxRangeDays, today);
    }

    /** Một ngày duy nhất (dashboard "hôm nay", so sánh "hôm qua"). */
    public static ReportPeriod ofDay(LocalDate day) {
        return new ReportPeriod(day, day);
    }

    /** {@code from 00:00} giờ VN, dưới dạng UTC instant (bao gồm). */
    public Instant startInclusive() {
        return from.atStartOfDay(DateTimeUtil.VN).toInstant();
    }

    /** {@code (to + 1) 00:00} giờ VN, dưới dạng UTC instant (không bao gồm). */
    public Instant endExclusive() {
        return to.plusDays(1).atStartOfDay(DateTimeUtil.VN).toInstant();
    }

    /** Số ngày trong khoảng, tính cả hai đầu (một ngày = 1). */
    public long dayCount() {
        return ChronoUnit.DAYS.between(from, to) + 1;
    }

    /** Mọi ngày trong khoảng theo thứ tự tăng dần — để điền đủ trục thời gian cho chart (ngày không có dữ liệu = 0). */
    public List<LocalDate> dates() {
        return from.datesUntil(to.plusDays(1)).toList();
    }

    /** Khoảng liền trước có cùng độ dài (vd. so sánh kỳ trước trên dashboard). */
    public ReportPeriod previous() {
        long days = dayCount();
        return new ReportPeriod(from.minusDays(days), from.minusDays(1));
    }

    /** {@code instant} có nằm trong {@code [startInclusive, endExclusive)} không. */
    public boolean contains(Instant instant) {
        return !instant.isBefore(startInclusive()) && instant.isBefore(endExclusive());
    }
}
