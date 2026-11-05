package edu.fpt.sba.scms.service.report;

import edu.fpt.sba.scms.common.enums.ErrorCode;
import edu.fpt.sba.scms.common.exception.InvalidFieldValueException;
import edu.fpt.sba.scms.dto.request.query.report.ReportPeriodQuery;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportPeriodTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);
    private static final int MAX = 366;

    @Test
    void haiDauTrongThiLaHomNay() {
        ReportPeriod p = ReportPeriod.of(null, null, MAX, TODAY);
        assertThat(p.from()).isEqualTo(TODAY);
        assertThat(p.to()).isEqualTo(TODAY);
    }

    @Test
    void thieuToThiBangFrom() {
        ReportPeriod p = ReportPeriod.of(LocalDate.of(2026, 10, 1), null, MAX, TODAY);
        assertThat(p.to()).isEqualTo(LocalDate.of(2026, 10, 1));
    }

    @Test
    void thieuFromThiBangTo() {
        ReportPeriod p = ReportPeriod.of(null, LocalDate.of(2026, 10, 1), MAX, TODAY);
        assertThat(p.from()).isEqualTo(LocalDate.of(2026, 10, 1));
    }

    @Test
    void dungDuocVoiQueryDto() {
        ReportPeriod p = ReportPeriod.of(
                new ReportPeriodQuery(1, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 5)), MAX, TODAY);
        assertThat(p.dayCount()).isEqualTo(5);
    }

    @Test
    void fromLonHonToBiTuChoi400() {
        assertThatThrownBy(() -> ReportPeriod.of(LocalDate.of(2026, 11, 6), LocalDate.of(2026, 11, 5), MAX, TODAY))
                .isInstanceOf(InvalidFieldValueException.class)
                .hasMessage(ReportPeriod.MSG_FROM_AFTER_TO)
                .satisfies(e -> {
                    InvalidFieldValueException ex = (InvalidFieldValueException) e;
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(ex.getField()).isEqualTo("from");
                });
    }

    @Test
    void dungBangMaxNgayThiHopLe() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        ReportPeriod p = ReportPeriod.of(from, from.plusDays(MAX - 1), MAX, TODAY);
        assertThat(p.dayCount()).isEqualTo(MAX);
    }

    @Test
    void vuotMaxNgayBiTuChoi() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        assertThatThrownBy(() -> ReportPeriod.of(from, from.plusDays(MAX), MAX, TODAY))
                .isInstanceOf(InvalidFieldValueException.class)
                .hasMessageContaining("366");
    }

    @Test
    void motNgayVnLaNuaMoTu17hUtcHomTruoc() {
        ReportPeriod p = ReportPeriod.ofDay(TODAY);
        assertThat(p.startInclusive()).isEqualTo(Instant.parse("2026-11-04T17:00:00Z"));
        assertThat(p.endExclusive()).isEqualTo(Instant.parse("2026-11-05T17:00:00Z"));
    }

    @Test
    void bienNgayTheoGioVn() {
        ReportPeriod p = ReportPeriod.ofDay(TODAY);
        // 00:30 VN ngày 05/11 = 17:30 UTC ngày 04/11 -> thuộc ngày 05/11
        assertThat(p.contains(Instant.parse("2026-11-04T17:30:00Z"))).isTrue();
        // 23:30 VN ngày 05/11 = 16:30 UTC ngày 05/11 -> vẫn thuộc ngày 05/11
        assertThat(p.contains(Instant.parse("2026-11-05T16:30:00Z"))).isTrue();
        // 23:59:59 VN ngày 04/11 -> không thuộc
        assertThat(p.contains(Instant.parse("2026-11-04T16:59:59Z"))).isFalse();
        // 00:00 VN ngày 06/11 -> không thuộc (đầu cuối mở)
        assertThat(p.contains(Instant.parse("2026-11-05T17:00:00Z"))).isFalse();
    }

    @Test
    void datesDienDuMoiNgay() {
        ReportPeriod p = ReportPeriod.of(LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 2), MAX, TODAY);
        assertThat(p.dates()).containsExactly(
                LocalDate.of(2026, 10, 30), LocalDate.of(2026, 10, 31),
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2));
        assertThat(p.dayCount()).isEqualTo(4);
    }

    @Test
    void kyTruocCungDoDai() {
        ReportPeriod p = ReportPeriod.of(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 7), MAX, TODAY);
        ReportPeriod prev = p.previous();
        assertThat(prev.from()).isEqualTo(LocalDate.of(2026, 10, 25));
        assertThat(prev.to()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(ReportPeriod.ofDay(TODAY).previous()).isEqualTo(ReportPeriod.ofDay(TODAY.minusDays(1)));
    }
}
