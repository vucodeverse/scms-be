package edu.fpt.sba.scms.domain;

import edu.fpt.sba.scms.common.enums.RecordStatus;
import edu.fpt.sba.scms.domain.voucher.VoucherEligibilityPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Domain: VoucherEligibilityPolicy Unit Tests")
class VoucherEligibilityPolicyTest {

    @Test
    @DisplayName("isActive: Kiểm tra trạng thái ACTIVE")
    void isActive_checksRecordStatus() {
        assertTrue(VoucherEligibilityPolicy.isActive(RecordStatus.ACTIVE));
        assertFalse(VoucherEligibilityPolicy.isActive(RecordStatus.INACTIVE));
        assertFalse(VoucherEligibilityPolicy.isActive(null));
    }

    @Test
    @DisplayName("isWithinPeriod: Kiểm tra khoảng thời gian hiệu lực")
    void isWithinPeriod_validatesDateRange() {
        Instant now = Instant.now();
        Instant yesterday = now.minus(1, ChronoUnit.DAYS);
        Instant tomorrow = now.plus(1, ChronoUnit.DAYS);

        // Hợp lệ: trong khoảng [yesterday, tomorrow]
        assertTrue(VoucherEligibilityPolicy.isWithinPeriod(now, yesterday, tomorrow));

        // Chưa đến ngày bắt đầu
        assertFalse(VoucherEligibilityPolicy.isWithinPeriod(yesterday, now, tomorrow));

        // Đã quá ngày kết thúc
        assertFalse(VoucherEligibilityPolicy.isWithinPeriod(tomorrow, yesterday, now));

        // Không giới hạn startDate hoặc endDate
        assertTrue(VoucherEligibilityPolicy.isWithinPeriod(now, null, tomorrow));
        assertTrue(VoucherEligibilityPolicy.isWithinPeriod(now, yesterday, null));
    }

    @Test
    @DisplayName("isUsageLimitReached: Kiểm tra số lần sử dụng tối đa")
    void isUsageLimitReached_checksUsageCount() {
        // Chưa chạm giới hạn
        assertFalse(VoucherEligibilityPolicy.isUsageLimitReached(100, 50));
        assertFalse(VoucherEligibilityPolicy.isUsageLimitReached(null, 50));

        // Đã chạm hoặc vượt giới hạn
        assertTrue(VoucherEligibilityPolicy.isUsageLimitReached(100, 100));
        assertTrue(VoucherEligibilityPolicy.isUsageLimitReached(100, 105));
    }

    @Test
    @DisplayName("meetsMinimumOrderAmount: Kiểm tra giá trị đơn hàng tối thiểu")
    void meetsMinimumOrderAmount_checksOrderTotal() {
        // Không yêu cầu min amount
        assertTrue(VoucherEligibilityPolicy.meetsMinimumOrderAmount(BigDecimal.valueOf(50000), null));

        // Đạt và vượt min amount
        assertTrue(VoucherEligibilityPolicy.meetsMinimumOrderAmount(BigDecimal.valueOf(100000), BigDecimal.valueOf(100000)));
        assertTrue(VoucherEligibilityPolicy.meetsMinimumOrderAmount(BigDecimal.valueOf(150000), BigDecimal.valueOf(100000)));

        // Chưa đạt min amount
        assertFalse(VoucherEligibilityPolicy.meetsMinimumOrderAmount(BigDecimal.valueOf(80000), BigDecimal.valueOf(100000)));
    }
}
