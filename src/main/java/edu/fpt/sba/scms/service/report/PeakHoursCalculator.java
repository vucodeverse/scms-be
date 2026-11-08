package edu.fpt.sba.scms.service.report;

import edu.fpt.sba.scms.dto.response.report.HourSlotDto;
import edu.fpt.sba.scms.repository.SalesReportRepository.UtcHourRow;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static edu.fpt.sba.scms.service.report.ReportMath.money;
import static edu.fpt.sba.scms.service.report.ReportMath.zeroIfNull;

/**
 * Phân bổ số đơn theo khung 1 giờ (giờ VN) trong giờ hoạt động và đánh dấu giờ cao điểm (UC42).
 * <ul>
 *   <li>BR-01/02: chỉ các slot {@code [openHour, closeHour)}, mỗi slot 1 giờ; đơn ngoài giờ không vào slot nào.</li>
 *   <li>BR-04: slot thuộc "nhóm cao nhất" khi {@code count >= peakTierRatio × max} (và {@code count > 0}).</li>
 * </ul>
 */
public final class PeakHoursCalculator {

    /** Kết quả: các slot đủ giờ hoạt động + tổng số đơn trong giờ hoạt động + số đơn của slot cao nhất. */
    public record PeakHours(List<HourSlotDto> slots, long totalOrders, long peakOrderCount) {
    }

    private PeakHoursCalculator() {
    }

    public static PeakHours calculate(List<UtcHourRow> rows, int openHour, int closeHour,
                                      BigDecimal peakTierRatio, long dayCount) {
        int slotCount = closeHour - openHour;
        long[] counts = new long[slotCount];
        BigDecimal[] revenues = new BigDecimal[slotCount];
        Arrays.fill(revenues, BigDecimal.ZERO);
        for (UtcHourRow row : rows) {
            int vnHour = ReportMath.utcHourToVn(row.getYr(), row.getMon(), row.getDy(), row.getHr()).getHour();
            if (vnHour < openHour || vnHour >= closeHour) {
                continue;
            }
            counts[vnHour - openHour] += zeroIfNull(row.getOrderCount());
            revenues[vnHour - openHour] = revenues[vnHour - openHour].add(zeroIfNull(row.getRevenue()));
        }

        long max = 0;
        long total = 0;
        for (long count : counts) {
            max = Math.max(max, count);
            total += count;
        }
        BigDecimal threshold = peakTierRatio.multiply(BigDecimal.valueOf(max));
        BigDecimal days = BigDecimal.valueOf(Math.max(dayCount, 1));

        List<HourSlotDto> slots = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++) {
            int hour = openHour + i;
            boolean peak = counts[i] > 0 && BigDecimal.valueOf(counts[i]).compareTo(threshold) >= 0;
            slots.add(new HourSlotDto(hour, label(hour), counts[i], money(revenues[i]),
                    ReportMath.divide(BigDecimal.valueOf(counts[i]), days), peak));
        }
        return new PeakHours(slots, total, max);
    }

    /** "06:00-07:00". */
    static String label(int hour) {
        return "%02d:00-%02d:00".formatted(hour, hour + 1);
    }
}
