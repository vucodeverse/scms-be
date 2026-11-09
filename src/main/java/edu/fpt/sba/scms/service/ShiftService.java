package edu.fpt.sba.scms.service;

import edu.fpt.sba.scms.dto.request.CashAdjustmentRequest;
import edu.fpt.sba.scms.dto.request.CloseShiftRequest;
import edu.fpt.sba.scms.dto.request.CreateScheduleShiftRequest;
import edu.fpt.sba.scms.dto.request.OpenShiftRequest;
import edu.fpt.sba.scms.dto.request.UpdateScheduleShiftRequest;
import edu.fpt.sba.scms.dto.request.query.ShiftListQuery;
import edu.fpt.sba.scms.dto.response.BranchShiftOverviewResponse;
import edu.fpt.sba.scms.dto.response.CashTransactionResponse;
import edu.fpt.sba.scms.dto.response.PreviousShiftSummaryResponse;
import edu.fpt.sba.scms.dto.response.ShiftResponse;
import edu.fpt.sba.scms.dto.response.ShiftScheduleResponse;
import edu.fpt.sba.scms.dto.response.ShiftSummaryResponse;
import edu.fpt.sba.scms.security.principal.UserPrincipal;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.List;

/**
 * Quản lý ca làm việc & vận hành két tiền bán hàng (UC24, UC25, UC26, UC27, UC08).
 */
public interface ShiftService {

    ShiftResponse openShift(OpenShiftRequest request, UserPrincipal principal);

    CashTransactionResponse cashIn(Integer shiftId, CashAdjustmentRequest request, UserPrincipal principal);

    CashTransactionResponse cashOut(Integer shiftId, CashAdjustmentRequest request, UserPrincipal principal);

    ShiftResponse closeShift(Integer shiftId, CloseShiftRequest request, UserPrincipal principal);

    ShiftSummaryResponse getCurrentShiftSummary(Integer branchId);

    ShiftSummaryResponse getShiftSummary(Integer shiftId);

    PreviousShiftSummaryResponse getPreviousClosedShift(Integer branchId);

    List<BranchShiftOverviewResponse> getBranchesOverview();

    Page<ShiftResponse> listShifts(ShiftListQuery query);

    ShiftScheduleResponse createSchedule(CreateScheduleShiftRequest request, UserPrincipal principal);

    ShiftScheduleResponse updateSchedule(Integer shiftId, UpdateScheduleShiftRequest request, UserPrincipal principal);

    void deleteSchedule(Integer shiftId, UserPrincipal principal);

    List<ShiftScheduleResponse> getSchedules(Integer branchId, LocalDate startDate, LocalDate endDate);
}
