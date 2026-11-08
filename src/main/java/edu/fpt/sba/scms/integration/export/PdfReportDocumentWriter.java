package edu.fpt.sba.scms.integration.export;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import edu.fpt.sba.scms.common.enums.ExportFormat;
import edu.fpt.sba.scms.common.enums.ReportColumnType;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Ghi báo cáo ra PDF (UC36) bằng OpenPDF, khổ A4 ngang. Font LiberationSans (đi kèm jar OpenPDF) được nhúng với
 * mã hoá Identity-H để hiển thị đúng tiếng Việt — font chuẩn Helvetica không có dấu tiếng Việt.
 */
@Component
public class PdfReportDocumentWriter implements ReportDocumentWriter {

    static final String FONT_RESOURCE = "font-fallback/LiberationSans-Regular.ttf";
    private static final float TITLE_SIZE = 14f;
    private static final float SECTION_SIZE = 11f;
    private static final float TEXT_SIZE = 9f;
    private static final float CELL_SIZE = 8f;
    private static final Color HEADER_BG = new Color(230, 230, 230);

    private final BaseFont baseFont;

    public PdfReportDocumentWriter() throws IOException {
        this.baseFont = loadFont();
    }

    @Override
    public ExportFormat format() {
        return ExportFormat.PDF;
    }

    @Override
    public byte[] write(ReportDocument report) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4.rotate(), 24, 24, 24, 24);
        try {
            PdfWriter.getInstance(document, out);
            document.open();
            document.add(new Paragraph(report.title(), new Font(baseFont, TITLE_SIZE, Font.BOLD)));
            Font text = new Font(baseFont, TEXT_SIZE);
            for (String line : report.headerLines()) {
                document.add(new Paragraph(line, text));
            }
            for (ReportDocument.Table table : report.tables()) {
                Paragraph section = new Paragraph(table.name(), new Font(baseFont, SECTION_SIZE, Font.BOLD));
                section.setSpacingBefore(10f);
                section.setSpacingAfter(4f);
                document.add(section);
                document.add(table(table));
            }
        } catch (DocumentException e) {
            throw new IOException("Không ghi được PDF", e);
        } finally {
            if (document.isOpen()) {
                document.close();
            }
        }
        return out.toByteArray();
    }

    private PdfPTable table(ReportDocument.Table table) {
        List<ReportDocument.Column> columns = table.columns();
        PdfPTable pdfTable = new PdfPTable(columns.size());
        pdfTable.setWidthPercentage(100);
        pdfTable.setHeaderRows(1);
        Font headerFont = new Font(baseFont, CELL_SIZE, Font.BOLD);
        Font cellFont = new Font(baseFont, CELL_SIZE);
        for (ReportDocument.Column column : columns) {
            PdfPCell cell = new PdfPCell(new Phrase(column.header(), headerFont));
            cell.setBackgroundColor(HEADER_BG);
            pdfTable.addCell(cell);
        }
        for (List<Object> row : table.rows()) {
            for (int c = 0; c < columns.size(); c++) {
                ReportColumnType type = columns.get(c).type();
                PdfPCell cell = new PdfPCell(new Phrase(CellText.format(row.get(c), type), cellFont));
                cell.setHorizontalAlignment(type == ReportColumnType.TEXT ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT);
                pdfTable.addCell(cell);
            }
        }
        return pdfTable;
    }

    private static BaseFont loadFont() throws IOException {
        try (InputStream in = PdfReportDocumentWriter.class.getClassLoader().getResourceAsStream(FONT_RESOURCE)) {
            if (in == null) {
                throw new IOException("Không tìm thấy font " + FONT_RESOURCE);
            }
            return BaseFont.createFont(FONT_RESOURCE, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true,
                    in.readAllBytes(), null);
        }
    }
}
