package edu.fpt.sba.scms.service.report;

import edu.fpt.sba.scms.common.enums.UserStatus;
import edu.fpt.sba.scms.config.properties.ReportProperties;
import edu.fpt.sba.scms.repository.HrReportRepository;
import edu.fpt.sba.scms.repository.HrReportRepository.AssignmentRow;
import edu.fpt.sba.scms.repository.HrReportRepository.AttendanceRow;
import edu.fpt.sba.scms.repository.SalesReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Nạp dữ liệu chấm công / phân ca / doanh số thu ngân và đối chiếu bằng {@link HrShiftCalculator}.
 * Dùng chung cho báo cáo UC39 và widget chấm công của dashboard UC34 — cùng một cách tính.
 */
@Component
@RequiredArgsConstructor
public class HrReportLoader {

    private final ReportProperties properties;
    private final HrReportRepository hrRepository;
    private final SalesReportRepository salesRepository;

    @Transactional(readOnly = true)
    public HrShiftCalculator.HrReport load(Integer branchId, ReportPeriod period, Instant now) {
        List<AttendanceRow> attendances = hrRepository.findAttendances(branchId, period.startInclusive(),
                period.endExclusive());
        List<AssignmentRow> assignments = hrRepository.findAssignments(branchId, period.from(), period.to());
        Set<Integer> userIds = new HashSet<>();
        attendances.forEach(a -> userIds.add(a.getUserId()));
        assignments.forEach(a -> userIds.add(a.getEmployeeId()));
        var employees = hrRepository.findEmployees(branchId, UserStatus.ACTIVE, userIds);
        var cashierSales = salesRepository.sumSalesByCashier(branchId, ReportRules.REVENUE_ORDER_STATUSES,
                period.startInclusive(), period.endExclusive());
        return HrShiftCalculator.calculate(employees, attendances, assignments, cashierSales,
                new HrShiftCalculator.Context(now, Duration.ofMinutes(properties.lateGraceMinutes())));
    }
}
