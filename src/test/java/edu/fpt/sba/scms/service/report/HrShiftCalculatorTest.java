package edu.fpt.sba.scms.service.report;

import edu.fpt.sba.scms.dto.response.report.EmployeeHrDto;
import edu.fpt.sba.scms.repository.HrReportRepository.AssignmentRow;
import edu.fpt.sba.scms.repository.HrReportRepository.AttendanceRow;
import edu.fpt.sba.scms.repository.HrReportRepository.EmployeeRow;
import edu.fpt.sba.scms.repository.SalesReportRepository.CashierSalesRow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HrShiftCalculatorTest {

    private static final int CASHIER = 10;
    private static final int MANAGER = 20;
    /** 2026-10-15 07:00 VN. */
    private static final Instant MORNING_START = Instant.parse("2026-10-15T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-17T00:00:00Z");
    private static final HrShiftCalculator.Context CONTEXT = new HrShiftCalculator.Context(NOW, Duration.ofMinutes(5));

    private static EmployeeRow employee(int id, String name, String role) {
        return Rows.of(EmployeeRow.class, "userId", id, "fullName", name, "roleCode", role);
    }

    private static AttendanceRow attendance(int userId, Instant in, Instant out, Long minutes) {
        return Rows.of(AttendanceRow.class, "userId", userId, "checkIn", in, "checkOut", out, "workedMinutes", minutes);
    }

    private static AssignmentRow assignment(int userId, int shiftId, Instant start, Instant end) {
        return Rows.of(AssignmentRow.class, "employeeId", userId, "shiftId", shiftId, "plannedStart", start,
                "plannedEnd", end);
    }

    private final List<EmployeeRow> employees = List.of(
            employee(CASHIER, "Thu ngân A", "CASHIER"), employee(MANAGER, "Quản lý B", "BRANCH_MANAGER"));
    private final List<AssignmentRow> assignments = List.of(
            assignment(CASHIER, 1, MORNING_START, MORNING_START.plus(Duration.ofHours(5))),
            assignment(CASHIER, 2, MORNING_START.plus(Duration.ofHours(6)), MORNING_START.plus(Duration.ofHours(11))));
    private final List<AttendanceRow> attendances = List.of(
            // Vào muộn 20 phút, về đúng giờ — 280 phút.
            attendance(CASHIER, MORNING_START.plus(Duration.ofMinutes(20)), MORNING_START.plus(Duration.ofHours(5)), 280L),
            // Quên check-out (mở quá 14 giờ).
            attendance(MANAGER, MORNING_START.plus(Duration.ofHours(1)), null, null));
    private final List<CashierSalesRow> cashierSales = List.of(Rows.of(CashierSalesRow.class,
            "cashierId", CASHIER, "orderCount", 3L, "revenue", new BigDecimal("215000")));

    @Test
    void doiChieuCaVoiChamCong() {
        HrShiftCalculator.HrReport report = HrShiftCalculator.calculate(employees, attendances, assignments,
                cashierSales, CONTEXT);

        EmployeeHrDto cashier = report.employees().get(0);
        assertThat(cashier.shiftsScheduled()).isEqualTo(2);
        assertThat(cashier.shiftsWorked()).isEqualTo(1);
        assertThat(cashier.absentCount()).isEqualTo(1);
        assertThat(cashier.lateCount()).isEqualTo(1);
        assertThat(cashier.earlyLeaveCount()).isZero();
        assertThat(cashier.hoursWorked()).isEqualByComparingTo("4.67");
        assertThat(cashier.ordersProcessed()).isEqualTo(3);
        assertThat(cashier.revenuePerHour()).isEqualByComparingTo("46038.54");

        EmployeeHrDto manager = report.employees().get(1);
        assertThat(manager.missingCheckoutCount()).isEqualTo(1);
        assertThat(manager.hoursWorked()).isEqualByComparingTo("0");
        assertThat(manager.ordersProcessed()).as("năng suất chỉ cho thu ngân").isNull();

        assertThat(report.summary().absentCount()).isEqualTo(1);
        assertThat(report.summary().attendanceCount()).isEqualTo(2);
        assertThat(report.scheduledEmployees()).isEqualTo(1);
        assertThat(report.checkedInEmployees()).isEqualTo(2);
        assertThat(report.onDutyNow()).isZero();
    }

    @Test
    void caChuaKetThucThiChuaTinhVang() {
        HrShiftCalculator.Context early = new HrShiftCalculator.Context(MORNING_START.plus(Duration.ofHours(2)),
                Duration.ofMinutes(5));

        HrShiftCalculator.HrReport report = HrShiftCalculator.calculate(employees, List.of(), assignments, List.of(),
                early);

        assertThat(report.employees().get(0).absentCount()).isZero();
        assertThat(report.employees().get(0).ordersProcessed()).isZero();
    }

    @Test
    void veSomNgoaiDungSai() {
        List<AttendanceRow> leftEarly = List.of(attendance(CASHIER, MORNING_START,
                MORNING_START.plus(Duration.ofHours(4)), 240L));

        HrShiftCalculator.HrReport report = HrShiftCalculator.calculate(employees, leftEarly, assignments.subList(0, 1),
                List.of(), CONTEXT);

        assertThat(report.employees().get(0).earlyLeaveCount()).isEqualTo(1);
        assertThat(report.employees().get(0).lateCount()).isZero();
    }
}
