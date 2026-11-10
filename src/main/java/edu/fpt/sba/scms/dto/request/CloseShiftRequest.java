package edu.fpt.sba.scms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
        String discrepancyReason,

        @Schema(description = "Mã PIN cá nhân (4-6 số) của Thu ngân để xác nhận đóng ca & tự động Check-out (tùy chọn với Quản lý/COO)", example = "2468")
        @Pattern(regexp = "^$|^[0-9]{4,6}$", message = "Mã PIN phải gồm từ 4 đến 6 chữ số")
        String pin,

        @Schema(description = "Lý do đóng ca sớm hơn 10 phút so với giờ dự kiến kết thúc (tùy chọn)", example = "Chi nhánh mất điện, xin đóng ca sớm")
        @Size(max = 500, message = "Lý do đóng ca sớm tối đa 500 ký tự")
        String earlyCloseReason
) {
    public CloseShiftRequest(BigDecimal countedCash, String discrepancyReason) {
        this(countedCash, discrepancyReason, null, null);
    }

    public CloseShiftRequest(BigDecimal countedCash, String discrepancyReason, String pin) {
        this(countedCash, discrepancyReason, pin, null);
    }
}
