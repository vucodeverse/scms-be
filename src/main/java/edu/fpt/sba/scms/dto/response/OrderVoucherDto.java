package edu.fpt.sba.scms.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(name = "OrderVoucherDto", description = "Thông tin voucher đã áp dụng cho đơn hàng")
public record OrderVoucherDto(
        Integer id,
        Integer voucherId,
        String voucherCode,
        BigDecimal discountAmountApplied
) {
}
