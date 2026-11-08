package edu.fpt.sba.scms.integration.export;

import edu.fpt.sba.scms.common.enums.ReportColumnType;
import edu.fpt.sba.scms.common.util.MoneyUtil;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Định dạng một ô thành chữ theo kiểu cột (dùng cho PDF và để ước lượng độ rộng cột Excel).
 */
final class CellText {

    private static final DecimalFormatSymbols VN = vnSymbols();

    private CellText() {
    }

    static String format(Object value, ReportColumnType type) {
        if (value == null) {
            return "";
        }
        if (!(value instanceof Number number)) {
            return value.toString();
        }
        BigDecimal decimal = new BigDecimal(number.toString());
        return switch (type) {
            case MONEY -> MoneyUtil.format(decimal);
            case INTEGER -> new DecimalFormat("#,##0", VN).format(decimal);
            case DECIMAL -> new DecimalFormat("#,##0.##", VN).format(decimal);
            case PERCENT -> new DecimalFormat("0.00", VN).format(decimal) + "%";
            case TEXT -> decimal.toPlainString();
        };
    }

    private static DecimalFormatSymbols vnSymbols() {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.of("vi", "VN"));
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        return symbols;
    }
}
