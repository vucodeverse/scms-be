package edu.fpt.sba.scms.service.report.export;

import edu.fpt.sba.scms.common.enums.ReportColumnType;
import edu.fpt.sba.scms.common.util.DateTimeUtil;
import edu.fpt.sba.scms.integration.export.ReportDocument;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

/**
 * Tiện ích dựng bảng xuất file: cột, dòng (cho phép ô {@code null}), định dạng ngày giờ theo giờ VN.
 */
final class ExportCells {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(DateTimeUtil.VN);

    private ExportCells() {
    }

    static ReportDocument.Column text(String header) {
        return new ReportDocument.Column(header, ReportColumnType.TEXT);
    }

    static ReportDocument.Column integer(String header) {
        return new ReportDocument.Column(header, ReportColumnType.INTEGER);
    }

    static ReportDocument.Column decimal(String header) {
        return new ReportDocument.Column(header, ReportColumnType.DECIMAL);
    }

    static ReportDocument.Column money(String header) {
        return new ReportDocument.Column(header, ReportColumnType.MONEY);
    }

    static ReportDocument.Column percent(String header) {
        return new ReportDocument.Column(header, ReportColumnType.PERCENT);
    }

    /** Một dòng dữ liệu; khác {@code List.of}, cho phép ô {@code null} (ô trống). */
    static List<Object> row(Object... cells) {
        return Arrays.asList(cells);
    }

    static String date(LocalDate date) {
        return date == null ? null : DATE.format(date);
    }

    static String dateTime(Instant instant) {
        return instant == null ? null : DATE_TIME.format(instant);
    }

    static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }

    static String yesNo(boolean value) {
        return value ? "Có" : "Không";
    }

    static String periodLine(LocalDate from, LocalDate to) {
        return "Kỳ báo cáo: " + date(from) + " - " + date(to);
    }

    /** {@code branchName = null} nghĩa là toàn chuỗi (UC41/42). */
    static String branchLine(String branchName) {
        return "Phạm vi: " + (branchName == null ? "Toàn chuỗi" : "Chi nhánh " + branchName);
    }
}
