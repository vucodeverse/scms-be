package edu.fpt.sba.scms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Định dạng payload Webhook gửi từ cổng thanh toán SePay (UC44, BR-29).
 */
@Schema(name = "SepayWebhookRequest", description = "Dữ liệu webhook biến động số dư ngân hàng từ SePay")
public record SepayWebhookRequest(
        @Schema(description = "ID giao dịch trên hệ thống SePay", example = "1234567")
        Long id,

        @Schema(description = "Tên cổng/ngân hàng nhận", example = "MBBank")
        String gateway,

        @Schema(description = "Thời gian xảy ra giao dịch", example = "2026-11-06 13:20:00")
        String transactionDate,

        @Schema(description = "Số tài khoản ngân hàng nhận tiền", example = "0987654321")
        String accountNumber,

        @Schema(description = "Tài khoản phụ (nếu có)")
        String subAccount,

        @Schema(description = "Mã code giao dịch (nếu có)")
        String code,

        @Schema(description = "Nội dung chuyển khoản (chứa mã tham chiếu CAFE...)", example = "CAFE1001 chuyen tien")
        String content,

        @Schema(description = "Loại giao dịch (in: tiền vào, out: tiền ra)", example = "in")
        String transferType,

        @Schema(description = "Mô tả chi tiết giao dịch từ sao kê", example = "MBVCB.123456.CAFE1001")
        String description,

        @Schema(description = "Số tiền chuyển khoản", example = "55000")
        BigDecimal transferAmount,

        @Schema(description = "Mã tham chiếu ngân hàng", example = "FT2611060001")
        String referenceCode,

        @Schema(description = "Số dư lũy kế sau giao dịch")
        BigDecimal accumulated
) {
}

