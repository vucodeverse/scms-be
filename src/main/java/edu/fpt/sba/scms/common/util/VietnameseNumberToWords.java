package edu.fpt.sba.scms.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tiện ích chuyển đổi số tiền VND sang chữ tiếng Việt phục vụ in hóa đơn / biên lai (UC22).
 */
public final class VietnameseNumberToWords {

    private static final String[] DIGITS = {
            "không", "một", "hai", "ba", "bốn", "năm", "sáu", "bảy", "tám", "chín"
    };

    private static final String[] SCALE_UNITS = {
            "", " ngàn", " triệu", " tỷ", " ngàn tỷ", " triệu tỷ"
    };

    private VietnameseNumberToWords() {
    }

    public static String convert(BigDecimal amount) {
        if (amount == null) {
            return "Không đồng";
        }
        long value = amount.setScale(0, RoundingMode.HALF_UP).longValue();
        if (value <= 0) {
            return "Không đồng";
        }

        List<String> parts = new ArrayList<>();
        int scaleIdx = 0;
        long temp = value;

        while (temp > 0) {
            int chunk = (int) (temp % 1000);
            temp /= 1000;
            if (chunk > 0) {
                boolean readFull = (temp > 0);
                String chunkWords = readThreeDigits(chunk, readFull);
                String unit = scaleIdx < SCALE_UNITS.length ? SCALE_UNITS[scaleIdx] : "";
                parts.add(chunkWords + unit);
            }
            scaleIdx++;
        }

        Collections.reverse(parts);
        String combined = String.join(" ", parts).trim();
        if (combined.isEmpty()) {
            return "Không đồng";
        }

        return Character.toUpperCase(combined.charAt(0)) + combined.substring(1) + " đồng";
    }

    private static String readThreeDigits(int number, boolean readFullHundred) {
        int hundreds = number / 100;
        int tens = (number % 100) / 10;
        int units = number % 10;
        List<String> words = new ArrayList<>(4);

        if (hundreds > 0 || readFullHundred) {
            words.add(DIGITS[hundreds] + " trăm");
        }

        if (tens > 1) {
            words.add(DIGITS[tens] + " mươi");
            if (units == 1) {
                words.add("mốt");
            } else if (units == 5) {
                words.add("lăm");
            } else if (units > 0) {
                words.add(DIGITS[units]);
            }
        } else if (tens == 1) {
            words.add("mười");
            if (units == 5) {
                words.add("lăm");
            } else if (units > 0) {
                words.add(DIGITS[units]);
            }
        } else {
            if ((hundreds > 0 || readFullHundred) && units > 0) {
                words.add("lẻ " + DIGITS[units]);
            } else if (units > 0) {
                words.add(DIGITS[units]);
            }
        }

        return String.join(" ", words);
    }
}

