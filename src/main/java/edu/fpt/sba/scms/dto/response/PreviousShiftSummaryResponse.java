package edu.fpt.sba.scms.dto.response;

import edu.fpt.sba.scms.common.enums.ShiftType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Schema(name = "PreviousShiftSummaryResponse", description = "Thông tin ca đã đóng gần nhất và các khoản nạp đầu ngày của chi nhánh để đối soát két đầu ca")
public record PreviousShiftSummaryResponse(
        boolean hasPreviousShift,
        Integer shiftId,
        LocalDate shiftDate,
        ShiftType shiftType,
        Instant closedAt,
        Integer closedBy,
        String closedByName,
        BigDecimal expectedCash,
        BigDecimal countedCash,
        BigDecimal variance,
        BigDecimal preOpenCashIn,
        BigDecimal expectedOpeningCash,
        List<PreOpenCashAdjustmentResponse> preOpenAdjustments
) {
    public static PreviousShiftSummaryResponse none() {
        return new PreviousShiftSummaryResponse(
                false, null, null, null, null, null, null, null, null, null,
                BigDecimal.ZERO, null, List.of());
    }
}
