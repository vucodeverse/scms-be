package edu.fpt.sba.scms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(name = "RejectManagerCloseRequest", description = "Quản lý chi nhánh / COO trả lại ca đang chờ nhận két để mở lại quầy POS")
public record RejectManagerCloseRequest(
        @Schema(description = "Lý do trả lại ca / mở lại POS", example = "Cần kiểm lại hóa đơn cuối ca trước khi chốt kết ngày")
        @Size(max = 500, message = "Lý do tối đa 500 ký tự")
        String reason
) {
}
