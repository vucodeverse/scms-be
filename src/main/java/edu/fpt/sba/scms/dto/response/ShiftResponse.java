package edu.fpt.sba.scms.dto.response;

import edu.fpt.sba.scms.common.enums.ShiftStatus;
import edu.fpt.sba.scms.common.enums.ShiftType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Schema(name = "ShiftResponse", description = "Thông tin ca làm việc / phiên két tiền")
public record ShiftResponse(
        Integer id,
        Integer branchId,
        LocalDate shiftDate,
        ShiftType shiftType,
        Instant plannedStart,
        Instant plannedEnd,
        Integer openedBy,
        Instant openedAt,
        Integer closedBy,
        Instant closedAt,
        BigDecimal openingCash,
        BigDecimal cashRevenue,
        BigDecimal transferRevenue,
        BigDecimal expectedCash,
        BigDecimal countedCash,
        BigDecimal variance,
        String discrepancyReason,
        ShiftStatus status,
        Instant createdAt,
        String branchName,
        String openedByName,
        String closedByName,
        Integer previousShiftId,
        BigDecimal previousClosingCash,
        BigDecimal openingVariance,
        String openingNote
) {
}
