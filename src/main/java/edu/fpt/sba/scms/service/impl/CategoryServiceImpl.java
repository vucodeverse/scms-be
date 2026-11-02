package edu.fpt.sba.scms.service.impl;

import edu.fpt.sba.scms.common.audit.AuditLogService;
import edu.fpt.sba.scms.common.enums.AuditAction;
import edu.fpt.sba.scms.common.enums.BusinessErrorCode;
import edu.fpt.sba.scms.common.enums.ErrorCode;
import edu.fpt.sba.scms.common.enums.ProductStatus;
import edu.fpt.sba.scms.common.enums.RecordStatus;
import edu.fpt.sba.scms.common.exception.BusinessRuleException;
import edu.fpt.sba.scms.common.exception.ConflictException;
import edu.fpt.sba.scms.common.exception.InvalidFieldValueException;
import edu.fpt.sba.scms.common.exception.NotFoundException;
import edu.fpt.sba.scms.dto.request.OrderCategoryRequest;
import edu.fpt.sba.scms.dto.request.SaveCategoryRequest;
import edu.fpt.sba.scms.dto.request.SaveCategoryStatusRequest;
import edu.fpt.sba.scms.dto.request.query.CategoryListQuery;
import edu.fpt.sba.scms.dto.response.CategoryDetailDto;
import edu.fpt.sba.scms.dto.response.CategoryDto;
import edu.fpt.sba.scms.mapper.CategoryMapper;
import edu.fpt.sba.scms.model.Category;
import edu.fpt.sba.scms.model.Product;
import edu.fpt.sba.scms.repository.CategoryRepository;
import edu.fpt.sba.scms.repository.CategorySpecs;
import edu.fpt.sba.scms.repository.ProductRepository;
import edu.fpt.sba.scms.service.CategoryService;
import edu.fpt.sba.scms.service.support.MasterDataQueries;
import edu.fpt.sba.scms.service.support.MasterDataText;
import edu.fpt.sba.scms.service.support.ProductAssembler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** UC09 — Manage Menu Categories. Phân quyền ở controller; service chỉ lo nghiệp vụ + audit. */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private static final String ENTITY = "category";
    private static final String RESOURCE_NAME = "danh mục";

    /** Whitelist {@code sortBy} của query → trường entity. */
    private static final Map<String, Sort> SORTS = Map.of(
            "sortOrder", Sort.by("sortOrder"),
            "code", Sort.by("code"),
            "name", Sort.by("name"),
            "createdAt", Sort.by("createdAt"));
    /** Thêm {@code id} làm khoá phụ để trang ổn định khi nhiều danh mục trùng sortOrder. */
    private static final Sort DEFAULT_SORT = Sort.by("sortOrder", "id");
    /** Trạng thái "vẫn đang bán" của sản phẩm — chặn ngừng danh mục. */
    private static final Set<ProductStatus> SELLING_STATUSES = Set.of(ProductStatus.AVAILABLE, ProductStatus.UNAVAILABLE);

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CategoryMapper categoryMapper;
    private final ProductAssembler productAssembler;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(readOnly = true)
    public Page<CategoryDto> list(CategoryListQuery query) {
        Sort sort = MasterDataQueries.sort(query.sortBy(), query.sortDir(), SORTS, DEFAULT_SORT);
        Page<Category> page = categoryRepository.findAll(CategorySpecs.managedList(query.keyword(), query.status()),
                MasterDataQueries.pageable(query.page(), query.size(), sort));
        if (page.isEmpty()) {
            return page.map(category -> categoryMapper.toDto(category, 0));
        }
        Map<Integer, Long> counts = countProducts(page.getContent());
        return page.map(category -> categoryMapper.toDto(category, counts.getOrDefault(category.getId(), 0L)));
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryDetailDto detail(Integer id) {
        Category category = getCategory(id);
        Map<Integer, Category> categories = Map.of(category.getId(), category);
        return categoryMapper.toDetail(category,
                productAssembler.toDtos(productRepository.findAllByCategoryIdOrderByNameAsc(id), categories));
    }

    @Override
    @Transactional
    public CategoryDto create(SaveCategoryRequest request) {
        String name = MasterDataText.requireText(request.name());
        if (categoryRepository.existsByName(name)) {
            throw new ConflictException(ErrorCode.DUPLICATE_NAME);
        }
        Category category = categoryRepository.saveAndFlush(Category.builder()
                .name(name)
                .description(MasterDataText.trimToNull(request.description()))
                .sortOrder(request.sortOrder())
                .status(request.status())
                .build());
        category.assignGeneratedCode();
        List<Integer> productIds = assignProducts(category.getId(), request.productIds());

        auditLogService.log(AuditAction.MENU_CATEGORY_SAVE, ENTITY, category.getId(), null,
                Map.of("code", category.getCode(), "name", name));
        log.info("Tạo danh mục: id={}, code={}, products={}", category.getId(), category.getCode(),
                productIds == null ? 0 : productIds.size());
        return categoryMapper.toDto(category, productIds == null ? 0 : productIds.size());
    }

    @Override
    @Transactional
    public CategoryDto update(Integer id, SaveCategoryRequest request) {
        Category category = getEditableCategory(id);
        String name = MasterDataText.requireText(request.name());
        if (categoryRepository.existsByNameAndIdNot(name, id)) {
            throw new ConflictException(ErrorCode.DUPLICATE_NAME);
        }
        String previousName = category.getName();
        category.setName(name);
        category.setDescription(MasterDataText.trimToNull(request.description()));
        category.setSortOrder(request.sortOrder());
        category.setStatus(request.status());
        List<Integer> productIds = assignProducts(id, request.productIds());

        auditLogService.log(AuditAction.MENU_CATEGORY_SAVE, ENTITY, id, Map.of("name", previousName),
                Map.of("name", name, "status", request.status().name()));
        return categoryMapper.toDto(category, productIds == null ? productRepository.countByCategoryId(id)
                : productIds.size());
    }

    @Override
    @Transactional
    public CategoryDto changeStatus(Integer id, SaveCategoryStatusRequest request) {
        Category category = getEditableCategory(id);
        if (request.status() == RecordStatus.INACTIVE) {
            ensureNoSellingProducts(id);
        }
        category.setStatus(request.status());

        auditLogService.log(AuditAction.MENU_CATEGORY_STATUS, ENTITY, id, null,
                Map.of("status", request.status().name()));
        return categoryMapper.toDto(category, productRepository.countByCategoryId(id));
    }

    /**
     * Thay danh sách sản phẩm của danh mục khi request có {@code productIds}.
     * <ul>
     *   <li>null = không gửi field → giữ nguyên (trả null để caller tự đếm).</li>
     *   <li>Sản phẩm đang ở danh mục khác bị chuyển sang danh mục này; sản phẩm của danh mục bị gỡ khỏi danh sách
     *       được đưa về {@code UNCATEGORIZED} (§10.7).</li>
     * </ul>
     *
     * @return id sản phẩm sau khi gán (để đếm cho DTO), hoặc null khi request không gửi field này
     */
    private List<Integer> assignProducts(Integer categoryId, List<Integer> requestedIds) {
        if (requestedIds == null) {
            return null;
        }
        List<Integer> ids = requestedIds.stream().distinct().toList();
        Map<Integer, Product> products = productRepository.lockAllByIdIn(ids).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        List<Integer> missing = ids.stream().filter(productId -> !products.containsKey(productId)).toList();
        if (!missing.isEmpty()) {
            throw new InvalidFieldValueException("productIds", "Sản phẩm không tồn tại: " + missing + ".");
        }
        products.values().forEach(product -> product.moveTo(categoryId));

        Category uncategorized = getSystemCategory();
        productRepository.lockAllByCategoryId(categoryId).stream()
                .filter(product -> !ids.contains(product.getId()))
                .forEach(product -> product.moveTo(uncategorized.getId()));
        return ids;
    }

    @Override
    @Transactional
    public void reorder(OrderCategoryRequest request) {
        ensureNoDuplicateIds(request);
        Map<Integer, Category> categories = categoryRepository
                .lockAllByIdIn(request.items().stream().map(OrderCategoryRequest.Item::categoryId).toList())
                .stream()
                .filter(category -> !category.isSystemCategory())
                .collect(Collectors.toMap(Category::getId, Function.identity()));
        ensureAllExist(request, categories);

        request.items().forEach(item -> categories.get(item.categoryId()).setSortOrder(item.sortOrder()));
        auditLogService.log(AuditAction.MENU_CATEGORY_SAVE, ENTITY, null, null,
                Map.of("reordered", request.items().size()));
        log.info("Sắp xếp lại {} danh mục", request.items().size());
    }

    @Override
    @Transactional
    public CategoryDto softDelete(Integer id) {
        return changeStatus(id, new SaveCategoryStatusRequest(RecordStatus.INACTIVE));
    }

    @Override
    @Transactional
    public void hardDelete(Integer id) {
        Category category = getCategory(id);
        if (category.isSystemCategory()) {
            throw new ConflictException(ErrorCode.RESOURCE_CONFLICT,
                    "Không thể xoá danh mục hệ thống " + Category.UNCATEGORIZED_CODE + ".");
        }
        if (!productRepository.lockAllByCategoryId(id).isEmpty()) {
            throw new ConflictException(ErrorCode.RESOURCE_CONFLICT,
                    "Danh mục còn sản phẩm. Chuyển sản phẩm sang danh mục khác hoặc xoá sản phẩm trước, "
                            + "hoặc dùng xoá mềm.");
        }
        if (categoryRepository.countVoucherLinks(id) > 0) {
            throw new ConflictException(ErrorCode.RESOURCE_CONFLICT,
                    "Danh mục đang được voucher sử dụng. Gỡ danh mục khỏi voucher trước khi xoá cứng.");
        }
        categoryRepository.delete(category);
        auditLogService.log(AuditAction.MENU_CATEGORY_DELETE, ENTITY, id,
                Map.of("code", category.getCode(), "name", category.getName()), null);
        log.info("Xoá cứng danh mục: id={}, code={}", id, category.getCode());
    }

    private Category getCategory(Integer id) {
        return categoryRepository.findById(id).orElseThrow(() -> new NotFoundException(RESOURCE_NAME));
    }

    /** Danh mục hệ thống UNCAT là nơi hứng sản phẩm bị gỡ: không cho sửa tên/trạng thái. */
    private Category getEditableCategory(Integer id) {
        Category category = getCategory(id);
        if (category.isSystemCategory()) {
            throw new ConflictException(ErrorCode.RESOURCE_CONFLICT,
                    "Danh mục hệ thống " + Category.UNCATEGORIZED_CODE + " không được sửa.");
        }
        return category;
    }

    private void ensureNoSellingProducts(Integer categoryId) {
        if (productRepository.existsByCategoryIdAndStatusIn(categoryId, SELLING_STATUSES)) {
            throw new BusinessRuleException(BusinessErrorCode.CATEGORY_NOT_EMPTY);
        }
    }

    private static void ensureNoDuplicateIds(OrderCategoryRequest request) {
        Set<Integer> duplicates = request.items().stream()
                .map(OrderCategoryRequest.Item::categoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        if (!duplicates.isEmpty()) {
            throw new InvalidFieldValueException("items", "Danh mục bị lặp trong danh sách sắp xếp: " + duplicates + ".");
        }
    }

    private static void ensureAllExist(OrderCategoryRequest request, Map<Integer, Category> found) {
        List<Integer> missing = request.items().stream()
                .map(OrderCategoryRequest.Item::categoryId)
                .filter(categoryId -> !found.containsKey(categoryId))
                .toList();
        if (!missing.isEmpty()) {
            throw new InvalidFieldValueException("items",
                    "Danh mục không tồn tại hoặc là danh mục hệ thống: " + missing + ".");
        }
    }

    private Category getSystemCategory() {
        return categoryRepository.findByCode(Category.UNCATEGORIZED_CODE)
                .orElseThrow(() -> new IllegalStateException(
                        "Danh mục hệ thống " + Category.UNCATEGORIZED_CODE + " chưa được seed."));
    }

    private Map<Integer, Long> countProducts(List<Category> categories) {
        List<Integer> ids = categories.stream().map(Category::getId).toList();
        return productRepository.countByCategoryIds(ids).stream()
                .collect(Collectors.toMap(row -> (Integer) row[0], row -> (Long) row[1]));
    }
}
