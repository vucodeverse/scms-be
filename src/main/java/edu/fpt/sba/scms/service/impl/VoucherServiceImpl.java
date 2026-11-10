package edu.fpt.sba.scms.service.impl;

import edu.fpt.sba.scms.common.audit.AuditLogService;
import edu.fpt.sba.scms.common.enums.AuditAction;
import edu.fpt.sba.scms.common.enums.BusinessErrorCode;
import edu.fpt.sba.scms.common.enums.DiscountType;
import edu.fpt.sba.scms.common.enums.ErrorCode;
import edu.fpt.sba.scms.common.enums.ProductStatus;
import edu.fpt.sba.scms.common.enums.RecordStatus;
import edu.fpt.sba.scms.common.exception.BusinessRuleException;
import edu.fpt.sba.scms.common.exception.ConflictException;
import edu.fpt.sba.scms.common.exception.InvalidFieldValueException;
import edu.fpt.sba.scms.common.exception.NotFoundException;
import edu.fpt.sba.scms.dto.request.SaveVoucherRequest;
import edu.fpt.sba.scms.dto.request.SaveVoucherStatusRequest;
import edu.fpt.sba.scms.dto.request.query.VoucherListQuery;
import edu.fpt.sba.scms.dto.response.CategoryRefDto;
import edu.fpt.sba.scms.dto.response.ProductRefDto;
import edu.fpt.sba.scms.dto.response.VoucherDetailDto;
import edu.fpt.sba.scms.dto.response.VoucherDto;
import edu.fpt.sba.scms.mapper.VoucherMapper;
import edu.fpt.sba.scms.model.Category;
import edu.fpt.sba.scms.model.Product;
import edu.fpt.sba.scms.model.Voucher;
import edu.fpt.sba.scms.model.VoucherCategory;
import edu.fpt.sba.scms.model.VoucherProduct;
import edu.fpt.sba.scms.repository.CategoryRepository;
import edu.fpt.sba.scms.repository.OrderVoucherRepository;
import edu.fpt.sba.scms.repository.ProductRepository;
import edu.fpt.sba.scms.repository.VoucherCategoryRepository;
import edu.fpt.sba.scms.repository.VoucherProductRepository;
import edu.fpt.sba.scms.repository.VoucherRepository;
import edu.fpt.sba.scms.repository.VoucherSpecs;
import edu.fpt.sba.scms.service.VoucherService;
import edu.fpt.sba.scms.service.support.MasterDataQueries;
import edu.fpt.sba.scms.service.support.MasterDataText;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** UC13 — Manage Vouchers. */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoucherServiceImpl implements VoucherService {

    private static final String ENTITY = "voucher";
    private static final String RESOURCE_NAME = "voucher";
    private static final BigDecimal PERCENT_MAX = BigDecimal.valueOf(100);

    private static final Map<String, Sort> SORTS = Map.of(
            "code", Sort.by("code"),
            "startDate", Sort.by("startDate"),
            "endDate", Sort.by("endDate"),
            "createdAt", Sort.by("createdAt"));
    /** Voucher mới nhất lên đầu — thứ tự thao tác thường gặp của COO. */
    private static final Sort DEFAULT_SORT = Sort.by("createdAt", "id");

    private final VoucherRepository voucherRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final VoucherProductRepository voucherProductRepository;
    private final VoucherCategoryRepository voucherCategoryRepository;
    private final OrderVoucherRepository orderVoucherRepository;
    private final VoucherMapper voucherMapper;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(readOnly = true)
    public Page<VoucherDto> list(VoucherListQuery query) {
        Sort sort = MasterDataQueries.sort(query.sortBy(), query.sortDir(), SORTS, DEFAULT_SORT);
        return voucherRepository.findAll(VoucherSpecs.filter(query.keyword(), query.status()),
                MasterDataQueries.pageable(query.page(), query.size(), sort)).map(voucherMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public VoucherDetailDto detail(Integer id) {
        Voucher voucher = getVoucher(id);
        return voucherMapper.toDetail(voucher, productsOf(voucher.getId()), categoriesOf(voucher.getId()));
    }

    @Override
    @Transactional
    public VoucherDetailDto create(SaveVoucherRequest request) {
        String code = MasterDataText.requireText(request.code());
        if (voucherRepository.existsByCode(code)) {
            throw new ConflictException(ErrorCode.DUPLICATE_CODE);
        }
        validateRequest(request);
        List<Integer> productIds = requireProducts(request.productIds());
        List<Integer> categoryIds = requireCategories(request.categoryIds());

        Voucher voucher = voucherRepository.save(Voucher.builder()
                .code(code)
                .discountType(request.discountType())
                .discountValue(request.discountValue())
                .minOrderAmount(request.minOrderAmount())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .usageLimit(request.usageLimit())
                .usedCount(0)
                .status(request.status())
                .build());
        replaceScope(voucher.getId(), productIds, categoryIds);

        auditLogService.log(AuditAction.VOUCHER_SAVE, ENTITY, voucher.getId(), null, Map.of("code", code));
        log.info("Tạo voucher: id={}, code={}", voucher.getId(), code);
        return detail(voucher.getId());
    }

    @Override
    @Transactional
    public VoucherDetailDto update(Integer id, SaveVoucherRequest request) {
        Voucher voucher = lockVoucher(id);
        if (voucher.getUsedCount() > 0 || orderVoucherRepository.existsByVoucherId(id)) {
            throw new BusinessRuleException(BusinessErrorCode.VOUCHER_IN_USE,
                    BusinessErrorCode.VOUCHER_IN_USE.message() + " Tạo voucher mới nếu cần thay đổi điều kiện.");
        }
        String submittedCode = MasterDataText.requireText(request.code());
        if (!submittedCode.equals(voucher.getCode())) {
            throw new InvalidFieldValueException("code", "Mã voucher không thể thay đổi sau khi tạo.");
        }
        validateRequest(request);
        List<Integer> productIds = requireProducts(request.productIds());
        List<Integer> categoryIds = requireCategories(request.categoryIds());

        String previousCode = voucher.getCode();
        voucher.setDiscountType(request.discountType());
        voucher.setDiscountValue(request.discountValue());
        voucher.setMinOrderAmount(request.minOrderAmount());
        voucher.setStartDate(request.startDate());
        voucher.setEndDate(request.endDate());
        voucher.setUsageLimit(request.usageLimit());
        voucher.setStatus(request.status());
        replaceScope(id, productIds, categoryIds);

        auditLogService.log(AuditAction.VOUCHER_SAVE, ENTITY, id, Map.of("code", previousCode),
                Map.of("code", previousCode, "status", request.status().name()));
        return detail(id);
    }

    @Override
    @Transactional
    public VoucherDto changeStatus(Integer id, SaveVoucherStatusRequest request) {
        Voucher voucher = lockVoucher(id);
        if (request.status() == RecordStatus.ACTIVE) {
            if (voucher.getEndDate() != null && voucher.getEndDate().isBefore(Instant.now())) {
                throw new BusinessRuleException(BusinessErrorCode.VOUCHER_EXPIRED,
                        "Voucher đã hết hạn, không thể kích hoạt lại.");
            }
            if (voucher.getUsageLimit() != null && voucher.getUsedCount() >= voucher.getUsageLimit()) {
                throw new BusinessRuleException(BusinessErrorCode.VOUCHER_USAGE_EXCEEDED,
                        "Voucher đã hết lượt sử dụng, không thể kích hoạt lại.");
            }
        }
        voucher.setStatus(request.status());
        auditLogService.log(AuditAction.VOUCHER_STATUS, ENTITY, id, null,
                Map.of("status", request.status().name()));
        return voucherMapper.toDto(voucher);
    }

    @Override
    @Transactional
    public VoucherDto softDelete(Integer id) {
        return changeStatus(id, new SaveVoucherStatusRequest(RecordStatus.INACTIVE));
    }

    @Override
    @Transactional
    public void hardDelete(Integer id) {
        Voucher voucher = lockVoucher(id);
        if (voucher.getUsedCount() > 0 || orderVoucherRepository.existsByVoucherId(id)) {
            throw new BusinessRuleException(BusinessErrorCode.VOUCHER_IN_USE,
                    "Voucher đã được dùng hoặc đang gắn với đơn hàng. Chỉ có thể xoá mềm để giữ lịch sử.");
        }
        voucherProductRepository.deleteAllByVoucherId(id);
        voucherCategoryRepository.deleteAllByVoucherId(id);
        voucherRepository.delete(voucher);

        auditLogService.log(AuditAction.VOUCHER_DELETE, ENTITY, id, Map.of("code", voucher.getCode()), null);
        log.info("Xoá cứng voucher: id={}, code={}", id, voucher.getCode());
    }

    /** Rule liên field của payload — lỗi ở đây là đầu vào sai nên trả 400 kèm tên field. */
    private static void validateRequest(SaveVoucherRequest request) {
        if (request.discountType() == DiscountType.PERCENT && request.discountValue().compareTo(PERCENT_MAX) > 0) {
            throw new InvalidFieldValueException("discountValue", "Giảm giá phần trăm không được vượt quá 100.");
        }
        if (!request.startDate().isBefore(request.endDate())) {
            throw new InvalidFieldValueException("endDate", "Ngày kết thúc phải sau ngày bắt đầu.");
        }
        if (request.endDate().isBefore(Instant.now())) {
            throw new InvalidFieldValueException("endDate", "Ngày kết thúc không được ở quá khứ.");
        }
    }

    private void replaceScope(Integer voucherId, List<Integer> productIds, List<Integer> categoryIds) {
        voucherProductRepository.deleteAllByVoucherId(voucherId);
        voucherCategoryRepository.deleteAllByVoucherId(voucherId);
        voucherProductRepository.flush(); // xoá trước khi chèn — tránh trùng khoá (voucher_id, product_id)
        voucherProductRepository.saveAll(productIds.stream()
                .map(productId -> new VoucherProduct(voucherId, productId))
                .toList());
        voucherCategoryRepository.saveAll(categoryIds.stream()
                .map(categoryId -> new VoucherCategory(voucherId, categoryId))
                .toList());
    }

    /** null = "không gửi field" → coi như áp cho mọi sản phẩm (rỗng). */
    private List<Integer> requireProducts(List<Integer> productIds) {
        List<Integer> ids = distinct(productIds);
        if (ids.isEmpty()) {
            return ids;
        }
        List<Product> found = productRepository.findAllById(ids);
        if (found.size() != ids.size()) {
            throw new InvalidFieldValueException("productIds", "Có sản phẩm không tồn tại.");
        }
        List<Integer> inactiveIds = found.stream()
                .filter(p -> p.getStatus() == ProductStatus.INACTIVE)
                .map(Product::getId)
                .toList();
        if (!inactiveIds.isEmpty()) {
            throw new InvalidFieldValueException("productIds",
                    "Không thể gắn sản phẩm đã ngừng kinh doanh vào voucher: " + inactiveIds + ".");
        }
        return ids;
    }

    private List<Integer> requireCategories(List<Integer> categoryIds) {
        List<Integer> ids = distinct(categoryIds);
        if (ids.isEmpty()) {
            return ids;
        }
        List<Category> found = categoryRepository.findAllById(ids);
        if (found.size() != ids.size()) {
            throw new InvalidFieldValueException("categoryIds", "Có danh mục không tồn tại.");
        }
        List<Integer> invalidIds = found.stream()
                .filter(c -> c.isSystemCategory() || c.getStatus() != RecordStatus.ACTIVE)
                .map(Category::getId)
                .toList();
        if (!invalidIds.isEmpty()) {
            throw new InvalidFieldValueException("categoryIds",
                    "Không thể gắn danh mục hệ thống hoặc danh mục đã ngừng hoạt động vào voucher: " + invalidIds + ".");
        }
        return ids;
    }

    private List<ProductRefDto> productsOf(Integer voucherId) {
        List<Integer> ids = voucherProductRepository.findAllByVoucherId(voucherId).stream()
                .map(VoucherProduct::getProductId)
                .toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return productRepository.findAllById(ids).stream()
                .sorted(Comparator.comparing(Product::getName))
                .map(product -> new ProductRefDto(product.getId(), product.getName()))
                .toList();
    }

    private List<CategoryRefDto> categoriesOf(Integer voucherId) {
        List<Integer> ids = voucherCategoryRepository.findAllByVoucherId(voucherId).stream()
                .map(VoucherCategory::getCategoryId)
                .toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return categoryRepository.findAllById(ids).stream()
                .sorted(Comparator.comparing(Category::getName))
                .map(category -> new CategoryRefDto(category.getId(), category.getName()))
                .toList();
    }

    private Voucher getVoucher(Integer id) {
        return voucherRepository.findById(id).orElseThrow(() -> new NotFoundException(RESOURCE_NAME));
    }

    private Voucher lockVoucher(Integer id) {
        return voucherRepository.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException(RESOURCE_NAME));
    }

    private static List<Integer> distinct(Collection<Integer> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }
}
