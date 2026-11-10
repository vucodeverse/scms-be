package edu.fpt.sba.scms.dto.response;

import edu.fpt.sba.scms.common.enums.ShiftStatus;
import edu.fpt.sba.scms.common.enums.ShiftType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Schema(name = "ShiftScheduleResponse", description = "Lịch ca làm việc và danh sách nhân viên phân công")
public record ShiftScheduleResponse(
        Integer shiftId,
        Integer branchId,
        LocalDate shiftDate,
        ShiftType shiftType,
        Instant plannedStart,
        Instant plannedEnd,
        ShiftStatus status,
        List<Integer> assignedEmployeeIds,
        int totalEmployees,
        Map<Integer, String> employeeRoles,
        List<Integer> replacementEmployeeIds
) {
}
