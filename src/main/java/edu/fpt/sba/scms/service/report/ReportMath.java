package edu.fpt.sba.scms.service.report;

import edu.fpt.sba.scms.common.util.DateTimeUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Phép tính dùng chung của các report: tiền, phần trăm, đổi bucket giờ UTC sang giờ VN.
 */
public final class ReportMath {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int MONEY_SCALE = 2;
    private static final int RATIO_SCALE = 2;

    private ReportMath() {
    }

    public static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public static long zeroIfNull(Long value) {
        return value == null ? 0L : value;
    }

    /** Làm tròn tiền về scale 2 (cột tiền của DB là {@code decimal(19,2)}). */
    public static BigDecimal money(BigDecimal value) {
        return zeroIfNull(value).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /** {@code part / whole × 100}, scale 2; {@code null} khi {@code whole = 0} (không chia được, FE hiển thị "—"). */
    public static BigDecimal percent(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.signum() == 0) {
            return null;
        }
        return zeroIfNull(part).multiply(HUNDRED).divide(whole, RATIO_SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal percent(long part, long whole) {
        return percent(BigDecimal.valueOf(part), BigDecimal.valueOf(whole));
    }

    /** Mức thay đổi so với kỳ trước, theo %; {@code null} khi kỳ trước = 0. */
    public static BigDecimal changePercent(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.signum() == 0) {
            return null;
        }
        return percent(zeroIfNull(current).subtract(previous), previous);
    }

    /** {@code numerator / denominator}, scale 2; {@code null} khi mẫu = 0. */
    public static BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.signum() == 0) {
            return null;
        }
        return zeroIfNull(numerator).divide(denominator, RATIO_SCALE, RoundingMode.HALF_UP);
    }

    /** Bucket giờ UTC (năm/tháng/ngày/giờ) do DB gom → thời điểm tương ứng theo giờ VN. */
    public static ZonedDateTime utcHourToVn(int year, int month, int day, int hour) {
        return LocalDateTime.of(year, month, day, hour, 0)
                .atOffset(ZoneOffset.UTC)
                .atZoneSameInstant(DateTimeUtil.VN);
    }
}
