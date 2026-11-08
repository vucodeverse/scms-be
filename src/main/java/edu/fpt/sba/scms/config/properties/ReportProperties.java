package edu.fpt.sba.scms.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Cấu hình module Reporting &amp; Analytics (UC34–UC42), prefix {@code app.report}.
 * <p>
 * Tách khỏi {@link AppProperties} để module report không sửa record dùng chung (và các test đang dựng
 * {@code new AppProperties(...)}). Được bật bởi {@code config.ReportConfig}.
 * <p>
 * Thiếu khoá nào thì dùng mặc định theo SRS; giá trị sai thì báo lỗi ngay lúc khởi động (compact constructor),
 * không chờ tới lúc gọi report.
 *
 * @param maxRangeDays     số ngày tối đa của một khoảng lọc {@code from..to} (bảo vệ DB)
 * @param openHour         giờ bắt đầu slot đầu tiên của Peak Hours (UC42 BR-01: 06:00)
 * @param closeHour        giờ kết thúc slot cuối (UC42 BR-01: 23:00), slot cuối = {@code [closeHour-1, closeHour)}
 * @param peakTierRatio    slot là "giờ cao điểm" khi {@code count >= ratio × max} (UC42 BR-04), trong (0, 1]
 * @param topProductsLimit số sản phẩm mặc định của báo cáo bán chạy (UC41 BR-03: top 10)
 * @param maxTopProducts   trần cho tham số {@code limit} client gửi lên
 * @param lateGraceMinutes số phút dung sai khi xét đi muộn / về sớm so với giờ ca dự kiến (UC39)
 */
@ConfigurationProperties(prefix = "app.report")
public record ReportProperties(
        Integer maxRangeDays,
        Integer openHour,
        Integer closeHour,
        BigDecimal peakTierRatio,
        Integer topProductsLimit,
        Integer maxTopProducts,
        Integer lateGraceMinutes) {

    public static final int DEFAULT_MAX_RANGE_DAYS = 366;
    public static final int DEFAULT_OPEN_HOUR = 6;
    public static final int DEFAULT_CLOSE_HOUR = 23;
    public static final BigDecimal DEFAULT_PEAK_TIER_RATIO = new BigDecimal("0.80");
    public static final int DEFAULT_TOP_PRODUCTS_LIMIT = 10;
    public static final int DEFAULT_MAX_TOP_PRODUCTS = 100;
    public static final int DEFAULT_LATE_GRACE_MINUTES = 5;

    public ReportProperties {
        maxRangeDays = maxRangeDays == null ? DEFAULT_MAX_RANGE_DAYS : maxRangeDays;
        openHour = openHour == null ? DEFAULT_OPEN_HOUR : openHour;
        closeHour = closeHour == null ? DEFAULT_CLOSE_HOUR : closeHour;
        peakTierRatio = peakTierRatio == null ? DEFAULT_PEAK_TIER_RATIO : peakTierRatio;
        topProductsLimit = topProductsLimit == null ? DEFAULT_TOP_PRODUCTS_LIMIT : topProductsLimit;
        maxTopProducts = maxTopProducts == null ? DEFAULT_MAX_TOP_PRODUCTS : maxTopProducts;
        lateGraceMinutes = lateGraceMinutes == null ? DEFAULT_LATE_GRACE_MINUTES : lateGraceMinutes;

        require(maxRangeDays >= 1, "app.report.max-range-days phải >= 1");
        require(openHour >= 0 && openHour <= 23, "app.report.open-hour phải trong [0, 23]");
        require(closeHour >= 1 && closeHour <= 24, "app.report.close-hour phải trong [1, 24]");
        require(openHour < closeHour, "app.report.open-hour phải nhỏ hơn close-hour");
        require(peakTierRatio.signum() > 0 && peakTierRatio.compareTo(BigDecimal.ONE) <= 0,
                "app.report.peak-tier-ratio phải trong (0, 1]");
        require(topProductsLimit >= 1, "app.report.top-products-limit phải >= 1");
        require(maxTopProducts >= topProductsLimit, "app.report.max-top-products phải >= top-products-limit");
        require(lateGraceMinutes >= 0, "app.report.late-grace-minutes phải >= 0");
    }

    /** Cấu hình mặc định theo SRS — dùng cho unit test không cần Spring. */
    public static ReportProperties defaults() {
        return new ReportProperties(null, null, null, null, null, null, null);
    }

    /** Số slot 1 giờ trong giờ hoạt động (mặc định 06–23 → 17 slot). */
    public int hourSlotCount() {
        return closeHour - openHour;
    }

    private static void require(boolean ok, String message) {
        if (!ok) {
            throw new IllegalArgumentException(message);
        }
    }
}
