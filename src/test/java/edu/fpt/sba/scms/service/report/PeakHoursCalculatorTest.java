package edu.fpt.sba.scms.service.report;

import edu.fpt.sba.scms.dto.response.report.HourSlotDto;
import edu.fpt.sba.scms.repository.SalesReportRepository.UtcHourRow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PeakHoursCalculatorTest {

    private static UtcHourRow utc(int hour, long orders) {
        return Rows.of(UtcHourRow.class, "yr", 2026, "mon", 10, "dy", 15, "hr", hour, "orderCount", orders,
                "revenue", BigDecimal.valueOf(orders * 10_000));
    }

    @Test
    void du17SlotGioVnVaDanhDauNhomCaoNhat() {
        // UTC 1h = 08:00 VN; UTC 16h = 23:00 VN (ngoài giờ); UTC 22h = 05:00 VN (ngoài giờ).
        List<UtcHourRow> rows = List.of(utc(1, 10), utc(2, 8), utc(3, 7), utc(16, 5), utc(22, 4));

        PeakHoursCalculator.PeakHours result = PeakHoursCalculator.calculate(rows, 6, 23, new BigDecimal("0.80"), 2);

        assertThat(result.slots()).hasSize(17);
        assertThat(result.slots().get(0).label()).isEqualTo("06:00-07:00");
        assertThat(result.slots().get(16).label()).isEqualTo("22:00-23:00");
        assertThat(result.totalOrders()).isEqualTo(25);
        assertThat(result.peakOrderCount()).isEqualTo(10);
        assertThat(result.slots()).filteredOn(HourSlotDto::peak).extracting(HourSlotDto::hour)
                .containsExactly(8, 9);
        HourSlotDto eight = result.slots().get(2);
        assertThat(eight.orderCount()).isEqualTo(10);
        assertThat(eight.revenue()).isEqualByComparingTo("100000");
        assertThat(eight.averageOrdersPerDay()).isEqualByComparingTo("5.00");
    }

    @Test
    void khongCoDonThiKhongCoGioCaoDiem() {
        PeakHoursCalculator.PeakHours result = PeakHoursCalculator.calculate(List.of(), 6, 23,
                new BigDecimal("0.80"), 1);

        assertThat(result.totalOrders()).isZero();
        assertThat(result.slots()).hasSize(17).noneMatch(HourSlotDto::peak);
    }
}
