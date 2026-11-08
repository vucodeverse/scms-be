package edu.fpt.sba.scms.service.report;

import edu.fpt.sba.scms.common.enums.PaymentMethod;
import edu.fpt.sba.scms.common.enums.ShiftType;
import edu.fpt.sba.scms.dto.response.report.DailySalesDto;
import edu.fpt.sba.scms.dto.response.report.SalesKpiDto;
import edu.fpt.sba.scms.dto.response.report.SalesSummaryDto;
import edu.fpt.sba.scms.dto.response.report.ShiftSalesDto;
import edu.fpt.sba.scms.model.Shift;
import edu.fpt.sba.scms.repository.SalesReportRepository.ShiftMethodRefundRow;
import edu.fpt.sba.scms.repository.SalesReportRepository.ShiftMethodSalesRow;
import edu.fpt.sba.scms.repository.SalesReportRepository.UtcHourRow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SalesReportCalculatorTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 15);

    private static ShiftMethodSalesRow sales(Integer shiftId, PaymentMethod method, long orders, String gross,
                                             String discount, String revenue) {
        return Rows.of(ShiftMethodSalesRow.class, "shiftId", shiftId, "method", method, "orderCount", orders,
                "grossAmount", new BigDecimal(gross), "discountAmount", new BigDecimal(discount),
                "revenue", new BigDecimal(revenue));
    }

    private static ShiftMethodRefundRow refund(Integer shiftId, PaymentMethod method, String amount) {
        return Rows.of(ShiftMethodRefundRow.class, "shiftId", shiftId, "method", method,
                "amount", new BigDecimal(amount));
    }

    private static UtcHourRow hour(int y, int m, int d, int h, long orders, String revenue) {
        return Rows.of(UtcHourRow.class, "yr", y, "mon", m, "dy", d, "hr", h, "orderCount", orders,
                "revenue", new BigDecimal(revenue));
    }

    private final List<ShiftMethodSalesRow> rows = List.of(
            sales(1, PaymentMethod.CASH, 1, "100000", "0", "100000"),
            sales(1, PaymentMethod.VIETQR, 1, "80000", "10000", "70000"),
            sales(null, PaymentMethod.CASH, 1, "45000", "0", "45000"));
    private final List<ShiftMethodRefundRow> refunds = List.of(refund(1, PaymentMethod.VIETQR, "10000"));

    @Test
    void tongHopDoanhThuThuanTruHoanTien() {
        SalesSummaryDto s = SalesReportCalculator.summarize(rows, refunds);

        assertThat(s.orderCount()).isEqualTo(3);
        assertThat(s.grossAmount()).isEqualByComparingTo("225000");
        assertThat(s.discountAmount()).isEqualByComparingTo("10000");
        assertThat(s.revenue()).isEqualByComparingTo("215000");
        assertThat(s.refundAmount()).isEqualByComparingTo("10000");
        assertThat(s.netRevenue()).isEqualByComparingTo("205000");
        assertThat(s.averageOrderValue()).isEqualByComparingTo("68333.33");
    }

    @Test
    void khongCoDonThiGiaTriTrungBinhBang0() {
        SalesSummaryDto s = SalesReportCalculator.summarize(List.of(), List.of());

        assertThat(s.orderCount()).isZero();
        assertThat(s.averageOrderValue()).isEqualByComparingTo("0");
    }

    @Test
    void paymentMixTheoCaVaDonKhongGanCaNamCuoi() {
        Shift shift = Shift.builder().id(1).shiftDate(DAY).shiftType(ShiftType.MORNING).build();

        List<ShiftSalesDto> result = SalesReportCalculator.byShift(rows, refunds, Map.of(1, shift));

        assertThat(result).hasSize(2);
        ShiftSalesDto first = result.get(0);
        assertThat(first.shiftId()).isEqualTo(1);
        assertThat(first.shiftType()).isEqualTo(ShiftType.MORNING);
        assertThat(first.orderCount()).isEqualTo(2);
        assertThat(first.revenue()).isEqualByComparingTo("170000");
        assertThat(first.cashPercent()).isEqualByComparingTo("58.82");
        assertThat(first.vietQrPercent()).isEqualByComparingTo("41.18");
        assertThat(first.refundAmount()).isEqualByComparingTo("10000");
        assertThat(result.get(1).shiftId()).isNull();
        assertThat(result.get(1).cashAmount()).isEqualByComparingTo("45000");
    }

    @Test
    void chuoiNgayTheoGioVnVaDuMoiNgay() {
        // 17:30Z ngày 14 = 00:30 VN ngày 15; 16:00Z ngày 15 = 23:00 VN ngày 15.
        List<UtcHourRow> hours = List.of(hour(2026, 10, 14, 17, 2, "50000"), hour(2026, 10, 15, 16, 1, "20000"));
        ReportPeriod period = new ReportPeriod(DAY.minusDays(1), DAY);

        List<DailySalesDto> daily = SalesReportCalculator.daily(hours, period);

        assertThat(daily).extracting(DailySalesDto::date).containsExactly(DAY.minusDays(1), DAY);
        assertThat(daily.get(0).orderCount()).isZero();
        assertThat(daily.get(0).revenue()).isEqualByComparingTo("0");
        assertThat(daily.get(1).orderCount()).isEqualTo(3);
        assertThat(daily.get(1).revenue()).isEqualByComparingTo("70000");
    }

    @Test
    void kpiSoVoiHomQua() {
        List<DailySalesDto> days = List.of(
                new DailySalesDto(DAY.minusDays(1), 4, new BigDecimal("200000")),
                new DailySalesDto(DAY, 5, new BigDecimal("250000")));

        SalesKpiDto kpi = SalesReportCalculator.kpi(days);

        assertThat(kpi.orderCount()).isEqualTo(5);
        assertThat(kpi.averageOrderValue()).isEqualByComparingTo("50000");
        assertThat(kpi.orderChangePercent()).isEqualByComparingTo("25.00");
        assertThat(kpi.revenueChangePercent()).isEqualByComparingTo("25.00");
    }

    @Test
    void kpiHomQuaBang0ThiPhanTramNull() {
        List<DailySalesDto> days = List.of(
                new DailySalesDto(DAY.minusDays(1), 0, BigDecimal.ZERO),
                new DailySalesDto(DAY, 1, new BigDecimal("30000")));

        SalesKpiDto kpi = SalesReportCalculator.kpi(days);

        assertThat(kpi.revenueChangePercent()).isNull();
        assertThat(kpi.orderChangePercent()).isNull();
    }
}
