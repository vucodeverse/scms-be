package edu.fpt.sba.scms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(name = "AcceptShiftHandoverRequest", description = "Kiểm két nhận ca & mở lại POS trên cùng phiên ca đang mở")
public record AcceptShiftHandoverRequest(
        @Schema(description = "Số tiền mặt người nhận thực đếm lại được trong két", example = "1250000")
        @NotNull(message = "Số tiền kiểm đếm nhận két không được để trống")
        @DecimalMin(value = "0.0", message = "Số tiền kiểm đếm phải >= 0")
        BigDecimal countedCash,

        @Schema(description = "Ghi chú / giải trình chênh lệch khi nhận két (bắt buộc nếu lệch so với số bàn giao)", example = "Đã kiểm đếm đủ")
        @Size(max = 500, message = "Ghi chú nhận két tối đa 500 ký tự")
        String note,

        @Schema(description = "Mã PIN cá nhân (4-6 số) của người nhận két", example = "1357")
        @Pattern(regexp = "^$|^[0-9]{4,6}$", message = "Mã PIN phải gồm từ 4 đến 6 chữ số")
        String pin,

        @Schema(description = "ID người nhận két (tùy chọn với Quản lý/COO khi bàn giao trực tiếp cho thu ngân khác hoặc tự đứng quầy)", example = "6")
        Integer incomingUserId
) {
}
