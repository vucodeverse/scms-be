package edu.fpt.sba.scms.dto.response;

import edu.fpt.sba.scms.common.enums.ShiftType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Schema(name = "PreviousShiftSummaryResponse", description = "Thông tin ca đã đóng gần nhất của chi nhánh để đối soát két đầu ca")
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
        BigDecimal variance
) {
    public static PreviousShiftSummaryResponse none() {
        return new PreviousShiftSummaryResponse(false, null, null, null, null, null, null, null, null, null);
    }
}
