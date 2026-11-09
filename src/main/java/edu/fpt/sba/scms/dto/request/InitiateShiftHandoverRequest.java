package edu.fpt.sba.scms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(name = "InitiateShiftHandoverRequest", description = "Khởi tạo bàn giao két giữa ca khi đổi thu ngân (tạm khóa POS & tự check-out người giao)")
public record InitiateShiftHandoverRequest(
        @Schema(description = "Số tiền mặt thực đếm được trong két tại thời điểm giao", example = "1250000")
        @NotNull(message = "Số tiền kiểm đếm khi bàn giao không được để trống")
        @DecimalMin(value = "0.0", message = "Số tiền kiểm đếm phải >= 0")
        BigDecimal countedCash,

        @Schema(description = "Lý do bàn giao giữa ca", example = "Thu ngân có việc gia đình đột xuất cần về sớm")
        @NotBlank(message = "Bắt buộc nhập lý do bàn giao két giữa ca")
        @Size(max = 500, message = "Lý do bàn giao tối đa 500 ký tự")
        String reason,

        @Schema(description = "Ghi chú thêm / giải trình chênh lệch tiền nếu lệch sổ sách", example = "Két khớp đủ tiền lẻ")
        @Size(max = 500, message = "Ghi chú bàn giao tối đa 500 ký tự")
        String note,

        @Schema(description = "Mã PIN cá nhân (4-6 số) của Thu ngân đang giao két (bắt buộc với Thu ngân; tùy chọn với Quản lý/COO)", example = "2468")
        @Pattern(regexp = "^$|^[0-9]{4,6}$", message = "Mã PIN phải gồm từ 4 đến 6 chữ số")
        String pin,

        @Schema(description = "ID nhân sự dự kiến nhận két (tùy chọn)", example = "5")
        Integer expectedIncomingUserId
) {
}
