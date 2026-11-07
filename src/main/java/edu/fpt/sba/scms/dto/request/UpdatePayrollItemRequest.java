package edu.fpt.sba.scms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(name = "UpdatePayrollItemRequest", description = "Điều chỉnh thưởng / phạt cho nhân viên trong kỳ lương (UC45, BR-53)")
public record UpdatePayrollItemRequest(
        @Schema(description = "Tiền thưởng thêm", example = "500000")
        @NotNull(message = "Tiền thưởng không được để trống")
        @DecimalMin(value = "0.0", message = "Tiền thưởng phải >= 0")
        BigDecimal bonus,

        @Schema(description = "Tiền phạt / khấu trừ", example = "100000")
        @NotNull(message = "Tiền khấu trừ không được để trống")
        @DecimalMin(value = "0.0", message = "Tiền khấu trừ phải >= 0")
        BigDecimal deduction,

        @Schema(description = "Ghi chú điều chỉnh", example = "Thưởng hoàn thành xuất sắc chỉ tiêu")
        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
        String note
) {
}

