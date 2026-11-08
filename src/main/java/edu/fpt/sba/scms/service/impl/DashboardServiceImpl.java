package edu.fpt.sba.scms.service.impl;

import edu.fpt.sba.scms.common.exception.NotFoundException;
import edu.fpt.sba.scms.common.util.DateTimeUtil;
import edu.fpt.sba.scms.dto.response.report.DashboardDto;
import edu.fpt.sba.scms.dto.response.report.DashboardWidgetDto;
import edu.fpt.sba.scms.model.Branch;
import edu.fpt.sba.scms.repository.BranchRepository;
import edu.fpt.sba.scms.service.DashboardService;
import edu.fpt.sba.scms.service.report.DashboardWidgetLoader;
import edu.fpt.sba.scms.service.report.ReportScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.function.Supplier;

/**
 * Dashboard chi nhánh (UC34). Phạm vi chi nhánh và chi nhánh không tồn tại vẫn báo lỗi cho cả request (403/404);
 * còn lỗi khi tải <b>một</b> widget chỉ làm widget đó có {@code status = ERROR} để FE hiển thị empty state + Retry (AT1).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    static final String WIDGET_LOAD_FAILED = "Không tải được dữ liệu dashboard. Vui lòng tải lại.";

    private final BranchRepository branchRepository;
    private final DashboardWidgetLoader widgets;

    /**
     * Không bọc transaction chung (NOT_SUPPORTED): mỗi widget mở transaction chỉ-đọc riêng trong
     * {@link DashboardWidgetLoader}, nên một widget lỗi không làm rollback-only cả dashboard.
     */
    @Override
    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    public DashboardDto getBranchDashboard(Integer requestedBranchId) {
        Integer branchId = ReportScope.requireBranch(requestedBranchId);
        Branch branch = branchRepository.findById(branchId).orElseThrow(() -> new NotFoundException("chi nhánh"));
        LocalDate today = DateTimeUtil.todayVN();
        Instant now = Instant.now();
        return new DashboardDto(branchId, branch.getName(), today, now,
                load("sales", branchId, () -> widgets.sales(branchId, today)),
                load("cashDrawer", branchId, () -> widgets.cashDrawer(branchId)),
                load("lowStock", branchId, () -> widgets.lowStock(branchId)),
                load("attendance", branchId, () -> widgets.attendance(branchId, today, now)));
    }

    /** Lỗi được ghi log đầy đủ (kèm stack trace) và trả về trạng thái ERROR của widget — không nuốt lặng lẽ. */
    private <T> DashboardWidgetDto<T> load(String widget, Integer branchId, Supplier<T> loader) {
        try {
            return DashboardWidgetDto.ok(loader.get());
        } catch (RuntimeException e) {
            log.error("Dashboard widget '{}' failed for branchId={}", widget, branchId, e);
            return DashboardWidgetDto.error(WIDGET_LOAD_FAILED);
        }
    }
}
