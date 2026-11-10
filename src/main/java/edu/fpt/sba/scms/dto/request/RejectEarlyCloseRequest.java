package edu.fpt.sba.scms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(name = "RejectEarlyCloseRequest", description = "Từ chối hoặc Thu ngân tự hủy yêu cầu đóng ca sớm để tiếp tục vận hành ca")
public record RejectEarlyCloseRequest(
        @Schema(description = "Lý do từ chối / mở lại ca (tùy chọn)", example = "Chưa đến giờ đóng ca, tiếp tục bán hàng tới 12:20")
        @Size(max = 500, message = "Lý do tối đa 500 ký tự")
        String reason
) {
}
