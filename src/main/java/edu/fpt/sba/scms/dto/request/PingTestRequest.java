package edu.fpt.sba.scms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "PingTestRequest", description = "Kiểm tra kết nối thử nghiệm cổng SePay Webhook (UC15)")
public record PingTestRequest(
        @Schema(description = "API Token kiểm tra (nếu bỏ trống sẽ lấy từ cấu hình đã lưu)", example = "sepay_sec_123456789")
        String apiToken,

        @Schema(description = "Webhook URL kiểm tra (nếu bỏ trống sẽ lấy từ cấu hình đã lưu)", example = "https://api.scms.vn/api/v1/webhooks/sepay")
        String webhookUrl
) {
}

