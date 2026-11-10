package edu.fpt.sba.scms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(name = "ApproveEarlyCloseRequest", description = "Quản lý chi nhánh / COO xác nhận duyệt đóng ca sớm & tự động Check-out Thu ngân")
public record ApproveEarlyCloseRequest(
        @Schema(description = "Số tiền mặt thực tế chốt đóng ca (nếu bỏ trống sẽ dùng đúng số Thu ngân đã khai báo)", example = "2450000")
        @DecimalMin(value = "0.0", message = "Số tiền kiểm đếm phải >= 0")
        BigDecimal countedCash,

        @Schema(description = "Giải trình chênh lệch tiền mặt két (BR-38)", example = "Khớp thực tế kiểm két cùng Quản lý")
        @Size(max = 500, message = "Lý do tối đa 500 ký tự")
        String discrepancyReason,

        @Schema(description = "Ghi chú phê duyệt của Quản lý chi nhánh (tùy chọn)", example = "Đã xác nhận đóng ca sớm do sự cố điện")
        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
        String approveNote,

        @Schema(description = "Mã PIN Quản lý xác nhận thêm (tùy chọn)", example = "654321")
        @Pattern(regexp = "^$|^[0-9]{4,6}$", message = "Mã PIN phải gồm từ 4 đến 6 chữ số")
        String pin
) {
}
