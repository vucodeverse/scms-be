package edu.fpt.sba.scms.dto.response.report;

/**
 * File báo cáo đã sinh để tải về (UC36).
 */
public record ReportFileDto(
        String fileName,
        String contentType,
        byte[] content) {
}
