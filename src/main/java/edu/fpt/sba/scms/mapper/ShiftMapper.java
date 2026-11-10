package edu.fpt.sba.scms.mapper;

import edu.fpt.sba.scms.dto.response.CashTransactionResponse;
import edu.fpt.sba.scms.dto.response.ShiftHandoverResponse;
import edu.fpt.sba.scms.dto.response.ShiftResponse;
import edu.fpt.sba.scms.dto.response.ShiftScheduleResponse;
import edu.fpt.sba.scms.model.CashTransaction;
import edu.fpt.sba.scms.model.Shift;
import edu.fpt.sba.scms.model.ShiftHandover;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;
import java.util.Map;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ShiftMapper {

    @Mapping(target = "branchName", ignore = true)
    @Mapping(target = "openedByName", ignore = true)
    @Mapping(target = "closedByName", ignore = true)
    @Mapping(target = "activeCashierName", ignore = true)
    @Mapping(target = "pendingHandover", ignore = true)
    @Mapping(target = "handoverPending", expression = "java(shift.isHandoverPending())")
    @Mapping(target = "earlyClosePending", expression = "java(shift.isEarlyClosePending())")
    @Mapping(target = "earlyCloseRequestedByName", ignore = true)
    ShiftResponse toShiftResponse(Shift shift);

    @Mapping(target = "createdByName", ignore = true)
    @Mapping(target = "approvedByName", ignore = true)
    CashTransactionResponse toCashTransactionResponse(CashTransaction tx);

    List<CashTransactionResponse> toCashTransactionResponses(List<CashTransaction> txList);

    @Mapping(target = "shiftId", source = "shift.id")
    @Mapping(target = "assignedEmployeeIds", source = "assignedEmployeeIds")
    @Mapping(target = "totalEmployees", expression = "java(assignedEmployeeIds != null ? assignedEmployeeIds.size() : 0)")
    @Mapping(target = "employeeRoles", source = "employeeRoles")
    @Mapping(target = "replacementEmployeeIds", source = "replacementEmployeeIds")
    ShiftScheduleResponse toShiftScheduleResponse(
            Shift shift,
            List<Integer> assignedEmployeeIds,
            Map<Integer, String> employeeRoles,
            List<Integer> replacementEmployeeIds);

    @Mapping(target = "fromUserName", ignore = true)
    @Mapping(target = "toUserName", ignore = true)
    @Mapping(target = "outgoingConfirmedByName", ignore = true)
    @Mapping(target = "incomingConfirmedByName", ignore = true)
    @Mapping(target = "cancelledByName", ignore = true)
    ShiftHandoverResponse toShiftHandoverResponse(ShiftHandover handover);
}
