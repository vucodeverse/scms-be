package edu.fpt.sba.scms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@Schema(name = "PinAttendanceRequest", description = "Yêu cầu chấm công qua PIN (UC03, UC04)")
public record PinAttendanceRequest(
        @Schema(description = "ID nhân viên (bắt buộc nếu chọn từ danh sách Kiosk; nếu để trống hệ thống sẽ tìm theo branchId + PIN)")
        Integer employeeId,

        @Schema(description = "Mã PIN 4-6 số", example = "1234")
        @NotBlank(message = "Mã PIN không được để trống")
        @Pattern(regexp = "^[0-9]{4,6}$", message = "Mã PIN phải từ 4 đến 6 chữ số")
        String pin,

        @Schema(description = "ID chi nhánh kiosk", example = "1")
        @NotNull(message = "Chi nhánh không được để trống")
        Integer branchId,

        @Schema(description = "Ghi chú (nếu có)")
        String notes
) {
}

