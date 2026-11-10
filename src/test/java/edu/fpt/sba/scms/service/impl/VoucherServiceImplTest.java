package edu.fpt.sba.scms.service.impl;

import edu.fpt.sba.scms.common.audit.AuditLogService;
import edu.fpt.sba.scms.common.enums.BusinessErrorCode;
import edu.fpt.sba.scms.common.enums.DiscountType;
import edu.fpt.sba.scms.common.enums.ProductStatus;
import edu.fpt.sba.scms.common.enums.RecordStatus;
import edu.fpt.sba.scms.common.exception.BusinessRuleException;
import edu.fpt.sba.scms.common.exception.InvalidFieldValueException;
import edu.fpt.sba.scms.dto.request.SaveVoucherRequest;
import edu.fpt.sba.scms.dto.request.SaveVoucherStatusRequest;
import edu.fpt.sba.scms.mapper.VoucherMapper;
import edu.fpt.sba.scms.model.Category;
import edu.fpt.sba.scms.model.Product;
import edu.fpt.sba.scms.model.Voucher;
import edu.fpt.sba.scms.repository.CategoryRepository;
import edu.fpt.sba.scms.repository.OrderVoucherRepository;
import edu.fpt.sba.scms.repository.ProductRepository;
import edu.fpt.sba.scms.repository.VoucherCategoryRepository;
import edu.fpt.sba.scms.repository.VoucherProductRepository;
import edu.fpt.sba.scms.repository.VoucherRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoucherServiceImplTest {

    @Mock
    private VoucherRepository voucherRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private VoucherProductRepository voucherProductRepository;
    @Mock
    private VoucherCategoryRepository voucherCategoryRepository;
    @Mock
    private OrderVoucherRepository orderVoucherRepository;
    @Mock
    private VoucherMapper voucherMapper;
    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private VoucherServiceImpl service;

    private static SaveVoucherRequest request(DiscountType type, String value, Instant start, Instant end) {
        return new SaveVoucherRequest("KHAITRUONG", type, new BigDecimal(value), null, start, end, null,
                RecordStatus.ACTIVE, null, null);
    }

    private static Voucher voucher(int usedCount) {
        Instant now = Instant.now();
        return Voucher.builder().id(1).code("KHAITRUONG").discountType(DiscountType.FIXED)
                .discountValue(BigDecimal.TEN).startDate(now).endDate(now.plus(1, ChronoUnit.DAYS))
                .usageLimit(null).usedCount(usedCount).status(RecordStatus.ACTIVE).build();
    }

    @Test
    void create_rejects_percent_above_100() {
        Instant now = Instant.now();
        assertThatThrownBy(() -> service.create(request(DiscountType.PERCENT, "101", now,
                now.plus(1, ChronoUnit.DAYS))))
                .isInstanceOf(InvalidFieldValueException.class)
                .hasMessageContaining("100");
        verify(voucherRepository, never()).save(any(Voucher.class));
    }

    @Test
    void create_rejects_end_date_not_after_start_date() {
        Instant now = Instant.now();
        assertThatThrownBy(() -> service.create(request(DiscountType.FIXED, "10", now, now)))
                .isInstanceOf(InvalidFieldValueException.class)
                .hasMessageContaining("Ngày kết thúc");
    }

    @Test
    void update_blocked_when_voucher_already_used() {
        when(voucherRepository.findByIdForUpdate(1)).thenReturn(Optional.of(voucher(3)));
        Instant now = Instant.now();

        assertThatThrownBy(() -> service.update(1, request(DiscountType.FIXED, "10", now,
                now.plus(1, ChronoUnit.DAYS))))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).detailCode())
                .isEqualTo(BusinessErrorCode.VOUCHER_IN_USE.name());
    }

    @Test
    void update_rejects_changing_voucher_code() {
        when(voucherRepository.findByIdForUpdate(1)).thenReturn(Optional.of(voucher(0)));
        Instant now = Instant.now();
        SaveVoucherRequest changedCode = new SaveVoucherRequest("NEWCODE", DiscountType.FIXED, BigDecimal.TEN, null,
                now, now.plus(1, ChronoUnit.DAYS), null, RecordStatus.ACTIVE, null, null);

        assertThatThrownBy(() -> service.update(1, changedCode))
                .isInstanceOf(InvalidFieldValueException.class)
                .hasMessageContaining("Mã voucher");
    }

    @Test
    void change_status_to_active_rejects_expired_voucher() {
        Instant now = Instant.now();
        Voucher expired = Voucher.builder().id(1).code("EXPIRED").discountType(DiscountType.FIXED)
                .discountValue(BigDecimal.TEN).startDate(now.minus(5, ChronoUnit.DAYS))
                .endDate(now.minus(1, ChronoUnit.DAYS)).usedCount(0).status(RecordStatus.INACTIVE).build();
        when(voucherRepository.findByIdForUpdate(1)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.changeStatus(1, new SaveVoucherStatusRequest(RecordStatus.ACTIVE)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).detailCode())
                .isEqualTo(BusinessErrorCode.VOUCHER_EXPIRED.name());
    }

    @Test
    void hard_delete_blocked_when_voucher_was_used() {
        when(voucherRepository.findByIdForUpdate(1)).thenReturn(Optional.of(voucher(1)));

        assertThatThrownBy(() -> service.hardDelete(1))
                .isInstanceOf(BusinessRuleException.class);
        verify(voucherRepository, never()).delete(any(Voucher.class));
    }

    @Test
    void hard_delete_blocked_when_order_voucher_reference_exists() {
        when(voucherRepository.findByIdForUpdate(1)).thenReturn(Optional.of(voucher(0)));
        when(orderVoucherRepository.existsByVoucherId(1)).thenReturn(true);

        assertThatThrownBy(() -> service.hardDelete(1))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).detailCode())
                .isEqualTo(BusinessErrorCode.VOUCHER_IN_USE.name());
        verify(voucherRepository, never()).delete(any(Voucher.class));
    }

    @Test
    void hard_delete_removes_scopes_then_voucher() {
        Voucher voucher = voucher(0);
        when(voucherRepository.findByIdForUpdate(1)).thenReturn(Optional.of(voucher));
        when(orderVoucherRepository.existsByVoucherId(1)).thenReturn(false);

        service.hardDelete(1);

        verify(voucherProductRepository).deleteAllByVoucherId(1);
        verify(voucherCategoryRepository).deleteAllByVoucherId(1);
        verify(voucherRepository).delete(voucher);
    }

    @Test
    void create_rejects_unknown_product_in_scope() {
        Instant now = Instant.now();
        SaveVoucherRequest request = new SaveVoucherRequest("KHAITRUONG", DiscountType.FIXED, BigDecimal.TEN, null,
                now, now.plus(1, ChronoUnit.DAYS), null, RecordStatus.ACTIVE, List.of(5), List.of());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(InvalidFieldValueException.class)
                .hasMessageContaining("sản phẩm");
    }

    @Test
    void create_rejects_inactive_product_in_scope() {
        Instant now = Instant.now();
        when(productRepository.findAllById(List.of(5))).thenReturn(List.of(
                Product.builder().id(5).name("Ngừng bán").status(ProductStatus.INACTIVE).build()));
        SaveVoucherRequest request = new SaveVoucherRequest("KHAITRUONG", DiscountType.FIXED, BigDecimal.TEN, null,
                now, now.plus(1, ChronoUnit.DAYS), null, RecordStatus.ACTIVE, List.of(5), List.of());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(InvalidFieldValueException.class)
                .hasMessageContaining("ngừng kinh doanh");
    }

    @Test
    void create_rejects_inactive_category_in_scope() {
        Instant now = Instant.now();
        when(categoryRepository.findAllById(List.of(3))).thenReturn(List.of(
                Category.builder().id(3).code("CAT3").name("Ngừng").status(RecordStatus.INACTIVE).build()));
        SaveVoucherRequest request = new SaveVoucherRequest("KHAITRUONG", DiscountType.FIXED, BigDecimal.TEN, null,
                now, now.plus(1, ChronoUnit.DAYS), null, RecordStatus.ACTIVE, List.of(), List.of(3));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(InvalidFieldValueException.class)
                .hasMessageContaining("ngừng hoạt động");
    }
}
