package edu.fpt.sba.scms.dto.request;

import edu.fpt.sba.scms.common.enums.ShiftType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Schema(name = "UpdateScheduleShiftRequest", description = "Cập nhật lịch ca làm việc và danh sách nhân viên phân công (UC08, BR-15)")
public record UpdateScheduleShiftRequest(
        @Schema(description = "Ngày làm việc", example = "2026-11-10")
        @NotNull(message = "Ngày làm việc không được để trống")
        LocalDate shiftDate,

        @Schema(description = "Loại ca làm việc", example = "MORNING")
        @NotNull(message = "Loại ca không được để trống")
        ShiftType shiftType,

        @Schema(description = "Thời gian bắt đầu dự kiến", example = "2026-11-10T00:00:00Z")
        @NotNull(message = "Thời gian bắt đầu không được để trống")
        Instant plannedStart,

        @Schema(description = "Thời gian kết thúc dự kiến", example = "2026-11-10T07:00:00Z")
        @NotNull(message = "Thời gian kết thúc không được để trống")
        Instant plannedEnd,

        @Schema(description = "Danh sách ID nhân viên phân công vào ca này", example = "[2, 3, 5]")
        List<Integer> assignedEmployeeIds,

        @Schema(description = "Vai trò kiêm nhiệm tạm thời theo ca (map employeeId -> roleCode; mặc định lấy chức danh gốc)")
        Map<Integer, String> employeeRoles
) {
    public UpdateScheduleShiftRequest {
        if (assignedEmployeeIds == null) {
            assignedEmployeeIds = List.of();
        }
        if (employeeRoles == null) {
            employeeRoles = Map.of();
        }
    }

    public UpdateScheduleShiftRequest(
            LocalDate shiftDate,
            ShiftType shiftType,
            Instant plannedStart,
            Instant plannedEnd,
            List<Integer> assignedEmployeeIds) {
        this(shiftDate, shiftType, plannedStart, plannedEnd, assignedEmployeeIds, Map.of());
    }
}
