package edu.fpt.sba.scms.dto.request;

import edu.fpt.sba.scms.common.enums.ShiftType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Schema(name = "CreateScheduleShiftRequest", description = "Tạo lịch ca làm việc và phân công nhân sự (UC08, BR-15)")
public record CreateScheduleShiftRequest(
        @Schema(description = "ID chi nhánh", example = "1")
        @NotNull(message = "Chi nhánh không được để trống")
        Integer branchId,

        @Schema(description = "Ngày làm việc", example = "2026-11-10")
        @NotNull(message = "Ngày làm việc không được để trống")
        LocalDate shiftDate,

        @Schema(description = "Loại ca làm việc", example = "MORNING")
        @NotNull(message = "Loại ca không được để trống")
        ShiftType shiftType,

        @Schema(description = "Thời gian bắt đầu dự kiến", example = "2026-11-10T07:00:00Z")
        @NotNull(message = "Thời gian bắt đầu không được để trống")
        Instant plannedStart,

        @Schema(description = "Thời gian kết thúc dự kiến", example = "2026-11-10T15:00:00Z")
        @NotNull(message = "Thời gian kết thúc không được để trống")
        Instant plannedEnd,

        @Schema(description = "Danh sách ID nhân viên phân công vào ca này", example = "[2, 3, 5]")
        List<Integer> assignedEmployeeIds
) {
    public CreateScheduleShiftRequest {
        if (assignedEmployeeIds == null) {
            assignedEmployeeIds = List.of();
        }
    }
}

