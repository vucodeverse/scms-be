package edu.fpt.sba.scms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "ApplyVoucherRequest", description = "Áp dụng mã khuyến mãi / voucher cho đơn hàng (UC17, BR-21)")
public record ApplyVoucherRequest(
        @Schema(description = "Mã voucher", example = "KHAITRUONG")
        @NotBlank(message = "Mã voucher không được để trống")
        String code
) {
}
