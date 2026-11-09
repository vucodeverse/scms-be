package edu.fpt.sba.scms.service.report;

import edu.fpt.sba.scms.common.enums.Roles;
import edu.fpt.sba.scms.domain.attendance.AttendanceCalculator;
import edu.fpt.sba.scms.domain.payroll.PayrollCalculator;
import edu.fpt.sba.scms.dto.response.report.EmployeeHrDto;
import edu.fpt.sba.scms.dto.response.report.HrSummaryDto;
import edu.fpt.sba.scms.repository.HrReportRepository.AssignmentRow;
import edu.fpt.sba.scms.repository.HrReportRepository.AttendanceRow;
import edu.fpt.sba.scms.repository.HrReportRepository.EmployeeRow;
import edu.fpt.sba.scms.repository.SalesReportRepository.CashierSalesRow;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static edu.fpt.sba.scms.service.report.ReportMath.money;
import static edu.fpt.sba.scms.service.report.ReportMath.zeroIfNull;

/**
 * Báo cáo nhân sự &amp; ca (UC39) — đối chiếu lịch phân ca với chấm công thực tế.
 * <ul>
 *   <li>Giờ công = check-in → check-out (BR-06/07): dùng {@code worked_minutes} đã lưu, thiếu thì tự tính.
 *       Lượt chưa check-out không có giờ công.</li>
 *   <li>Quên check-out: mở quá {@value #FORGOT_CHECKOUT_HOURS} giờ (BR-09).</li>
 *   <li>Một ca được phân công khớp với lượt chấm công đầu tiên (chưa dùng) của cùng nhân viên giao nhau với
 *       khung giờ ca. Không khớp và ca đã kết thúc → vắng (AT2). Đi muộn / về sớm so với giờ ca, có dung sai.</li>
 *   <li>Năng suất chỉ cho Thu ngân: số đơn &amp; doanh thu / giờ công (BR-09/10 của UC39). Không có lương (BR-47).</li>
 * </ul>
 */
public final class HrShiftCalculator {

    static final long FORGOT_CHECKOUT_HOURS = 14;

    /** Kết quả đã đối chiếu; {@code onDutyNow} = lượt đang mở chưa quá hạn quên check-out (dùng cho dashboard). */
    public record HrReport(List<EmployeeHrDto> employees, HrSummaryDto summary, int onDutyNow,
                           int scheduledEmployees, int checkedInEmployees) {
    }

    /** Ngữ cảnh tính: thời điểm hiện tại (xét ca đã kết thúc / quên check-out) và dung sai đi muộn. */
    public record Context(Instant now, Duration lateGrace) {
    }

    private HrShiftCalculator() {
    }

    public static HrReport calculate(List<EmployeeRow> employees, List<AttendanceRow> attendances,
                                     List<AssignmentRow> assignments, List<CashierSalesRow> cashierSales,
                                     Context context) {
        Map<Integer, EmployeeTally> tallies = new HashMap<>();
        Map<Integer, List<AttendanceRow>> attendanceByUser = new HashMap<>();
        int onDuty = 0;
        for (AttendanceRow a : attendances) {
            attendanceByUser.computeIfAbsent(a.getUserId(), id -> new ArrayList<>()).add(a);
            tally(tallies, a.getUserId()).addAttendance(a, context.now());
            onDuty += a.getCheckOut() == null && !isForgotten(a, context.now()) ? 1 : 0;
        }
        // So khớp theo danh tính object: projection proxy không đảm bảo equals/hashCode theo giá trị.
        Set<AttendanceRow> used = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Integer> scheduled = new HashSet<>();
        for (AssignmentRow shift : assignments) {
            scheduled.add(shift.getEmployeeId());
            AttendanceRow match = findMatch(shift, attendanceByUser.getOrDefault(shift.getEmployeeId(), List.of()), used,
                    context.now());
            tally(tallies, shift.getEmployeeId()).addShift(shift, match, context);
        }
        Map<Integer, CashierSalesRow> salesByCashier = new HashMap<>();
        cashierSales.forEach(s -> salesByCashier.put(s.getCashierId(), s));

        List<EmployeeHrDto> rows = new ArrayList<>();
        for (EmployeeRow e : employees) {
            EmployeeTally t = tallies.getOrDefault(e.getUserId(), new EmployeeTally());
            boolean cashier = Roles.CASHIER.name().equals(e.getRoleCode());
            rows.add(t.toDto(e, cashier ? salesByCashier.get(e.getUserId()) : null, cashier));
        }
        return new HrReport(rows, summarize(rows), onDuty, scheduled.size(), attendanceByUser.size());
    }

    private static EmployeeTally tally(Map<Integer, EmployeeTally> tallies, Integer userId) {
        return tallies.computeIfAbsent(userId, id -> new EmployeeTally());
    }

    private static AttendanceRow findMatch(AssignmentRow shift, List<AttendanceRow> candidates,
                                           Set<AttendanceRow> used, Instant now) {
        for (AttendanceRow a : candidates) {
            Instant end = a.getCheckOut() != null ? a.getCheckOut() : now;
            boolean overlaps = a.getCheckIn().isBefore(shift.getPlannedEnd()) && end.isAfter(shift.getPlannedStart());
            if (overlaps && used.add(a)) {
                return a;
            }
        }
        return null;
    }

    private static boolean isForgotten(AttendanceRow a, Instant now) {
        return a.getCheckOut() == null && a.getCheckIn().plus(Duration.ofHours(FORGOT_CHECKOUT_HOURS)).isBefore(now);
    }

    private static long workedMinutes(AttendanceRow a) {
        if (a.getCheckOut() == null) {
            return 0;
        }
        if (a.getWorkedMinutes() != null) {
            return a.getWorkedMinutes();
        }
        return AttendanceCalculator.calculateWorkedMinutes(a.getCheckIn(), a.getCheckOut());
    }

    private static HrSummaryDto summarize(List<EmployeeHrDto> rows) {
        int scheduled = 0;
        int worked = 0;
        int absent = 0;
        int attendance = 0;
        int late = 0;
        int early = 0;
        int missing = 0;
        BigDecimal hours = BigDecimal.ZERO;
        for (EmployeeHrDto r : rows) {
            scheduled += r.shiftsScheduled();
            worked += r.shiftsWorked();
            absent += r.absentCount();
            attendance += r.attendanceCount();
            late += r.lateCount();
            early += r.earlyLeaveCount();
            missing += r.missingCheckoutCount();
            hours = hours.add(r.hoursWorked());
        }
        return new HrSummaryDto(rows.size(), scheduled, worked, absent, attendance, hours, late, early, missing);
    }

    /** Bộ đếm của một nhân viên. */
    private static final class EmployeeTally {
        private int scheduled;
        private int worked;
        private int absent;
        private int attendanceCount;
        private long minutes;
        private int late;
        private int earlyLeave;
        private int missingCheckout;

        void addAttendance(AttendanceRow a, Instant now) {
            attendanceCount++;
            minutes += workedMinutes(a);
            missingCheckout += isForgotten(a, now) ? 1 : 0;
        }

        void addShift(AssignmentRow shift, AttendanceRow match, Context context) {
            scheduled++;
            if (match == null) {
                absent += shift.getPlannedEnd().isBefore(context.now()) ? 1 : 0;
                return;
            }
            worked++;
            boolean isReplacement = Boolean.TRUE.equals(shift.getIsReplacement());
            if (!isReplacement) {
                late += match.getCheckIn().isAfter(shift.getPlannedStart().plus(context.lateGrace())) ? 1 : 0;
                boolean leftEarly = match.getCheckOut() != null
                        && match.getCheckOut().isBefore(shift.getPlannedEnd().minus(context.lateGrace()));
                earlyLeave += leftEarly ? 1 : 0;
            }
        }

        EmployeeHrDto toDto(EmployeeRow e, CashierSalesRow sales, boolean cashier) {
            BigDecimal hours = PayrollCalculator.minutesToHours(minutes);
            Long orders = cashier ? (sales == null ? 0L : zeroIfNull(sales.getOrderCount())) : null;
            BigDecimal revenue = cashier ? money(sales == null ? null : sales.getRevenue()) : null;
            BigDecimal perHour = cashier ? ReportMath.divide(revenue, hours) : null;
            return new EmployeeHrDto(e.getUserId(), e.getFullName(), e.getRoleCode(), scheduled, worked, absent,
                    attendanceCount, hours, late, earlyLeave, missingCheckout, orders, revenue,
                    perHour == null ? null : money(perHour));
        }
    }
}
