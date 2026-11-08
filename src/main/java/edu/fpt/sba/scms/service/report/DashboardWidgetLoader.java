package edu.fpt.sba.scms.service.report;

import edu.fpt.sba.scms.common.enums.PaymentStatus;
import edu.fpt.sba.scms.common.enums.ShiftStatus;
import edu.fpt.sba.scms.dto.response.report.AttendanceTodayDto;
import edu.fpt.sba.scms.dto.response.report.CashDrawerDto;
import edu.fpt.sba.scms.dto.response.report.LowStockItemDto;
import edu.fpt.sba.scms.dto.response.report.LowStockWidgetDto;
import edu.fpt.sba.scms.dto.response.report.SalesKpiDto;
import edu.fpt.sba.scms.repository.CashFlowReportRepository;
import edu.fpt.sba.scms.repository.InventoryReportRepository;
import edu.fpt.sba.scms.repository.SalesReportRepository;
import edu.fpt.sba.scms.repository.ShiftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Nạp dữ liệu từng widget dashboard (UC34). Mỗi method là một transaction chỉ-đọc <b>riêng</b>: widget lỗi không
 * đánh dấu rollback-only transaction của widget khác (AT1).
 */
@Component
@RequiredArgsConstructor
public class DashboardWidgetLoader {

    /** Số ngày của mini chart doanh thu (gồm hôm nay). */
    static final int TREND_DAYS = 7;
    /** Số nguyên liệu tồn thấp hiển thị trên widget. */
    static final int LOW_STOCK_ITEMS = 5;

    private final SalesReportRepository salesRepository;
    private final ShiftRepository shiftRepository;
    private final CashFlowReportRepository cashFlowRepository;
    private final InventoryReportRepository inventoryRepository;
    private final HrReportLoader hrReportLoader;

    @Transactional(readOnly = true)
    public SalesKpiDto sales(Integer branchId, LocalDate today) {
        ReportPeriod period = new ReportPeriod(today.minusDays(TREND_DAYS - 1L), today);
        var hours = salesRepository.sumSalesByPaidUtcHour(branchId, ReportRules.REVENUE_ORDER_STATUSES,
                period.startInclusive(), period.endExclusive(), null, null, PaymentStatus.SUCCESS);
        return SalesReportCalculator.kpi(SalesReportCalculator.daily(hours, period));
    }

    @Transactional(readOnly = true)
    public CashDrawerDto cashDrawer(Integer branchId) {
        return shiftRepository.findFirstByBranchIdAndStatusOrderByOpenedAtDesc(branchId, ShiftStatus.OPEN)
                .map(shift -> CashFlowCalculator.drawer(shift, cashFlowRepository.findByShiftIds(List.of(shift.getId()))))
                .orElseGet(CashDrawerDto::noOpenShift);
    }

    @Transactional(readOnly = true)
    public LowStockWidgetDto lowStock(Integer branchId) {
        List<LowStockItemDto> low = InventoryReportCalculator.lowStock(inventoryRepository.findStockItems(branchId));
        return new LowStockWidgetDto(low.size(), low.subList(0, Math.min(LOW_STOCK_ITEMS, low.size())));
    }

    @Transactional(readOnly = true)
    public AttendanceTodayDto attendance(Integer branchId, LocalDate today, Instant now) {
        HrShiftCalculator.HrReport report = hrReportLoader.load(branchId, ReportPeriod.ofDay(today), now);
        return new AttendanceTodayDto(report.scheduledEmployees(), report.checkedInEmployees(), report.onDutyNow(),
                report.summary().lateCount(), report.summary().absentCount(),
                report.summary().missingCheckoutCount());
    }
}
