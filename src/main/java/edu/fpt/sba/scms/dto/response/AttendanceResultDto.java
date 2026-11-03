package edu.fpt.sba.scms.dto.response;

import edu.fpt.sba.scms.common.enums.AttendanceStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(name = "AttendanceResultDto", description = "Kết quả chấm công (UC03, UC04)")
public record AttendanceResultDto(
        Integer attendanceId,
        Integer employeeId,
        String employeeName,
        String role,
        Integer branchId,
        Instant checkInTime,
        Instant checkOutTime,
        Long workedMinutes,
        AttendanceStatus status,
        String message
) {
}

