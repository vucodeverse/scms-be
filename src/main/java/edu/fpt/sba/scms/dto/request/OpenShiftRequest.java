package edu.fpt.sba.scms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(name = "OpenShiftRequest", description = "Mở ca làm việc & khai báo số dư két đầu ca (UC24, BR-36)")
public record OpenShiftRequest(
        @Schema(description = "ID chi nhánh", example = "1")
        @NotNull(message = "Chi nhánh không được để trống")
        Integer branchId,

        @Schema(description = "Số tiền mặt ban đầu trong két", example = "1000000")
        @NotNull(message = "Số tiền đầu ca không được để trống")
        @DecimalMin(value = "0.0", message = "Số tiền đầu ca phải >= 0")
        BigDecimal openingCash,

        @Schema(description = "ID ca đã lên lịch sẵn (nếu có)", example = "1")
        Integer scheduledShiftId,

        @Schema(description = "Ghi chú mở ca / giải trình chênh lệch đầu ca", example = "Mở ca sáng đầu ngày")
        @Size(max = 500, message = "Ghi chú mở ca tối đa 500 ký tự")
        String note
) {
}
