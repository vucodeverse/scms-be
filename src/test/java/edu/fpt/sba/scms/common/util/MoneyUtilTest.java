package edu.fpt.sba.scms.common.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyUtilTest {

    @Test
    void formatVndWithGrouping() {
        assertThat(MoneyUtil.format(new BigDecimal("125000"))).isEqualTo("125.000 ₫");
        assertThat(MoneyUtil.format(new BigDecimal("0"))).isEqualTo("0 ₫");
        assertThat(MoneyUtil.format(null)).isEmpty();
    }

    @Test
    void parseIgnoresFormattingCharacters() {
        assertThat(MoneyUtil.parse("125.000 ₫")).isEqualByComparingTo("125000");
        assertThat(MoneyUtil.parse("  50,000đ ")).isEqualByComparingTo("50000");
        assertThat(MoneyUtil.parse("")).isEqualByComparingTo("0");
        assertThat(MoneyUtil.parse(null)).isEqualByComparingTo("0");
    }
}
