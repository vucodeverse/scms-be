package edu.fpt.sba.scms.service.impl;

import edu.fpt.sba.scms.common.audit.AuditLogService;
import edu.fpt.sba.scms.common.enums.AuditAction;
import edu.fpt.sba.scms.common.enums.ProductSizeName;
import edu.fpt.sba.scms.common.exception.InvalidFieldValueException;
import edu.fpt.sba.scms.common.exception.NotFoundException;
import edu.fpt.sba.scms.domain.recipe.RecipeCostCalculator;
import edu.fpt.sba.scms.dto.request.RecipeItemRequest;
import edu.fpt.sba.scms.dto.request.SaveProductRecipeRequest;
import edu.fpt.sba.scms.dto.response.ProductRecipeResponse;
import edu.fpt.sba.scms.dto.response.RecipeLineDto;
import edu.fpt.sba.scms.integration.inventory.IngredientLookupPort;
import edu.fpt.sba.scms.integration.inventory.IngredientLookupPort.IngredientInfo;
import edu.fpt.sba.scms.model.Product;
import edu.fpt.sba.scms.model.Recipe;
import edu.fpt.sba.scms.repository.ProductRepository;
import edu.fpt.sba.scms.repository.ProductSizeRepository;
import edu.fpt.sba.scms.repository.RecipeRepository;
import edu.fpt.sba.scms.security.principal.UserPrincipal;
import edu.fpt.sba.scms.service.RecipeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Hiện thực nghiệp vụ quản lý công thức định lượng (BOM) và giá vốn món (UC12).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecipeServiceImpl implements RecipeService {

    private final ProductRepository productRepository;
    private final ProductSizeRepository productSizeRepository;
    private final RecipeRepository recipeRepository;
    private final IngredientLookupPort ingredientLookupPort;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(readOnly = true)
    public ProductRecipeResponse getRecipe(Integer productId, ProductSizeName size) {
        Product product = requireProduct(productId);
        if (size != null) {
            validateProductHasSize(productId, size);
        }

        List<Recipe> recipes = recipeRepository.findApplicableRecipes(productId, size);
        List<RecipeLineDto> lines = buildRecipeLines(recipes);
        BigDecimal totalCogs = RecipeCostCalculator.calculateTotalCogs(
                lines.stream().map(RecipeLineDto::lineCost).toList());

        return new ProductRecipeResponse(productId, product.getName(), size, totalCogs, lines);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecipeLineDto> listRecipeLines(Integer productId, ProductSizeName size) {
        return getRecipe(productId, size).items();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductRecipeResponse saveRecipe(Integer productId, SaveProductRecipeRequest request, UserPrincipal actor) {
        Product product = requireProduct(productId);
        ProductSizeName size = request.size();
        if (size != null) {
            validateProductHasSize(productId, size);
        }

        List<RecipeItemRequest> items = request.items() != null ? request.items() : List.of();
        validateRecipeItems(items);

        List<Recipe> beforeRecipes = size == null
                ? recipeRepository.findByProductIdAndSizeIsNullOrderByIngredientIdAsc(productId)
                : recipeRepository.findByProductIdAndSizeOrderByIngredientIdAsc(productId, size);

        // Xoá công thức cũ của (productId, size)
        if (size == null) {
            recipeRepository.deleteByProductIdAndSizeIsNull(productId);
        } else {
            recipeRepository.deleteByProductIdAndSize(productId, size);
        }

        // Lưu công thức mới
        if (!items.isEmpty()) {
            List<Recipe> newEntities = items.stream()
                    .map(item -> Recipe.builder()
                            .productId(productId)
                            .ingredientId(item.ingredientId())
                            .size(size)
                            .quantityBase(item.quantityBase())
                            .build())
                    .toList();
            recipeRepository.saveAll(newEntities);
        }

        log.info("Cập nhật công thức sản phẩm {}: size={}, số dòng cũ={}, số dòng mới={}",
                productId, size, beforeRecipes.size(), items.size());

        auditLogService.log(AuditAction.RECIPE_UPDATE, "Recipe", productId,
                "size=" + size + ", count=" + beforeRecipes.size(),
                "size=" + size + ", count=" + items.size());

        return getRecipe(productId, size);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRecipe(Integer productId, ProductSizeName size, UserPrincipal actor) {
        requireProduct(productId);

        if (size == null) {
            recipeRepository.deleteByProductId(productId);
            log.info("Đã xoá toàn bộ công thức định lượng của sản phẩm {}", productId);
        } else {
            recipeRepository.deleteByProductIdAndSize(productId, size);
            log.info("Đã xoá công thức định lượng size {} của sản phẩm {}", size, productId);
        }

        auditLogService.log(AuditAction.RECIPE_UPDATE, "Recipe", productId,
                "DELETE size=" + size, null);
    }

    private Product requireProduct(Integer productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Sản phẩm"));
    }

    private void validateProductHasSize(Integer productId, ProductSizeName size) {
        boolean sizeExists = productSizeRepository.findByProductIdAndSize(productId, size).isPresent();
        if (!sizeExists) {
            throw new InvalidFieldValueException("size", "Size " + size + " chưa được cấu hình cho sản phẩm.");
        }
    }

    private void validateRecipeItems(List<RecipeItemRequest> items) {
        if (items.isEmpty()) {
            return;
        }

        Set<Integer> seen = new HashSet<>();
        List<Integer> duplicates = new ArrayList<>();
        for (RecipeItemRequest item : items) {
            if (!seen.add(item.ingredientId())) {
                duplicates.add(item.ingredientId());
            }
        }
        if (!duplicates.isEmpty()) {
            throw new InvalidFieldValueException("items", "Nguyên liệu trong công thức bị trùng lặp: " + duplicates);
        }

        List<Integer> ingredientIds = items.stream().map(RecipeItemRequest::ingredientId).toList();
        Map<Integer, IngredientInfo> ingredientMap = ingredientLookupPort.findByIds(ingredientIds);

        List<Integer> missingIds = ingredientIds.stream()
                .filter(id -> !ingredientMap.containsKey(id))
                .toList();
        if (!missingIds.isEmpty()) {
            throw new InvalidFieldValueException("items", "Có nguyên liệu không tồn tại trong hệ thống: " + missingIds);
        }

        List<Integer> inactiveIds = ingredientIds.stream()
                .filter(id -> {
                    IngredientInfo info = ingredientMap.get(id);
                    return info != null && !"ACTIVE".equalsIgnoreCase(info.status());
                })
                .toList();
        if (!inactiveIds.isEmpty()) {
            throw new InvalidFieldValueException("items", "Nguyên liệu không ở trạng thái hoạt động (ACTIVE): " + inactiveIds);
        }
    }

    private List<RecipeLineDto> buildRecipeLines(List<Recipe> recipes) {
        if (recipes.isEmpty()) {
            return List.of();
        }

        List<Integer> ingredientIds = recipes.stream()
                .map(Recipe::getIngredientId)
                .distinct()
                .toList();
        Map<Integer, IngredientInfo> ingredientMap = ingredientLookupPort.findByIds(ingredientIds);

        return recipes.stream()
                .map(r -> {
                    IngredientInfo info = ingredientMap.get(r.getIngredientId());
                    String name = info != null ? info.name() : null;
                    String unit = info != null ? info.baseUnit() : null;
                    BigDecimal unitCost = info != null && info.unitCost() != null ? info.unitCost() : BigDecimal.ZERO;
                    BigDecimal lineCost = RecipeCostCalculator.calculateLineCost(r.getQuantityBase(), unitCost);
                    return new RecipeLineDto(r.getIngredientId(), name, unit, r.getQuantityBase(), r.getSize(), unitCost, lineCost);
                })
                .toList();
    }
}

