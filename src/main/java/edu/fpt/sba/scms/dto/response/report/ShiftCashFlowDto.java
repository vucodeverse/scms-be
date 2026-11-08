package edu.fpt.sba.scms.dto.response.report;

import edu.fpt.sba.scms.common.enums.CashReconcileStatus;
import edu.fpt.sba.scms.common.enums.ShiftStatus;
import edu.fpt.sba.scms.common.enums.ShiftType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Dòng tiền một ca (UC37): expected = opening + cashSales + cashIn - cashOut - cashRefunds (BR-39), variance = counted - expected (BR-38); ca chưa đối soát có {@code variance = null}.
 */
public record ShiftCashFlowDto(
        Integer shiftId,
        LocalDate shiftDate,
        ShiftType shiftType,
        ShiftStatus shiftStatus,
        Instant openedAt,
        Instant closedAt,
        BigDecimal openingCash,
        BigDecimal cashSales,
        BigDecimal cashIn,
        BigDecimal cashOut,
        BigDecimal cashRefunds,
        BigDecimal expectedCash,
        BigDecimal countedCash,
        BigDecimal variance,
        CashReconcileStatus reconcileStatus,
        boolean hasVariance,
        String discrepancyReason,
        List<CashMovementDto> movements) {
}
