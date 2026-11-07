package edu.fpt.sba.scms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(name = "CloseShiftRequest", description = "Đóng ca & kiểm kê tiền mặt két (UC27, BR-38, BR-39)")
public record CloseShiftRequest(
        @Schema(description = "Số tiền mặt thực tế kiểm đếm được trong két", example = "2450000")
        @NotNull(message = "Số tiền kiểm đếm thực tế không được để trống")
        @DecimalMin(value = "0.0", message = "Số tiền kiểm đếm phải >= 0")
        BigDecimal countedCash,

        @Schema(description = "Lý do giải trình chênh lệch (Bắt buộc theo BR-38 nếu lệch tiền)", example = "Thối nhầm 10k cho khách đơn #12")
        @Size(max = 500, message = "Lý do tối đa 500 ký tự")
        String discrepancyReason
) {
}

