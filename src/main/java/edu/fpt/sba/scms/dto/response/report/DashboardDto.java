package edu.fpt.sba.scms.dto.response.report;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Dashboard tổng quan trong ngày của chi nhánh (UC34). Mỗi widget tải độc lập.
 */
public record DashboardDto(
        Integer branchId,
        String branchName,
        LocalDate date,
        Instant generatedAt,
        DashboardWidgetDto<SalesKpiDto> sales,
        DashboardWidgetDto<CashDrawerDto> cashDrawer,
        DashboardWidgetDto<LowStockWidgetDto> lowStock,
        DashboardWidgetDto<AttendanceTodayDto> attendance) {
}
