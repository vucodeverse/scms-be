package edu.fpt.sba.scms.integration.export;

import edu.fpt.sba.scms.common.enums.ReportColumnType;

import java.util.List;
import java.util.Objects;

/**
 * Mô hình trung lập của một báo cáo để ghi ra file (UC36) — không phụ thuộc định dạng (xlsx/pdf).
 *
 * @param title       tiêu đề báo cáo
 * @param headerLines các dòng mô tả bộ lọc (chi nhánh, khoảng ngày...)
 * @param tables      các bảng; xlsx: mỗi bảng một sheet, pdf: các bảng nối tiếp nhau
 */
public record ReportDocument(String title, List<String> headerLines, List<Table> tables) {

    public ReportDocument {
        Objects.requireNonNull(title, "title");
        headerLines = List.copyOf(headerLines);
        tables = List.copyOf(tables);
    }

    /** Một cột: tiêu đề + kiểu dữ liệu quyết định định dạng ô. */
    public record Column(String header, ReportColumnType type) {
    }

    /**
     * Một bảng. Mỗi dòng có đúng số ô bằng số cột; ô là {@code String}, {@code Number} hoặc {@code null} (ô trống).
     */
    public record Table(String name, List<Column> columns, List<List<Object>> rows) {

        public Table {
            Objects.requireNonNull(name, "name");
            columns = List.copyOf(columns);
            rows = List.copyOf(rows);
            for (List<Object> row : rows) {
                if (row.size() != columns.size()) {
                    throw new IllegalArgumentException("Số ô (" + row.size() + ") khác số cột (" + columns.size()
                            + ") ở bảng " + name);
                }
            }
        }
    }
}
