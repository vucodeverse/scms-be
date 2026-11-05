package edu.fpt.sba.scms.common.enums;

/**
 * Nguồn dữ liệu cho các port đọc của module report ({@code app.report.source}, không phân biệt hoa thường).
 * <ul>
 *   <li>{@link #NONE} — adapter rỗng: bảng nguồn (order/shift/inventory...) chưa có, report trả dữ liệu rỗng.</li>
 *   <li>{@link #SQL} — adapter native SQL theo schema SDD §5.1.</li>
 * </ul>
 */
public enum ReportSource {
    NONE, SQL
}
