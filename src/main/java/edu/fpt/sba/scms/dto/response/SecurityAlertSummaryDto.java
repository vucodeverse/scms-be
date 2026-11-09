package edu.fpt.sba.scms.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "SecurityAlertSummaryDto", description = "Tổng hợp cảnh báo bảo mật cho Quản lý chi nhánh và COO")
public record SecurityAlertSummaryDto(
        long activeCount,
        long currentlyLockedCount,
        List<SecurityAlertDto> items
) {
}
