package edu.fpt.sba.scms.dto.response;

import edu.fpt.sba.scms.common.enums.PayPeriodStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Schema(name = "PayPeriodResponse", description = "Thông tin kỳ tính lương của chi nhánh")
public record PayPeriodResponse(
        Integer id,
        Integer branchId,
        String periodName,
        LocalDate startDate,
        LocalDate endDate,
        PayPeriodStatus status,
        BigDecimal totalAmount,
        Integer createdBy,
        Integer approvedBy,
        Instant approvedAt,
        String rejectionReason,
        Instant createdAt,
        int totalEmployees
) {
}

