package edu.fpt.sba.scms.service.impl;

import edu.fpt.sba.scms.common.audit.AuditLogService;
import edu.fpt.sba.scms.common.enums.BusinessErrorCode;
import edu.fpt.sba.scms.common.enums.ErrorCode;
import edu.fpt.sba.scms.common.enums.RecordStatus;
import edu.fpt.sba.scms.common.exception.BusinessRuleException;
import edu.fpt.sba.scms.common.exception.ConflictException;
import edu.fpt.sba.scms.common.exception.InvalidFieldValueException;
import edu.fpt.sba.scms.common.exception.NotFoundException;
import edu.fpt.sba.scms.dto.request.OrderCategoryRequest;
import edu.fpt.sba.scms.dto.request.SaveCategoryRequest;
import edu.fpt.sba.scms.dto.request.SaveCategoryStatusRequest;
import edu.fpt.sba.scms.dto.request.query.CategoryListQuery;
import edu.fpt.sba.scms.dto.response.CategoryDto;
import edu.fpt.sba.scms.mapper.CategoryMapper;
import edu.fpt.sba.scms.model.Category;
import edu.fpt.sba.scms.repository.CategoryRepository;
import edu.fpt.sba.scms.repository.ProductRepository;
import edu.fpt.sba.scms.service.support.ProductAssembler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private CategoryMapper categoryMapper;
    @Mock
    private ProductAssembler productAssembler;
    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private CategoryServiceImpl service;

    private static Category category(int id, String code, RecordStatus status) {
        return Category.builder().id(id).code(code).name("Cà phê " + id).sortOrder(1).status(status).build();
    }

    private static SaveCategoryRequest request(RecordStatus status) {
        return new SaveCategoryRequest(" Cà phê ", " Mô tả ", 2, status, null);
    }

    @Test
    void list_uses_default_page_size_10_when_query_omits_paging() {
        when(categoryRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(category(1, "CAT1", RecordStatus.ACTIVE))));
        Object[] countRow = {1, 3L};
        when(productRepository.countByCategoryIds(any())).thenReturn(List.<Object[]>of(countRow));
        when(categoryMapper.toDto(any(Category.class), eq(3L)))
                .thenReturn(new CategoryDto(1, "CAT1", "Cà phê", null, 1, RecordStatus.ACTIVE, 3, null, null));

        service.list(new CategoryListQuery(null, null, null, null, null, null));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(categoryRepository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
    }

    @Test
    void list_rejects_sort_field_outside_whitelist() {
        CategoryListQuery query = new CategoryListQuery(null, null, 1, 10, null, "dropTable");

        assertThatThrownBy(() -> service.list(query))
                .isInstanceOf(InvalidFieldValueException.class)
                .hasMessageContaining("dropTable");
    }

    @Test
    void detail_returns_not_found_when_missing() {
        when(categoryRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail(99))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("danh mục");
    }

    @Test
    void create_generates_code_from_seeded_id_and_trims_input() {
        when(categoryRepository.existsByName("Cà phê")).thenReturn(false);
        when(categoryRepository.saveAndFlush(any(Category.class))).thenAnswer(invocation -> {
            Category saved = invocation.getArgument(0);
            saved.setId(7);
            return saved;
        });
        when(categoryMapper.toDto(any(Category.class), eq(0L)))
                .thenAnswer(invocation -> {
                    Category saved = invocation.getArgument(0);
                    return new CategoryDto(saved.getId(), saved.getCode(), saved.getName(), saved.getDescription(),
                            saved.getSortOrder(), saved.getStatus(), 0, null, null);
                });

        CategoryDto dto = service.create(request(RecordStatus.ACTIVE));

        assertThat(dto.id()).isEqualTo(7);
        assertThat(dto.code()).isEqualTo("CAT7");
        assertThat(dto.description()).isEqualTo("Mô tả");
        verify(auditLogService).log(any(), eq("category"), eq(7), eq(null), any());
    }

    @Test
    void create_rejects_duplicate_name() {
        when(categoryRepository.existsByName("Cà phê")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request(RecordStatus.ACTIVE)))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_NAME);
        verify(categoryRepository, never()).saveAndFlush(any());
    }

    @Test
    void change_status_to_inactive_blocked_when_products_still_selling() {
        when(categoryRepository.findById(1)).thenReturn(Optional.of(category(1, "CAT1", RecordStatus.ACTIVE)));
        when(productRepository.existsByCategoryIdAndStatusIn(eq(1), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.changeStatus(1, new SaveCategoryStatusRequest(RecordStatus.INACTIVE)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).detailCode())
                .isEqualTo(BusinessErrorCode.CATEGORY_NOT_EMPTY.name());
    }

    @Test
    void update_to_inactive_blocked_when_selling_products_remain() {
        when(categoryRepository.findById(1)).thenReturn(Optional.of(category(1, "CAT1", RecordStatus.ACTIVE)));
        when(categoryRepository.existsByNameAndIdNot("Cà phê", 1)).thenReturn(false);
        when(productRepository.existsByCategoryIdAndStatusIn(eq(1), any())).thenReturn(true);

        assertThatThrownBy(() -> service.update(1, request(RecordStatus.INACTIVE)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).detailCode())
                .isEqualTo(BusinessErrorCode.CATEGORY_NOT_EMPTY.name());
    }

    @Test
    void hard_delete_blocked_when_category_still_has_products() {
        when(categoryRepository.findById(1)).thenReturn(Optional.of(category(1, "CAT1", RecordStatus.INACTIVE)));
        when(productRepository.lockAllByCategoryId(1)).thenReturn(List.of(edu.fpt.sba.scms.model.Product.builder()
                .id(5).categoryId(1).build()));

        assertThatThrownBy(() -> service.hardDelete(1))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("còn sản phẩm");
        verify(categoryRepository, never()).delete(any(Category.class));
    }

    @Test
    void hard_delete_blocked_for_system_category() {
        when(categoryRepository.findById(1))
                .thenReturn(Optional.of(category(1, Category.UNCATEGORIZED_CODE, RecordStatus.INACTIVE)));

        assertThatThrownBy(() -> service.hardDelete(1))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining(Category.UNCATEGORIZED_CODE);
    }

    @Test
    void hard_delete_only_when_no_references() {
        Category target = category(1, "CAT1", RecordStatus.INACTIVE);
        when(categoryRepository.findById(1)).thenReturn(Optional.of(target));
        when(productRepository.lockAllByCategoryId(1)).thenReturn(List.of());
        when(categoryRepository.countVoucherLinks(1)).thenReturn(0L);

        service.hardDelete(1);

        verify(categoryRepository).delete(target);
        verify(auditLogService).log(any(), eq("category"), eq(1), any(), eq(null));
    }

    @Test
    void reorder_rejects_unknown_category_id() {
        when(categoryRepository.lockAllByIdIn(List.of(1, 2))).thenReturn(List.of(category(1, "CAT1", RecordStatus.ACTIVE)));

        OrderCategoryRequest request = new OrderCategoryRequest(List.of(
                new OrderCategoryRequest.Item(1, 1),
                new OrderCategoryRequest.Item(2, 2)));

        assertThatThrownBy(() -> service.reorder(request))
                .isInstanceOf(InvalidFieldValueException.class)
                .hasMessageContaining("[2]");
    }

    @Test
    void reorder_rejects_duplicate_ids() {
        OrderCategoryRequest request = new OrderCategoryRequest(List.of(
                new OrderCategoryRequest.Item(1, 1),
                new OrderCategoryRequest.Item(1, 2)));

        assertThatThrownBy(() -> service.reorder(request))
                .isInstanceOf(InvalidFieldValueException.class)
                .hasMessageContaining("lặp");
    }

    @Test
    void reorder_rejects_duplicate_sort_order() {
        OrderCategoryRequest request = new OrderCategoryRequest(List.of(
                new OrderCategoryRequest.Item(1, 5),
                new OrderCategoryRequest.Item(2, 5)));

        assertThatThrownBy(() -> service.reorder(request))
                .isInstanceOf(InvalidFieldValueException.class)
                .hasMessageContaining("sortOrder");
    }

    @Test
    void reorder_writes_new_sort_order() {
        Category first = category(1, "CAT1", RecordStatus.ACTIVE);
        Category second = category(2, "CAT2", RecordStatus.ACTIVE);
        when(categoryRepository.lockAllByIdIn(List.of(2, 1))).thenReturn(List.of(first, second));

        service.reorder(new OrderCategoryRequest(List.of(
                new OrderCategoryRequest.Item(2, 1),
                new OrderCategoryRequest.Item(1, 2))));

        assertThat(second.getSortOrder()).isEqualTo(1);
        assertThat(first.getSortOrder()).isEqualTo(2);
        assertThat(Set.of(first.getSortOrder(), second.getSortOrder())).hasSize(2);
    }
}
