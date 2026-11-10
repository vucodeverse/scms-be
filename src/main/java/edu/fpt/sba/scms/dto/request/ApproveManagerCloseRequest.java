package edu.fpt.sba.scms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(name = "ApproveManagerCloseRequest", description = "Quản lý chi nhánh / COO xác nhận nhận két cuối ca từ Thu ngân, tùy chọn rút tiền cất két Quản lý và đóng ca")
public record ApproveManagerCloseRequest(
        @Schema(description = "Số tiền mặt Quản lý kiểm đếm thực tế khi nhận két (nếu bỏ trống sẽ dùng đúng số Thu ngân đã bàn giao)", example = "5000000")
        @DecimalMin(value = "0.0", message = "Số tiền kiểm đếm phải >= 0")
        BigDecimal countedCash,

        @Schema(description = "Giải trình chênh lệch nếu Quản lý đếm khác số Thu ngân giao hoặc khác sổ sách", example = "Khớp đúng tiền mặt thực tế trong két")
        @Size(max = 500, message = "Lý do tối đa 500 ký tự")
        String discrepancyReason,

        @Schema(description = "Số tiền Quản lý rút cất két Quản lý ngay sau khi kiểm nhận (0 đ nếu để nguyên tại két)", example = "3000000")
        @DecimalMin(value = "0.0", message = "Số tiền rút phải >= 0")
        BigDecimal withdrawAmount,

        @Schema(description = "Lý do rút cất két Quản lý (nếu rút > 0 đ)", example = "Rút tiền doanh thu cuối ngày cất két Quản lý")
        @Size(max = 500, message = "Lý do rút tối đa 500 ký tự")
        String withdrawReason,

        @Schema(description = "Ghi chú phê duyệt của Quản lý chi nhánh (tùy chọn)", example = "Đã kiểm két đủ 5tr, rút 3tr cất két")
        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
        String approveNote,

        @Schema(description = "Mã PIN Quản lý xác nhận thêm (tùy chọn)", example = "654321")
        @Pattern(regexp = "^$|^[0-9]{4,6}$", message = "Mã PIN phải gồm từ 4 đến 6 chữ số")
        String pin
) {
}
