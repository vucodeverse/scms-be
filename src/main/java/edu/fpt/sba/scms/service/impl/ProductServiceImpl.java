package edu.fpt.sba.scms.service.impl;

import edu.fpt.sba.scms.common.audit.AuditLogService;
import edu.fpt.sba.scms.common.enums.AuditAction;
import edu.fpt.sba.scms.common.enums.ErrorCode;
import edu.fpt.sba.scms.common.enums.ProductSizeName;
import edu.fpt.sba.scms.common.enums.ProductStatus;
import edu.fpt.sba.scms.common.exception.ConflictException;
import edu.fpt.sba.scms.common.exception.InvalidFieldValueException;
import edu.fpt.sba.scms.common.exception.NotFoundException;
import edu.fpt.sba.scms.dto.request.SaveProductRequest;
import edu.fpt.sba.scms.dto.request.SaveProductToppingsRequest;
import edu.fpt.sba.scms.dto.request.query.ProductListQuery;
import edu.fpt.sba.scms.dto.response.ProductDetailDto;
import edu.fpt.sba.scms.dto.response.ProductDto;
import edu.fpt.sba.scms.dto.response.RecipeLineDto;
import edu.fpt.sba.scms.integration.inventory.IngredientLookupPort;
import edu.fpt.sba.scms.integration.inventory.IngredientLookupPort.IngredientInfo;
import edu.fpt.sba.scms.mapper.ProductMapper;
import edu.fpt.sba.scms.model.Category;
import edu.fpt.sba.scms.model.Product;
import edu.fpt.sba.scms.model.ProductTopping;
import edu.fpt.sba.scms.repository.CategoryRepository;
import edu.fpt.sba.scms.repository.ProductRepository;
import edu.fpt.sba.scms.repository.ProductSizeRepository;
import edu.fpt.sba.scms.repository.ProductToppingRepository;
import edu.fpt.sba.scms.repository.ProductSpecs;
import edu.fpt.sba.scms.repository.RecipeReadRepository;
import edu.fpt.sba.scms.repository.ToppingRepository;
import edu.fpt.sba.scms.service.ProductService;
import edu.fpt.sba.scms.service.support.MasterDataQueries;
import edu.fpt.sba.scms.service.support.MasterDataText;
import edu.fpt.sba.scms.service.support.ProductAssembler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** UC10 — Manage Products. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private static final String ENTITY = "product";
    private static final String RESOURCE_NAME = "sản phẩm";

    private static final Map<String, Sort> SORTS = Map.of(
            "name", Sort.by("name"),
            "basePrice", Sort.by("id"),
            "createdAt", Sort.by("createdAt"));
    private static final Sort DEFAULT_SORT = Sort.by("name", "id");

    private final ProductRepository productRepository;
    private final ProductSizeRepository productSizeRepository;
    private final CategoryRepository categoryRepository;
    private final ToppingRepository toppingRepository;
    private final ProductToppingRepository productToppingRepository;
    private final RecipeReadRepository recipeReadRepository;
    private final IngredientLookupPort ingredientLookupPort;
    private final ProductMapper productMapper;
    private final ProductAssembler productAssembler;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(readOnly = true)
    public Page<ProductDto> list(ProductListQuery query) {
        Sort sort = MasterDataQueries.sort(query.sortBy(), query.sortDir(), SORTS, DEFAULT_SORT);
        Page<Product> page = productRepository.findAll(ProductSpecs.filter(query.keyword(), query.categoryId(), query.status()),
                MasterDataQueries.pageable(query.page(), query.size(), sort));
        if (page.isEmpty()) {
            return page.map(product -> productMapper.toDto(product, null, null, List.of(), List.of()));
        }
        // Gom size/topping/tên danh mục cho cả trang một lần; Page.map chỉ gắn lại theo đúng thứ tự dòng.
        List<ProductDto> assembled = productAssembler.toDtos(page.getContent(), categoriesOf(page.getContent()));
        Iterator<ProductDto> items = assembled.iterator();
        return page.map(product -> items.next());
    }

    @Override
    @Transactional(readOnly = true)
    public ProductDetailDto detail(Integer id) {
        Product product = getProduct(id);
        Category category = productCategory(product);
        return productMapper.toDetail(product,
                category == null ? null : category.getName(),
                category == null ? null : category.getCode(),
                productAssembler.sizes(id),
                productAssembler.toppings(id),
                recipe(id, null));
    }

    @Override
    @Transactional
    public ProductDto create(SaveProductRequest request) {
        String name = MasterDataText.requireText(request.name());
        Category category = requireCategory(request.categoryId());
        if (productRepository.existsByName(name)) {
            throw new ConflictException(ErrorCode.DUPLICATE_NAME);
        }
        Product product = productRepository.save(Product.builder()
                .categoryId(category.getId())
                .name(name)
                .description(MasterDataText.trimToNull(request.description()))
                .imageUrl(MasterDataText.trimToNull(request.imageUrl()))
                .defaultSize(request.defaultSize())
                .status(request.status())
                .build());

        auditLogService.log(AuditAction.PRODUCT_SAVE, ENTITY, product.getId(), null, Map.of("name", name));
        log.info("Tạo sản phẩm: id={}, name={}, categoryId={}", product.getId(), name, category.getId());
        return productMapper.toDto(product, category.getName(), category.getCode(), List.of(), List.of());
    }

    @Override
    @Transactional
    public ProductDto update(Integer id, SaveProductRequest request) {
        Product product = getProduct(id);
        String name = MasterDataText.requireText(request.name());
        Category category = requireCategory(request.categoryId());
        if (productRepository.existsByNameAndIdNot(name, id)) {
            throw new ConflictException(ErrorCode.DUPLICATE_NAME);
        }
        String previousName = product.getName();
        product.setCategoryId(category.getId());
        product.setName(name);
        product.setDescription(MasterDataText.trimToNull(request.description()));
        product.setImageUrl(MasterDataText.trimToNull(request.imageUrl()));
        product.setDefaultSize(request.defaultSize());
        product.setStatus(request.status());

        auditLogService.log(AuditAction.PRODUCT_SAVE, ENTITY, id, Map.of("name", previousName),
                Map.of("name", name, "categoryId", category.getId(), "status", request.status().name()));
        return productMapper.toDto(product, category.getName(), category.getCode(),
                productAssembler.sizes(id), productAssembler.toppings(id));
    }

    @Override
    @Transactional
    public ProductDetailDto replaceToppings(Integer id, SaveProductToppingsRequest request) {
        requireProductForUpdate(id);
        validateToppings(request);

        productToppingRepository.deleteAllByProductId(id);
        productToppingRepository.flush(); // xoá trước khi chèn — tránh trùng khoá (product_id, topping_id)
        productToppingRepository.saveAll(request.items().stream()
                .map(item -> ProductTopping.builder()
                        .productId(id)
                        .toppingId(item.toppingId())
                        .extraPrice(item.extraPrice())
                        .isDefault(item.isDefault())
                        .build())
                .toList());

        auditLogService.log(AuditAction.PRICE_UPDATE, "product_topping", id, null,
                Map.of("toppingIds", request.items().stream().map(SaveProductToppingsRequest.Item::toppingId).toList()));
        log.info("Cập nhật topping sản phẩm: productId={}, count={}", id, request.items().size());
        return detail(id);
    }

    @Override
    @Transactional
    public ProductDto softDelete(Integer id) {
        Product product = getProduct(id);
        product.setStatus(ProductStatus.INACTIVE);
        auditLogService.log(AuditAction.PRODUCT_SAVE, ENTITY, id, null,
                Map.of("status", ProductStatus.INACTIVE.name()));
        return productMapper.toDto(product, null, null, List.of(), List.of());
    }

    @Override
    @Transactional
    public void hardDelete(Integer id) {
        Product product = requireProductForUpdate(id);
        long voucherLinks = productRepository.countVoucherLinks(id);
        if (voucherLinks > 0) {
            throw new ConflictException(ErrorCode.RESOURCE_CONFLICT,
                    "Sản phẩm đang được voucher sử dụng. Gỡ sản phẩm khỏi voucher trước khi xoá cứng.");
        }
        productToppingRepository.deleteAllByProductId(id);
        productSizeRepository.deleteAllByProductId(id);
        productRepository.delete(product);

        auditLogService.log(AuditAction.PRODUCT_DELETE, ENTITY, id, Map.of("name", product.getName()), null);
        log.info("Xoá cứng sản phẩm: id={}, name={}", id, product.getName());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecipeLineDto> recipe(Integer id, ProductSizeName size) {
        requireProductExists(id);
        List<RecipeRow> rows = recipeReadRepository.findRecipe(id, size == null ? null : size.name())
                .stream()
                .map(RecipeRow::of)
                .toList();
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Integer, IngredientInfo> ingredients = ingredientLookupPort.findByIds(
                rows.stream().map(RecipeRow::ingredientId).distinct().toList());

        return rows.stream()
                .map(row -> toRecipeLine(row, ingredients.get(row.ingredientId())))
                .toList();
    }

    private static RecipeLineDto toRecipeLine(RecipeRow row, IngredientInfo ingredient) {
        return new RecipeLineDto(row.ingredientId(),
                ingredient == null ? null : ingredient.name(),
                ingredient == null ? null : ingredient.baseUnit(),
                row.quantityBase(),
                row.sizeName() == null ? null : ProductSizeName.valueOf(row.sizeName()));
    }

    /** Dòng SQL gốc: {@code [ingredient_id, quantity_base, size]}. */
    private record RecipeRow(int ingredientId, BigDecimal quantityBase, String sizeName) {

        static RecipeRow of(Object[] row) {
            return new RecipeRow(((Number) row[0]).intValue(), (BigDecimal) row[1],
                    row[2] == null ? null : row[2].toString());
        }
    }

    /** Topping gửi lên phải tồn tại và không trùng — báo lỗi đầu vào thay vì để FK/UNIQUE ném 500. */
    private void validateToppings(SaveProductToppingsRequest request) {
        List<Integer> ids = request.items().stream().map(SaveProductToppingsRequest.Item::toppingId).toList();
        List<Integer> duplicates = ids.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .toList();
        if (!duplicates.isEmpty()) {
            throw new InvalidFieldValueException("items", "Topping bị lặp: " + duplicates + ".");
        }
        long found = toppingRepository.findAllById(ids).size();
        if (found != ids.size()) {
            throw new InvalidFieldValueException("items", "Có topping không tồn tại.");
        }
    }

    private Map<Integer, Category> categoriesOf(List<Product> products) {
        List<Integer> categoryIds = products.stream().map(Product::getCategoryId).distinct().toList();
        return categoryRepository.findAllById(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
    }

    private Category productCategory(Product product) {
        return categoryRepository.findById(product.getCategoryId()).orElse(null);
    }

    private Category requireCategory(Integer categoryId) {
        return categoryRepository.findById(categoryId).orElseThrow(() -> new NotFoundException("danh mục"));
    }

    private Product getProduct(Integer id) {
        return productRepository.findById(id).orElseThrow(() -> new NotFoundException(RESOURCE_NAME));
    }

    private void requireProductExists(Integer id) {
        if (!productRepository.existsById(id)) {
            throw new NotFoundException(RESOURCE_NAME);
        }
    }

    private Product requireProductForUpdate(Integer id) {
        return productRepository.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException(RESOURCE_NAME));
    }
}
