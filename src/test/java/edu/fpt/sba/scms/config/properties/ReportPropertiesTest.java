package edu.fpt.sba.scms.config.properties;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportPropertiesTest {

    @Test
    void thieuKhoaThiDungMacDinhTheoSrs() {
        ReportProperties p = ReportProperties.defaults();

        assertThat(p.lateGraceMinutes()).isEqualTo(5);
        assertThat(p.maxRangeDays()).isEqualTo(366);
        assertThat(p.openHour()).isEqualTo(6);
        assertThat(p.closeHour()).isEqualTo(23);
        assertThat(p.hourSlotCount()).isEqualTo(17);
        assertThat(p.peakTierRatio()).isEqualByComparingTo("0.8");
        assertThat(p.topProductsLimit()).isEqualTo(10);
        assertThat(p.maxTopProducts()).isEqualTo(100);
    }

    @Test
    void bindTuYmlKebabCase() {
        Map<String, String> yml = Map.of(
                "app.report.late-grace-minutes", "10",
                "app.report.max-range-days", "31",
                "app.report.peak-tier-ratio", "0.75",
                "app.report.top-products-limit", "5");

        ReportProperties p = new Binder(new MapConfigurationPropertySource(yml))
                .bind("app.report", ReportProperties.class).get();

        assertThat(p.lateGraceMinutes()).isEqualTo(10);
        assertThat(p.maxRangeDays()).isEqualTo(31);
        assertThat(p.peakTierRatio()).isEqualByComparingTo("0.75");
        assertThat(p.topProductsLimit()).isEqualTo(5);
        assertThat(p.openHour()).isEqualTo(6);
    }

    @Test
    void gioMoCuaPhaiNhoHonGioDongCua() {
        assertThatThrownBy(() -> new ReportProperties(null, 23, 23, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("open-hour");
    }

    @Test
    void gioNgoaiKhoangBiTuChoi() {
        assertThatThrownBy(() -> new ReportProperties(null, -1, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReportProperties(null, null, 25, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void peakTierRatioPhaiTrongKhoang0Den1() {
        assertThatThrownBy(() -> new ReportProperties(null, null, null, BigDecimal.ZERO, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReportProperties(null, null, null, new BigDecimal("1.01"), null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new ReportProperties(null, null, null, BigDecimal.ONE, null, null, null).peakTierRatio())
                .isEqualByComparingTo("1");
    }

    @Test
    void khoangNgayVaTopLimitPhaiHopLe() {
        assertThatThrownBy(() -> new ReportProperties(0, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReportProperties(null, null, null, null, 0, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReportProperties(null, null, null, null, 20, 10, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-top-products");
    }
}
