package edu.fpt.sba.scms.dto.response;

import edu.fpt.sba.scms.common.enums.DiscountType;
import edu.fpt.sba.scms.common.enums.RecordStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

/** Dòng danh sách {@code GET /vouchers} (UC13) — kèm lượt dùng. */
@Schema(name = "VoucherDto")
public record VoucherDto(
        Integer id,
        String code,
        DiscountType discountType,
        BigDecimal discountValue,
        BigDecimal minOrderAmount,
        Instant startDate,
        Instant endDate,
        @Schema(description = "null = không giới hạn") Integer usageLimit,
        int usedCount,
        RecordStatus status,
        Instant createdAt,
        Instant updatedAt) {
}
