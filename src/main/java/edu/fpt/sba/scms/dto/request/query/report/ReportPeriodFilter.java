package edu.fpt.sba.scms.dto.request.query.report;

import java.time.LocalDate;

/**
 * Bộ lọc chung của mọi report (UC34–UC42): chi nhánh + khoảng ngày theo giờ VN.
 * <p>
 * Record không kế thừa được nhau, nên mỗi query report (vd. Sales thêm {@code shiftId}, {@code paymentMethod})
 * hiện thực interface này để tầng service xử lý khoảng ngày và branch scope một cách thống nhất.
 */
public interface ReportPeriodFilter {

    /** Chi nhánh được yêu cầu; {@code null} = theo token (BM) hoặc toàn chuỗi (COO, report cho phép). */
    Integer branchId();

    /** Từ ngày (giờ VN, bao gồm); {@code null} = theo mặc định của {@code ReportPeriod}. */
    LocalDate from();

    /** Đến ngày (giờ VN, bao gồm); {@code null} = theo mặc định của {@code ReportPeriod}. */
    LocalDate to();
}
