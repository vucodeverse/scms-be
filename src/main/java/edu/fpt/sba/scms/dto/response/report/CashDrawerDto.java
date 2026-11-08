package edu.fpt.sba.scms.dto.response.report;

import edu.fpt.sba.scms.common.enums.ShiftType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Két tiền ca đang mở (UC34). {@code shiftOpen = false} khi chi nhánh chưa mở ca (các trường còn lại null).
 */
public record CashDrawerDto(
        boolean shiftOpen,
        Integer shiftId,
        ShiftType shiftType,
        Instant openedAt,
        BigDecimal openingCash,
        BigDecimal cashSales,
        BigDecimal cashIn,
        BigDecimal cashOut,
        BigDecimal cashRefunds,
        BigDecimal expectedCash) {

    public static CashDrawerDto noOpenShift() {
        return new CashDrawerDto(false, null, null, null, null, null, null, null, null, null);
    }
}
