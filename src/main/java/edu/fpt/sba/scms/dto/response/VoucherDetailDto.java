package edu.fpt.sba.scms.dto.response;

import edu.fpt.sba.scms.common.enums.DiscountType;
import edu.fpt.sba.scms.common.enums.RecordStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Chi tiết {@code GET /vouchers/{id}} (UC13): kèm phạm vi áp dụng (sản phẩm / danh mục). */
@Schema(name = "VoucherDetailDto")
public record VoucherDetailDto(
        Integer id,
        String code,
        DiscountType discountType,
        BigDecimal discountValue,
        BigDecimal minOrderAmount,
        Instant startDate,
        Instant endDate,
        Integer usageLimit,
        int usedCount,
        RecordStatus status,
        @Schema(description = "Rỗng = áp cho mọi sản phẩm") List<ProductRefDto> products,
        @Schema(description = "Rỗng = không giới hạn theo danh mục") List<CategoryRefDto> categories,
        Instant createdAt,
        Instant updatedAt) {
}
