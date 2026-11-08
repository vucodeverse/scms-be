package edu.fpt.sba.scms.service.report;

import edu.fpt.sba.scms.common.enums.CashReconcileStatus;
import edu.fpt.sba.scms.common.enums.CashTransactionType;
import edu.fpt.sba.scms.common.enums.ShiftStatus;
import edu.fpt.sba.scms.domain.shift.ShiftCashCalculator;
import edu.fpt.sba.scms.domain.shift.ShiftCashCalculator.CashAggregation;
import edu.fpt.sba.scms.domain.shift.ShiftCashCalculator.CashEntry;
import edu.fpt.sba.scms.dto.response.report.CashDrawerDto;
import edu.fpt.sba.scms.dto.response.report.CashFlowSummaryDto;
import edu.fpt.sba.scms.dto.response.report.CashMovementDto;
import edu.fpt.sba.scms.dto.response.report.ShiftCashFlowDto;
import edu.fpt.sba.scms.model.CashTransaction;
import edu.fpt.sba.scms.model.Shift;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static edu.fpt.sba.scms.service.report.ReportMath.money;
import static edu.fpt.sba.scms.service.report.ReportMath.zeroIfNull;

/**
 * Dòng tiền két theo ca (UC37), dùng lại công thức của domain ca ({@link ShiftCashCalculator}):
 * <ul>
 *   <li>BR-39: expected = opening + cashSales + cashIn − cashOut − cashRefunds; {@code cashSales} là
 *       {@code shift.cash_revenue} — doanh thu tiền mặt đơn PAID đã ghi vào ca (BR-27, UC37 BR-04).</li>
 *   <li>BR-38: variance = counted − expected, chỉ khi ca đã đóng và đã đếm tiền; ngược lại UNRECONCILED (AT2).</li>
 * </ul>
 */
public final class CashFlowCalculator {

    /** Biến động hiển thị trong chi tiết ca; OPEN/CLOSE chỉ là bút toán mở/đóng két. */
    private static final Set<CashTransactionType> MOVEMENT_TYPES =
            Set.of(CashTransactionType.CASH_IN, CashTransactionType.CASH_OUT, CashTransactionType.REFUND_OUT);

    private CashFlowCalculator() {
    }

    /** @param transactions các giao dịch tiền mặt của đúng ca này */
    public static ShiftCashFlowDto shift(Shift shift, List<CashTransaction> transactions) {
        CashAggregation totals = aggregate(transactions);
        BigDecimal expected = expectedCash(shift, totals);
        boolean reconciled = shift.getStatus() == ShiftStatus.CLOSED && shift.getCountedCash() != null;
        BigDecimal variance = reconciled ? money(ShiftCashCalculator.calculateVariance(shift.getCountedCash(), expected))
                : null;
        List<CashMovementDto> movements = transactions.stream()
                .filter(t -> MOVEMENT_TYPES.contains(t.getType()))
                .map(t -> new CashMovementDto(t.getType(), money(t.getAmount()), t.getReason(), t.getCreatedAt()))
                .toList();
        return new ShiftCashFlowDto(shift.getId(), shift.getShiftDate(), shift.getShiftType(), shift.getStatus(),
                shift.getOpenedAt(), shift.getClosedAt(),
                money(shift.getOpeningCash()), money(shift.getCashRevenue()),
                money(totals.totalIn()), money(totals.totalOut()), money(totals.totalRefund()), expected,
                reconciled ? money(shift.getCountedCash()) : null, variance,
                reconciled ? CashReconcileStatus.RECONCILED : CashReconcileStatus.UNRECONCILED,
                variance != null && variance.signum() != 0,
                shift.getDiscrepancyReason(), movements);
    }

    public static CashFlowSummaryDto summarize(List<ShiftCashFlowDto> shifts) {
        int unreconciled = 0;
        int withVariance = 0;
        BigDecimal opening = BigDecimal.ZERO;
        BigDecimal sales = BigDecimal.ZERO;
        BigDecimal in = BigDecimal.ZERO;
        BigDecimal out = BigDecimal.ZERO;
        BigDecimal refunds = BigDecimal.ZERO;
        BigDecimal expected = BigDecimal.ZERO;
        BigDecimal counted = BigDecimal.ZERO;
        BigDecimal variance = BigDecimal.ZERO;
        for (ShiftCashFlowDto s : shifts) {
            unreconciled += s.reconcileStatus() == CashReconcileStatus.UNRECONCILED ? 1 : 0;
            withVariance += s.hasVariance() ? 1 : 0;
            opening = opening.add(s.openingCash());
            sales = sales.add(s.cashSales());
            in = in.add(s.cashIn());
            out = out.add(s.cashOut());
            refunds = refunds.add(s.cashRefunds());
            expected = expected.add(s.expectedCash());
            counted = counted.add(zeroIfNull(s.countedCash()));
            variance = variance.add(zeroIfNull(s.variance()));
        }
        return new CashFlowSummaryDto(shifts.size(), unreconciled, withVariance, money(opening), money(sales),
                money(in), money(out), money(refunds), money(expected), money(counted), money(variance));
    }

    /** Két tiền ca đang mở cho dashboard (UC34). */
    public static CashDrawerDto drawer(Shift openShift, List<CashTransaction> transactions) {
        CashAggregation totals = aggregate(transactions);
        return new CashDrawerDto(true, openShift.getId(), openShift.getShiftType(), openShift.getOpenedAt(),
                money(openShift.getOpeningCash()), money(openShift.getCashRevenue()),
                money(totals.totalIn()), money(totals.totalOut()), money(totals.totalRefund()),
                expectedCash(openShift, totals));
    }

    private static CashAggregation aggregate(List<CashTransaction> transactions) {
        return ShiftCashCalculator.aggregate(transactions.stream()
                .map(t -> new CashEntry(t.getType(), t.getAmount()))
                .toList());
    }

    private static BigDecimal expectedCash(Shift shift, CashAggregation totals) {
        return money(ShiftCashCalculator.calculateExpectedCash(shift.getOpeningCash(), totals.totalIn(),
                totals.totalOut(), totals.totalRefund(), shift.getCashRevenue()));
    }
}
