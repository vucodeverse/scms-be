package edu.fpt.sba.scms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(name = "CreatePayPeriodRequest", description = "Tạo kỳ tính lương mới cho chi nhánh (UC45, BR-53)")
public record CreatePayPeriodRequest(
        @Schema(description = "ID chi nhánh", example = "1")
        @NotNull(message = "Chi nhánh không được để trống")
        Integer branchId,

        @Schema(description = "Tên kỳ tính lương", example = "Kỳ lương Tháng 11/2026")
        @NotBlank(message = "Tên kỳ tính lương là bắt buộc")
        @Size(max = 100, message = "Tên kỳ lương tối đa 100 ký tự")
        String periodName,

        @Schema(description = "Ngày bắt đầu kỳ lương", example = "2026-11-01")
        @NotNull(message = "Ngày bắt đầu không được để trống")
        LocalDate startDate,

        @Schema(description = "Ngày kết thúc kỳ lương", example = "2026-11-30")
        @NotNull(message = "Ngày kết thúc không được để trống")
        LocalDate endDate
) {
}

