package edu.fpt.sba.scms.dto.request.query.report;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ReportPeriodQueryTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void init() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void close() {
        factory.close();
    }

    @Test
    void moiThamSoDeuTuyChon() {
        assertThat(validator.validate(new ReportPeriodQuery(null, null, null))).isEmpty();
    }

    @Test
    void branchIdPhaiDuong() {
        assertThat(validator.validate(new ReportPeriodQuery(0, LocalDate.of(2026, 11, 1), null)))
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("branchId");
    }
}
