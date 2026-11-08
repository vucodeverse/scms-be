package edu.fpt.sba.scms.dto.response.report;

import edu.fpt.sba.scms.common.enums.ShiftType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Doanh số và tỷ trọng thanh toán (Cash / VietQR) của một ca (TAB-07). {@code shiftId = null}: đơn không gắn ca.
 */
public record ShiftSalesDto(
        Integer shiftId,
        LocalDate shiftDate,
        ShiftType shiftType,
        long orderCount,
        BigDecimal revenue,
        BigDecimal cashAmount,
        BigDecimal vietQrAmount,
        BigDecimal cashPercent,
        BigDecimal vietQrPercent,
        BigDecimal refundAmount) {
}
