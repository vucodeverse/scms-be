package edu.fpt.sba.scms.common.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationUtilTest {

    @ParameterizedTest
    @ValueSource(strings = {"0912345678", "0355555555", "+84912345678", "0771234567"})
    void acceptsVietnamesePhones(String phone) {
        assertThat(ValidationUtil.VN_PHONE.matcher(phone).matches()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0123456789", "091234567", "09123456789", "abcdefghij", ""})
    void rejectsInvalidPhones(String phone) {
        assertThat(ValidationUtil.VN_PHONE.matcher(phone).matches()).isFalse();
    }

    @Test
    void pinMustBeFourDigits() {
        assertThat(ValidationUtil.PIN.matcher("0123").matches()).isTrue();
        assertThat(ValidationUtil.PIN.matcher("123").matches()).isFalse();
        assertThat(ValidationUtil.PIN.matcher("12a4").matches()).isFalse();
    }

    @Test
    void passwordRequiresEightCharsWithLetterAndDigit() {
        assertThat(ValidationUtil.PASSWORD.matcher("Admin@123").matches()).isTrue();
        assertThat(ValidationUtil.PASSWORD.matcher("abcdefgh").matches()).isFalse();
        assertThat(ValidationUtil.PASSWORD.matcher("12345678").matches()).isFalse();
        assertThat(ValidationUtil.PASSWORD.matcher("Abc123").matches()).isFalse();
    }
}
