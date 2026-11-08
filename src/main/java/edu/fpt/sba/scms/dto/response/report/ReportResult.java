package edu.fpt.sba.scms.dto.response.report;

/**
 * Kết quả report có thể rỗng — xuất file khi rỗng bị chặn (UC36 AT2), FE hiển thị empty state.
 */
public interface ReportResult {

    /** {@code true} khi kỳ báo cáo không có dữ liệu nghiệp vụ nào. */
    boolean empty();
}
