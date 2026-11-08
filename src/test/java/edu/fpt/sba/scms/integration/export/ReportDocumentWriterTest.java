package edu.fpt.sba.scms.integration.export;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import edu.fpt.sba.scms.common.enums.ReportColumnType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportDocumentWriterTest {

    private final ReportDocument document = new ReportDocument("Báo cáo doanh số chi nhánh",
            List.of("Phạm vi: Chi nhánh Bến Thành", "Kỳ báo cáo: 15/10/2026 - 15/10/2026"),
            List.of(new ReportDocument.Table("Tổng hợp",
                            List.of(new ReportDocument.Column("Số đơn", ReportColumnType.INTEGER),
                                    new ReportDocument.Column("Doanh thu", ReportColumnType.MONEY)),
                            List.of(Arrays.asList(3L, new BigDecimal("215000.00")))),
                    new ReportDocument.Table("Tổng hợp",
                            List.of(new ReportDocument.Column("Sản phẩm", ReportColumnType.TEXT),
                                    new ReportDocument.Column("% Số lượng", ReportColumnType.PERCENT)),
                            List.of(Arrays.asList("Cà phê sữa đá", new BigDecimal("66.67")),
                                    Arrays.asList("Trà đào", null)))));

    @Test
    void xlsxGiuKieuSoVaTenSheetKhongTrung() throws Exception {
        byte[] bytes = new XlsxReportDocumentWriter().write(document);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(2);
            assertThat(workbook.getSheetName(1)).isEqualTo("Tổng hợp (2)");
            Sheet summary = workbook.getSheet("Tổng hợp");
            assertThat(summary.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Báo cáo doanh số chi nhánh");
            // tiêu đề + 2 dòng mô tả + tên bảng + dòng trống → header cột ở dòng 5, dữ liệu ở dòng 6
            assertThat(summary.getRow(5).getCell(1).getStringCellValue()).isEqualTo("Doanh thu");
            assertThat(summary.getRow(6).getCell(0).getNumericCellValue()).isEqualTo(3d);
            assertThat(summary.getRow(6).getCell(1).getNumericCellValue()).isEqualTo(215000d);
        }
    }

    @Test
    void pdfHienThiTiengViet() throws Exception {
        byte[] bytes = new PdfReportDocumentWriter().write(document);

        assertThat(new String(bytes, 0, 5)).isEqualTo("%PDF-");
        PdfReader reader = new PdfReader(bytes);
        String text = new PdfTextExtractor(reader).getTextFromPage(1);
        reader.close();
        assertThat(text).contains("Báo cáo doanh số chi nhánh", "Bến Thành", "Cà phê sữa đá", "215.000", "66,67%");
    }

    @Test
    void bangPhaiCoDuOTheoSoCot() {
        List<ReportDocument.Column> columns = List.of(new ReportDocument.Column("A", ReportColumnType.TEXT));
        List<List<Object>> rows = List.of(Arrays.asList("x", "y"));

        assertThatThrownBy(() -> new ReportDocument.Table("Bảng", columns, rows))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
