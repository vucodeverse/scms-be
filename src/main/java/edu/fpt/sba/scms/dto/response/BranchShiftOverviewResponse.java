package edu.fpt.sba.scms.dto.response;

import edu.fpt.sba.scms.common.enums.BranchStatus;
import edu.fpt.sba.scms.common.enums.ShiftType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(name = "BranchShiftOverviewResponse", description = "Tổng quan trạng thái ca & két tiền từng chi nhánh cho COO")
public record BranchShiftOverviewResponse(
        Integer branchId,
        String branchCode,
        String branchName,
        BranchStatus branchStatus,
        boolean hasOpenShift,
        Integer openShiftId,
        ShiftType openShiftType,
        Instant openedAt,
        Integer openedBy,
        String openedByName,
        BigDecimal openingCash,
        BigDecimal cashRevenue,
        BigDecimal currentCashBalance,
        Integer previousClosedShiftId,
        BigDecimal previousClosingCash,
        Instant previousClosedAt,
        Integer activeCashierId,
        String activeCashierName,
        boolean handoverPending,
        boolean earlyClosePending,
        boolean managerClosePending,
        BigDecimal preOpenCashIn
) {
}
