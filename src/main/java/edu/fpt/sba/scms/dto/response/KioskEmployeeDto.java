package edu.fpt.sba.scms.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "KioskEmployeeDto", description = "Danh sách nhân viên hiển thị trên Kiosk chấm công")
public record KioskEmployeeDto(
        Integer employeeId,
        String fullName,
        String role,
        @Schema(description = "true nếu nhân viên đang có ca làm chưa check-out")
        boolean isCheckedIn
) {
}

