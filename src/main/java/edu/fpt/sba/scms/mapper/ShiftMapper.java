package edu.fpt.sba.scms.mapper;

import edu.fpt.sba.scms.dto.response.CashTransactionResponse;
import edu.fpt.sba.scms.dto.response.ShiftResponse;
import edu.fpt.sba.scms.dto.response.ShiftScheduleResponse;
import edu.fpt.sba.scms.model.CashTransaction;
import edu.fpt.sba.scms.model.Shift;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ShiftMapper {

    @Mapping(target = "branchName", ignore = true)
    @Mapping(target = "openedByName", ignore = true)
    @Mapping(target = "closedByName", ignore = true)
    ShiftResponse toShiftResponse(Shift shift);

    @Mapping(target = "createdByName", ignore = true)
    @Mapping(target = "approvedByName", ignore = true)
    CashTransactionResponse toCashTransactionResponse(CashTransaction tx);

    List<CashTransactionResponse> toCashTransactionResponses(List<CashTransaction> txList);

    @Mapping(target = "shiftId", source = "shift.id")
    @Mapping(target = "assignedEmployeeIds", source = "assignedEmployeeIds")
    @Mapping(target = "totalEmployees", expression = "java(assignedEmployeeIds != null ? assignedEmployeeIds.size() : 0)")
    ShiftScheduleResponse toShiftScheduleResponse(Shift shift, List<Integer> assignedEmployeeIds);
}
