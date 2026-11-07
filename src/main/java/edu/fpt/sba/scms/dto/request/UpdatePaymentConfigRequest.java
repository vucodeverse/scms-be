package edu.fpt.sba.scms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "UpdatePaymentConfigRequest", description = "Cập nhật cấu hình cổng thanh toán SePay & tài khoản nhận tiền (UC15)")
public record UpdatePaymentConfigRequest(
        @Schema(description = "Tên ngân hàng thụ hưởng toàn chuỗi", example = "MBBank")
        @NotBlank(message = "Tên ngân hàng không được để trống")
        @Size(max = 50, message = "Tên ngân hàng tối đa 50 ký tự")
        String bankName,

        @Schema(description = "Số tài khoản nhận tiền", example = "0987654321")
        @NotBlank(message = "Số tài khoản không được để trống")
        @Pattern(regexp = "^[0-9A-Za-z]{6,20}$", message = "Số tài khoản từ 6 đến 20 ký tự chữ số")
        String accountNumber,

        @Schema(description = "Tên chủ tài khoản thụ hưởng", example = "CONG TY CP SCMS CAFE")
        @NotBlank(message = "Tên chủ tài khoản không được để trống")
        @Size(max = 100, message = "Tên chủ tài khoản tối đa 100 ký tự")
        String accountHolder,

        @Schema(description = "Tiền tố nhận diện chuyển khoản (Payment Reference)", example = "CAFE")
        @NotBlank(message = "Tiền tố nội dung chuyển khoản không được để trống")
        @Pattern(regexp = "^[A-Z0-9]{2,10}$", message = "Tiền tố gồm 2-10 ký tự chữ in hoa hoặc số")
        String paymentPrefix,

        @Schema(description = "API Token / Secret Key kết nối cổng SePay (để trống nếu giữ nguyên token cũ)", example = "sepay_sec_123456789")
        String apiToken,

        @Schema(description = "URL webhook tiếp nhận biến động số dư từ SePay", example = "https://api.scms.vn/api/v1/webhooks/sepay")
        @NotBlank(message = "Webhook URL không được để trống")
        @Size(max = 255, message = "Webhook URL tối đa 255 ký tự")
        String webhookUrl,

        @Schema(description = "Trạng thái kích hoạt cổng thanh toán SePay", example = "true")
        boolean active
) {
}

