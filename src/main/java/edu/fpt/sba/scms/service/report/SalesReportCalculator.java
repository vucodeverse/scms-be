package edu.fpt.sba.scms.service.report;

import edu.fpt.sba.scms.common.enums.PaymentMethod;
import edu.fpt.sba.scms.dto.response.report.DailySalesDto;
import edu.fpt.sba.scms.dto.response.report.SalesKpiDto;
import edu.fpt.sba.scms.dto.response.report.SalesSummaryDto;
import edu.fpt.sba.scms.dto.response.report.ShiftSalesDto;
import edu.fpt.sba.scms.model.Shift;
import edu.fpt.sba.scms.repository.SalesReportRepository.ShiftMethodRefundRow;
import edu.fpt.sba.scms.repository.SalesReportRepository.ShiftMethodSalesRow;
import edu.fpt.sba.scms.repository.SalesReportRepository.UtcHourRow;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static edu.fpt.sba.scms.service.report.ReportMath.money;
import static edu.fpt.sba.scms.service.report.ReportMath.percent;
import static edu.fpt.sba.scms.service.report.ReportMath.zeroIfNull;

/**
 * Tính toán báo cáo doanh số (UC35) từ các dòng tổng hợp của DB. Hàm thuần — không truy cập DB.
 * <p>
 * Quy ước: {@code revenue} = Σ {@code final_amount} (tiền khách trả); {@code netRevenue} = revenue − hoàn tiền.
 */
public final class SalesReportCalculator {

    private SalesReportCalculator() {
    }

    public static SalesSummaryDto summarize(List<ShiftMethodSalesRow> sales, List<ShiftMethodRefundRow> refunds) {
        long orders = 0;
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal revenue = BigDecimal.ZERO;
        for (ShiftMethodSalesRow row : sales) {
            orders += zeroIfNull(row.getOrderCount());
            gross = gross.add(zeroIfNull(row.getGrossAmount()));
            discount = discount.add(zeroIfNull(row.getDiscountAmount()));
            revenue = revenue.add(zeroIfNull(row.getRevenue()));
        }
        BigDecimal refunded = refunds.stream().map(r -> zeroIfNull(r.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal net = revenue.subtract(refunded);
        BigDecimal average = orders == 0 ? BigDecimal.ZERO : ReportMath.divide(net, BigDecimal.valueOf(orders));
        return new SalesSummaryDto(orders, money(gross), money(discount), money(revenue), money(refunded),
                money(net), money(average));
    }

    /** Doanh số + tỷ trọng Cash/VietQR theo ca (TAB-07). Đơn không gắn ca gom vào dòng {@code shiftId = null} ở cuối. */
    public static List<ShiftSalesDto> byShift(List<ShiftMethodSalesRow> sales, List<ShiftMethodRefundRow> refunds,
                                              Map<Integer, Shift> shiftsById) {
        Map<Integer, ShiftAccumulator> byShift = new LinkedHashMap<>();
        for (ShiftMethodSalesRow row : sales) {
            byShift.computeIfAbsent(row.getShiftId(), id -> new ShiftAccumulator()).addSales(row);
        }
        for (ShiftMethodRefundRow row : refunds) {
            byShift.computeIfAbsent(row.getShiftId(), id -> new ShiftAccumulator()).addRefund(row.getAmount());
        }
        List<ShiftSalesDto> result = new ArrayList<>();
        byShift.forEach((shiftId, acc) -> result.add(acc.toDto(shiftId,
                shiftId == null ? null : shiftsById.get(shiftId))));
        result.sort(Comparator.comparing(ShiftSalesDto::shiftDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ShiftSalesDto::shiftId, Comparator.nullsLast(Comparator.naturalOrder())));
        return result;
    }

    /** Chuỗi doanh số theo ngày (giờ VN), đủ mọi ngày trong kỳ — ngày không có đơn = 0. */
    public static List<DailySalesDto> daily(List<UtcHourRow> hours, ReportPeriod period) {
        Map<LocalDate, long[]> counts = new HashMap<>();
        Map<LocalDate, BigDecimal> revenues = new HashMap<>();
        for (UtcHourRow row : hours) {
            LocalDate date = ReportMath.utcHourToVn(row.getYr(), row.getMon(), row.getDy(), row.getHr()).toLocalDate();
            counts.computeIfAbsent(date, d -> new long[1])[0] += zeroIfNull(row.getOrderCount());
            revenues.merge(date, zeroIfNull(row.getRevenue()), BigDecimal::add);
        }
        return period.dates().stream()
                .map(date -> new DailySalesDto(date, counts.getOrDefault(date, new long[1])[0],
                        money(revenues.get(date))))
                .toList();
    }

    /**
     * KPI dashboard (UC34) từ chuỗi ngày kết thúc ở hôm nay: hôm nay = phần tử cuối, hôm qua = phần tử kề cuối.
     */
    public static SalesKpiDto kpi(List<DailySalesDto> lastDays) {
        DailySalesDto today = lastDays.get(lastDays.size() - 1);
        DailySalesDto yesterday = lastDays.size() > 1 ? lastDays.get(lastDays.size() - 2)
                : new DailySalesDto(today.date().minusDays(1), 0, money(BigDecimal.ZERO));
        BigDecimal average = today.orderCount() == 0 ? BigDecimal.ZERO
                : ReportMath.divide(today.revenue(), BigDecimal.valueOf(today.orderCount()));
        return new SalesKpiDto(today.orderCount(), today.revenue(), money(average),
                yesterday.orderCount(), yesterday.revenue(),
                ReportMath.changePercent(BigDecimal.valueOf(today.orderCount()),
                        BigDecimal.valueOf(yesterday.orderCount())),
                ReportMath.changePercent(today.revenue(), yesterday.revenue()),
                lastDays);
    }

    /** Cộng dồn một ca; tách riêng để {@link #byShift} không lồng quá 2 cấp. */
    private static final class ShiftAccumulator {
        private long orders;
        private BigDecimal revenue = BigDecimal.ZERO;
        private BigDecimal cash = BigDecimal.ZERO;
        private BigDecimal vietQr = BigDecimal.ZERO;
        private BigDecimal refunds = BigDecimal.ZERO;

        void addSales(ShiftMethodSalesRow row) {
            BigDecimal amount = zeroIfNull(row.getRevenue());
            orders += zeroIfNull(row.getOrderCount());
            revenue = revenue.add(amount);
            if (row.getMethod() == PaymentMethod.CASH) {
                cash = cash.add(amount);
            }
            if (row.getMethod() == PaymentMethod.VIETQR) {
                vietQr = vietQr.add(amount);
            }
        }

        void addRefund(BigDecimal amount) {
            refunds = refunds.add(zeroIfNull(amount));
        }

        ShiftSalesDto toDto(Integer shiftId, Shift shift) {
            boolean known = Objects.nonNull(shift);
            return new ShiftSalesDto(shiftId,
                    known ? shift.getShiftDate() : null,
                    known ? shift.getShiftType() : null,
                    orders, money(revenue), money(cash), money(vietQr),
                    percent(cash, revenue), percent(vietQr, revenue), money(refunds));
        }
    }
}
