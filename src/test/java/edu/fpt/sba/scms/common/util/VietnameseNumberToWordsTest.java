package edu.fpt.sba.scms.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class VietnameseNumberToWordsTest {

    @Test
    @DisplayName("Chuyển đổi số 0 hoặc null trả về 'Không đồng'")
    void convertZeroOrNull() {
        assertThat(VietnameseNumberToWords.convert(null)).isEqualTo("Không đồng");
        assertThat(VietnameseNumberToWords.convert(BigDecimal.ZERO)).isEqualTo("Không đồng");
    }

    @Test
    @DisplayName("Chuyển đổi các mức tiền thường gặp trong hóa đơn cà phê")
    void convertCoffeeAmounts() {
        assertThat(VietnameseNumberToWords.convert(new BigDecimal("25000")))
                .isEqualTo("Hai mươi lăm ngàn đồng");
        assertThat(VietnameseNumberToWords.convert(new BigDecimal("130000")))
                .isEqualTo("Một trăm ba mươi ngàn đồng");
        assertThat(VietnameseNumberToWords.convert(new BigDecimal("225000")))
                .isEqualTo("Hai trăm hai mươi lăm ngàn đồng");
        assertThat(VietnameseNumberToWords.convert(new BigDecimal("1000000")))
                .isEqualTo("Một triệu đồng");
        assertThat(VietnameseNumberToWords.convert(new BigDecimal("1005000")))
                .isEqualTo("Một triệu không trăm lẻ năm ngàn đồng");
    }
}

