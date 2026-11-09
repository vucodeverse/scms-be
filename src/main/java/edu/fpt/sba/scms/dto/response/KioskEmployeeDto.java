package edu.fpt.sba.scms.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "KioskEmployeeDto", description = "Danh sách nhân viên hiển thị trên Kiosk chấm công")
public record KioskEmployeeDto(
        Integer employeeId,
        String fullName,
        String role,
        @Schema(description = "true nếu nhân viên đang có ca làm chưa check-out")
        boolean isCheckedIn,
        @Schema(description = "true nếu nhân viên được phân công ca làm việc phù hợp để check-in/check-out")
        boolean hasAssignedShift,
        @Schema(description = "Nhãn ca làm việc được gán (vd: Ca chiều · 14:30–22:30), null nếu chưa phân công")
        String shiftLabel,
        @Schema(description = "Trạng thái ca phân công: OPEN, SCHEDULED, CLOSED hoặc null")
        String shiftStatus,
        @Schema(description = "true nếu PIN của nhân viên đang bị tạm khóa do nhập sai nhiều lần")
        boolean pinLocked,
        @Schema(description = "Thời điểm hết hạn khóa tạm PIN (null nếu không bị khóa)")
        java.time.Instant pinLockedUntil
) {
}
