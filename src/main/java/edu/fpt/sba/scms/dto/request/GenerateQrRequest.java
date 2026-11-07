package edu.fpt.sba.scms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(name = "GenerateQrRequest", description = "Yêu cầu tạo mã VietQR động cho đơn hàng (UC43)")
public record GenerateQrRequest(
        @Schema(description = "ID đơn hàng cần thanh toán", example = "1001")
        @NotNull(message = "ID đơn hàng không được để trống")
        Integer orderId
) {
}

