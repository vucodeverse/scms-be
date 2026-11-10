package edu.fpt.sba.scms.integration.export;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import edu.fpt.sba.scms.common.util.MoneyUtil;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;

/**
 * Trình sinh file PDF biên lai khổ nhiệt K80 (80mm) theo chuẩn in ấn (UC22, BR-31).
 */
@Component
public class ReceiptPdfWriter {

    private static final String FONT_RESOURCE = "font-fallback/LiberationSans-Regular.ttf";
    private static final float K80_WIDTH = 226.77f; // 80mm tính theo point (1 inch = 72 pt, 80mm ~ 3.149 in)
    private static final float MARGIN_H = 10f;
    private static final float MARGIN_V = 12f;

    private static final float TITLE_SIZE = 12f;
    private static final float BRANCH_NAME_SIZE = 11f;
    private static final float TEXT_SIZE = 8f;
    private static final float SMALL_TEXT_SIZE = 7f;
    private static final float HIGHLIGHT_SIZE = 10f;

    private final BaseFont baseFont;

    public ReceiptPdfWriter() throws IOException {
        this.baseFont = loadFont();
    }

    public byte[] write(ReceiptPrintModel model) throws IOException {
        // Ước lượng chiều dài trang K80 cuộn tự động theo số lượng món
        int itemCount = model.items() != null ? model.items().size() : 0;
        int toppingLines = 0;
        if (model.items() != null) {
            for (ReceiptPrintModel.Item item : model.items()) {
                if (item.toppings() != null) {
                    toppingLines += item.toppings().size();
                }
                if (item.options() != null && !item.options().isBlank()) {
                    toppingLines++;
                }
            }
        }
        float calculatedHeight = Math.max(380f, 260f + (itemCount * 28f) + (toppingLines * 12f));

        Rectangle pageSize = new Rectangle(K80_WIDTH, calculatedHeight);
        Document document = new Document(pageSize, MARGIN_H, MARGIN_H, MARGIN_V, MARGIN_V);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            Font fontBranch = new Font(baseFont, BRANCH_NAME_SIZE, Font.BOLD);
            Font fontTitle = new Font(baseFont, TITLE_SIZE, Font.BOLD);
            Font fontText = new Font(baseFont, TEXT_SIZE);
            Font fontBold = new Font(baseFont, TEXT_SIZE, Font.BOLD);
            Font fontSmallItalic = new Font(baseFont, SMALL_TEXT_SIZE, Font.ITALIC);
            Font fontHighlight = new Font(baseFont, HIGHLIGHT_SIZE, Font.BOLD);

            // 1. Header chi nhánh
            Paragraph branchName = new Paragraph(safeText(model.branchName(), "SMART CAFE").toUpperCase(), fontBranch);
            branchName.setAlignment(Element.ALIGN_CENTER);
            document.add(branchName);

            if (model.branchAddress() != null && !model.branchAddress().isBlank()) {
                Paragraph address = new Paragraph(model.branchAddress(), fontText);
                address.setAlignment(Element.ALIGN_CENTER);
                document.add(address);
            }

            if (model.branchPhone() != null && !model.branchPhone().isBlank()) {
                Paragraph phone = new Paragraph("ĐT: " + model.branchPhone(), fontText);
                phone.setAlignment(Element.ALIGN_CENTER);
                document.add(phone);
            }

            // 2. Tiêu đề
            Paragraph title = new Paragraph("HÓA ĐƠN THANH TOÁN", fontTitle);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingBefore(4f);
            title.setSpacingAfter(4f);
            document.add(title);

            addSeparatorLine(document);

            // 3. Thông tin đơn hàng (ĐÃ BỎ BÀN)
            addKeyValueRow(document, fontBold, fontText, "Mã đơn hàng:", "#" + model.orderId());
            addKeyValueRow(document, fontText, fontText, "Ngày in: " + model.printedDate(), "Giờ in: " + model.printedTime());
            addKeyValueRow(document, fontText, fontText, "Thu ngân:", safeText(model.cashierName(), "Thu ngân"));

            addSeparatorLine(document);

            // 4. Bảng danh sách món
            PdfPTable table = new PdfPTable(new float[]{45f, 13f, 21f, 21f});
            table.setWidthPercentage(100f);
            table.setSpacingBefore(2f);
            table.setSpacingAfter(2f);

            // Header bảng
            table.addCell(createCell("TÊN HÀNG", fontBold, Element.ALIGN_LEFT, false));
            table.addCell(createCell("SL", fontBold, Element.ALIGN_CENTER, false));
            table.addCell(createCell("ĐƠN GIÁ", fontBold, Element.ALIGN_RIGHT, false));
            table.addCell(createCell("T.TIỀN", fontBold, Element.ALIGN_RIGHT, false));

            if (model.items() != null) {
                for (ReceiptPrintModel.Item item : model.items()) {
                    String titleLine = item.index() + ") " + item.productName()
                            + (item.size() != null && !item.size().isBlank() ? " (" + item.size() + ")" : "");
                    PdfPCell titleCell = createCell(titleLine, fontBold, Element.ALIGN_LEFT, false);
                    titleCell.setColspan(4);
                    table.addCell(titleCell);

                    table.addCell(createCell("", fontText, Element.ALIGN_LEFT, false));
                    table.addCell(createCell(String.valueOf(item.quantity()), fontText, Element.ALIGN_CENTER, false));
                    table.addCell(createCell(formatMoney(item.unitPrice()), fontText, Element.ALIGN_RIGHT, false));
                    table.addCell(createCell(formatMoney(item.lineTotal()), fontBold, Element.ALIGN_RIGHT, false));

                    if (item.toppings() != null) {
                        for (String topping : item.toppings()) {
                            PdfPCell topCell = createCell("  + " + topping, fontSmallItalic, Element.ALIGN_LEFT, false);
                            topCell.setColspan(4);
                            table.addCell(topCell);
                        }
                    }

                    if (item.options() != null && !item.options().isBlank()) {
                        PdfPCell optCell = createCell("  * " + item.options(), fontSmallItalic, Element.ALIGN_LEFT, false);
                        optCell.setColspan(4);
                        table.addCell(optCell);
                    }
                }
            }
            document.add(table);

            addSeparatorLine(document);

            // 5. Tổng kết
            addSummaryRow(document, fontText, fontBold, "T.Cộng (" + model.totalQuantity() + " món):", formatMoney(model.totalAmount()));

            if (model.hasDiscount() && model.discountAmount() != null && model.discountAmount().compareTo(BigDecimal.ZERO) > 0) {
                addSummaryRow(document, fontText, fontText, "Giảm giá (Voucher):", "-" + formatMoney(model.discountAmount()));
            }

            addSeparatorLine(document);

            addSummaryRow(document, fontHighlight, fontHighlight, safeText(model.paymentMethod(), "TIỀN MẶT") + ":", formatMoney(model.finalAmount()));

            if (model.isCash()) {
                if (model.receivedAmount() != null) {
                    addSummaryRow(document, fontText, fontText, "Tiền khách đưa:", formatMoney(model.receivedAmount()));
                }
                if (model.changeReturned() != null) {
                    addSummaryRow(document, fontText, fontBold, "Tiền thối lại:", formatMoney(model.changeReturned()));
                }
            }

            if (model.amountInWords() != null && !model.amountInWords().isBlank()) {
                Paragraph words = new Paragraph("(" + model.amountInWords() + "./)", fontSmallItalic);
                words.setAlignment(Element.ALIGN_RIGHT);
                words.setSpacingBefore(3f);
                document.add(words);
            }

            addSeparatorLine(document);

            // 6. Chân trang (ĐÃ BỎ WIFI)
            Paragraph footer = new Paragraph("Cảm Ơn Quý Khách - Hẹn Gặp Lại!", fontBold);
            footer.setAlignment(Element.ALIGN_CENTER);
            footer.setSpacingBefore(4f);
            document.add(footer);

        } catch (DocumentException e) {
            throw new IOException("Lỗi khi kết xuất file PDF biên lai", e);
        } finally {
            if (document.isOpen()) {
                document.close();
            }
        }

        return out.toByteArray();
    }

    private static void addKeyValueRow(Document doc, Font leftFont, Font rightFont, String left, String right) throws DocumentException {
        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(100f);
        t.addCell(createCell(left, leftFont, Element.ALIGN_LEFT, false));
        t.addCell(createCell(right, rightFont, Element.ALIGN_RIGHT, false));
        doc.add(t);
    }

    private static void addSummaryRow(Document doc, Font leftFont, Font rightFont, String left, String right) throws DocumentException {
        PdfPTable t = new PdfPTable(new float[]{60f, 40f});
        t.setWidthPercentage(100f);
        t.addCell(createCell(left, leftFont, Element.ALIGN_LEFT, false));
        t.addCell(createCell(right, rightFont, Element.ALIGN_RIGHT, false));
        doc.add(t);
    }

    private static void addSeparatorLine(Document doc) throws DocumentException {
        Paragraph sep = new Paragraph("--------------------------------------------------", new Font(Font.COURIER, 7f));
        sep.setAlignment(Element.ALIGN_CENTER);
        sep.setSpacingBefore(1f);
        sep.setSpacingAfter(1f);
        doc.add(sep);
    }

    private static PdfPCell createCell(String text, Font font, int alignment, boolean border) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", font));
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPaddingTop(1f);
        cell.setPaddingBottom(1f);
        cell.setPaddingLeft(0f);
        cell.setPaddingRight(0f);
        if (!border) {
            cell.setBorder(Rectangle.NO_BORDER);
        }
        return cell;
    }

    private static String formatMoney(BigDecimal amount) {
        if (amount == null) {
            return "0 đ";
        }
        return MoneyUtil.format(amount).replace(" ₫", " đ");
    }

    private static String safeText(String value, String fallback) {
        return (value != null && !value.isBlank()) ? value : fallback;
    }

    private static BaseFont loadFont() throws IOException {
        try (InputStream in = ReceiptPdfWriter.class.getClassLoader().getResourceAsStream(FONT_RESOURCE)) {
            if (in == null) {
                throw new IOException("Không tìm thấy font " + FONT_RESOURCE);
            }
            return BaseFont.createFont(FONT_RESOURCE, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true,
                    in.readAllBytes(), null);
        }
    }
}

