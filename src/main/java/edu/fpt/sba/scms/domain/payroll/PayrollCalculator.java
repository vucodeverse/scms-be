package edu.fpt.sba.scms.domain.payroll;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

/**
 * Công thức tính toán bảng lương chi nhánh (Pure Java - Domain).
 * Hiện thực nghiệp vụ tính lương UC45:
 * - Quy đổi phút sang giờ làm.
 * - Lương cơ bản theo giờ làm * đơn giá giờ.
 * - Lương thực nhận (Net Salary) = Base + Bonus - Deduction.
 */
public final class PayrollCalculator {

    private PayrollCalculator() {
        // Utility class
    }

    /**
     * Quy đổi tổng số phút làm việc sang giờ làm (làm tròn 2 chữ số thập phân HALF_UP).
     */
    public static BigDecimal minutesToHours(long totalMinutes) {
        if (totalMinutes <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(totalMinutes)
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    /**
     * Tính lương cơ bản theo giờ:
     * baseSalary = totalHours * hourlyRate (làm tròn 2 chữ số thập phân HALF_UP).
     */
    public static BigDecimal calculateBaseSalary(BigDecimal totalHours, BigDecimal hourlyRate) {
        BigDecimal hours = totalHours != null ? totalHours : BigDecimal.ZERO;
        BigDecimal rate = hourlyRate != null ? hourlyRate : BigDecimal.ZERO;
        return hours.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Tính lương thực nhận (Net Salary):
     * netSalary = max(0, baseSalary + bonus - deduction)
     */
    public static BigDecimal calculateNetSalary(BigDecimal baseSalary, BigDecimal bonus, BigDecimal deduction) {
        BigDecimal base = baseSalary != null ? baseSalary : BigDecimal.ZERO;
        BigDecimal b = bonus != null ? bonus : BigDecimal.ZERO;
        BigDecimal d = deduction != null ? deduction : BigDecimal.ZERO;

        BigDecimal net = base.add(b).subtract(d);
        return net.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : net.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Tính tổng quỹ lương của toàn bộ kỳ lương.
     */
    public static BigDecimal calculatePeriodTotal(Collection<BigDecimal> itemNetSalaries) {
        if (itemNetSalaries == null || itemNetSalaries.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return itemNetSalaries.stream()
                .filter(s -> s != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }
}

