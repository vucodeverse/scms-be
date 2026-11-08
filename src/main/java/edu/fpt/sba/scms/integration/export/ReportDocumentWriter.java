package edu.fpt.sba.scms.integration.export;

import edu.fpt.sba.scms.common.enums.ExportFormat;

import java.io.IOException;

/**
 * Cổng ghi {@link ReportDocument} ra file. Mỗi định dạng một implementation (Strategy) — thêm định dạng mới
 * không sửa service xuất báo cáo.
 */
public interface ReportDocumentWriter {

    ExportFormat format();

    byte[] write(ReportDocument document) throws IOException;
}
