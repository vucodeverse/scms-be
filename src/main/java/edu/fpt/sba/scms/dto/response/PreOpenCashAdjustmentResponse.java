package edu.fpt.sba.scms.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(name = "PreOpenCashAdjustmentResponse", description = "Khoản Quản lý nạp thêm tiền vào két trước khi mở ca")
public record PreOpenCashAdjustmentResponse(
        Integer id,
        Integer branchId,
        BigDecimal amount,
        String reason,
        Integer createdBy,
        String createdByName,
        Integer approvedBy,
        String approvedByName,
        Integer appliedShiftId,
        Instant appliedAt,
        Instant cancelledAt,
        Instant createdAt
) {
}
