package edu.fpt.sba.scms.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(name = "PayrollItemResponse", description = "Phiếu lương chi tiết của nhân viên trong kỳ tính lương")
public record PayrollItemResponse(
        Integer id,
        Integer payPeriodId,
        Integer userId,
        String fullName,
        String role,
        BigDecimal totalHours,
        BigDecimal hourlyRate,
        BigDecimal baseSalary,
        BigDecimal bonus,
        BigDecimal deduction,
        BigDecimal netSalary,
        String note
) {
}

