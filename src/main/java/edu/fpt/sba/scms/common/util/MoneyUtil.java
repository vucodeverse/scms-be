package edu.fpt.sba.scms.common.util;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** VND nguyên, không dùng float (IMPLEMENTATION.md §8.7). */
public final class MoneyUtil {

    public static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final DecimalFormat FORMAT;

    static {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.of("vi", "VN"));
        symbols.setGroupingSeparator('.');
        FORMAT = new DecimalFormat("#,##0", symbols);
    }

    private MoneyUtil() {
    }

    public static String format(BigDecimal amount) {
        return amount == null ? "" : FORMAT.format(amount) + " ₫";
    }

    /** Bỏ mọi ký tự không phải số/dấu âm, trả BigDecimal nguyên. */
    public static BigDecimal parse(CharSequence text) {
        if (text == null || text.toString().isBlank()) {
            return ZERO;
        }
        String digits = text.toString().trim().replaceAll("[^0-9-]", "");
        return digits.isEmpty() ? ZERO : new BigDecimal(digits);
    }

    public static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? ZERO : value;
    }
}
