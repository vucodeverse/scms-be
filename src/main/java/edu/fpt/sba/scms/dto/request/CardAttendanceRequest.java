package edu.fpt.sba.scms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(name = "CardAttendanceRequest", description = "Yêu cầu chấm công qua quẹt thẻ RFID (UC03, UC04)")
public record CardAttendanceRequest(
        @Schema(description = "Mã thẻ RFID", example = "CARD-001")
        @NotBlank(message = "Mã thẻ không được để trống")
        String cardCode,

        @Schema(description = "ID chi nhánh kiosk", example = "1")
        @NotNull(message = "Chi nhánh không được để trống")
        Integer branchId,

        @Schema(description = "ID ca làm việc (tùy chọn; nếu để trống hệ thống sẽ tự phân giải ca phù hợp)")
        Integer shiftId,

        @Schema(description = "Ghi chú (nếu có)")
        String notes
) {
}

