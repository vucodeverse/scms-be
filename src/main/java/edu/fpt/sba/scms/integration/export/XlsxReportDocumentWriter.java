package edu.fpt.sba.scms.integration.export;

import edu.fpt.sba.scms.common.enums.ExportFormat;
import edu.fpt.sba.scms.common.enums.ReportColumnType;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ghi báo cáo ra Excel .xlsx (UC36, BR-50) bằng {@link SXSSFWorkbook} (streaming, ít RAM). Mỗi bảng một sheet,
 * ô số giữ kiểu số (lọc/cộng được trong Excel), chỉ đổi định dạng hiển thị.
 * <p>
 * Không dùng {@code autoSizeColumn}: cần font hệ thống (AWT) — dễ lỗi trên container headless. Độ rộng cột
 * ước lượng theo độ dài chữ.
 */
@Component
public class XlsxReportDocumentWriter implements ReportDocumentWriter {

    private static final int ROW_WINDOW = 200;
    private static final int MAX_SHEET_NAME = 31;
    private static final int MIN_COLUMN_CHARS = 10;
    private static final int MAX_COLUMN_CHARS = 60;

    @Override
    public ExportFormat format() {
        return ExportFormat.XLSX;
    }

    @Override
    public byte[] write(ReportDocument document) throws IOException {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(ROW_WINDOW);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles styles = new Styles(workbook);
            Set<String> usedNames = new HashSet<>();
            for (ReportDocument.Table table : document.tables()) {
                Sheet sheet = workbook.createSheet(uniqueSheetName(table.name(), usedNames));
                writeSheet(sheet, document, table, styles);
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private void writeSheet(Sheet sheet, ReportDocument document, ReportDocument.Table table, Styles styles) {
        int rowIndex = 0;
        text(sheet.createRow(rowIndex++), 0, document.title(), styles.title);
        for (String line : document.headerLines()) {
            text(sheet.createRow(rowIndex++), 0, line, null);
        }
        text(sheet.createRow(rowIndex++), 0, table.name(), styles.header);
        rowIndex++;

        Row headerRow = sheet.createRow(rowIndex++);
        List<ReportDocument.Column> columns = table.columns();
        int[] widths = new int[columns.size()];
        for (int c = 0; c < columns.size(); c++) {
            text(headerRow, c, columns.get(c).header(), styles.header);
            widths[c] = columns.get(c).header().length();
        }
        for (List<Object> values : table.rows()) {
            Row row = sheet.createRow(rowIndex++);
            for (int c = 0; c < columns.size(); c++) {
                ReportColumnType type = columns.get(c).type();
                writeCell(row.createCell(c), values.get(c), styles.byType.get(type));
                widths[c] = Math.max(widths[c], CellText.format(values.get(c), type).length());
            }
        }
        for (int c = 0; c < widths.length; c++) {
            int chars = Math.min(MAX_COLUMN_CHARS, Math.max(MIN_COLUMN_CHARS, widths[c] + 2));
            sheet.setColumnWidth(c, chars * 256);
        }
    }

    private static void writeCell(Cell cell, Object value, CellStyle style) {
        cell.setCellStyle(style);
        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
            return;
        }
        if (value != null) {
            cell.setCellValue(value.toString());
        }
    }

    private static void text(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    private static String uniqueSheetName(String name, Set<String> used) {
        String base = WorkbookUtil.createSafeSheetName(name);
        String candidate = base;
        for (int i = 2; !used.add(candidate.toLowerCase()); i++) {
            String suffix = " (" + i + ")";
            candidate = base.substring(0, Math.min(base.length(), MAX_SHEET_NAME - suffix.length())) + suffix;
        }
        return candidate;
    }

    /** Style dùng lại cho cả workbook (Excel giới hạn số style). */
    private static final class Styles {
        private final CellStyle title;
        private final CellStyle header;
        private final Map<ReportColumnType, CellStyle> byType = new EnumMap<>(ReportColumnType.class);

        Styles(Workbook workbook) {
            Font bold = workbook.createFont();
            bold.setBold(true);
            Font big = workbook.createFont();
            big.setBold(true);
            big.setFontHeightInPoints((short) 14);

            title = workbook.createCellStyle();
            title.setFont(big);

            header = workbook.createCellStyle();
            header.setFont(bold);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setBorderBottom(BorderStyle.THIN);

            short dataFormatText = workbook.createDataFormat().getFormat("@");
            byType.put(ReportColumnType.TEXT, numberStyle(workbook, dataFormatText));
            byType.put(ReportColumnType.INTEGER, numberStyle(workbook, workbook.createDataFormat().getFormat("#,##0")));
            byType.put(ReportColumnType.MONEY, numberStyle(workbook, workbook.createDataFormat().getFormat("#,##0")));
            byType.put(ReportColumnType.DECIMAL,
                    numberStyle(workbook, workbook.createDataFormat().getFormat("#,##0.####")));
            byType.put(ReportColumnType.PERCENT,
                    numberStyle(workbook, workbook.createDataFormat().getFormat("0.00\"%\"")));
        }

        private static CellStyle numberStyle(Workbook workbook, short format) {
            CellStyle style = workbook.createCellStyle();
            style.setDataFormat(format);
            return style;
        }
    }
}
