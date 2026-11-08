package edu.fpt.sba.scms.service.impl;

import edu.fpt.sba.scms.dto.PageResponse;
import edu.fpt.sba.scms.dto.request.query.InventoryQuery;
import edu.fpt.sba.scms.dto.response.IngredientStockDto;
import edu.fpt.sba.scms.dto.response.LowStockAlertDto;
import edu.fpt.sba.scms.model.Branch;
import edu.fpt.sba.scms.model.Ingredient;
import edu.fpt.sba.scms.model.Inventory;
import edu.fpt.sba.scms.repository.BranchRepository;
import edu.fpt.sba.scms.repository.InventoryRepository;
import edu.fpt.sba.scms.security.BranchContext;
import edu.fpt.sba.scms.service.InventoryService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Triển khai nghiệp vụ UC33 — Tra cứu sổ kho & Cảnh báo tồn thấp.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private static final String DEFAULT_CATEGORY_NAME = "Nguyên vật liệu";
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final InventoryRepository inventoryRepository;
    private final BranchRepository branchRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<IngredientStockDto> getBranchInventory(InventoryQuery query) {
        Integer targetBranchId = resolveBranchId(query != null && query.branchId() != null
                ? query.branchId().intValue() : null);

        Specification<Inventory> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("branch").get("id"), targetBranchId));

            if (query != null && query.keyword() != null && !query.keyword().isBlank()) {
                String kw = "%" + query.keyword().trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("ingredient").get("name")), kw));
            }

            if (query != null && "LOW_STOCK".equalsIgnoreCase(query.status())) {
                predicates.add(cb.isTrue(root.get("lowStockFlag")));
            } else if (query != null && "SUFFICIENT".equalsIgnoreCase(query.status())) {
                predicates.add(cb.isFalse(root.get("lowStockFlag")));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        int pageIndex = (query != null && query.page() != null && query.page() >= 0) ? query.page() : 0;
        int pageSize = (query != null && query.size() != null && query.size() > 0)
                ? Math.min(query.size(), 100) : DEFAULT_PAGE_SIZE;

        Sort sort = Sort.by("ingredient.name").ascending();
        if (query != null && query.sortDir() != null && query.sortDir().name().equalsIgnoreCase("DESC")) {
            sort = Sort.by("ingredient.name").descending();
        }

        Pageable pageable = PageRequest.of(pageIndex, pageSize, sort);
        Page<Inventory> page = inventoryRepository.findAll(spec, pageable);

        List<IngredientStockDto> dtos = page.getContent().stream()
                .map(this::toIngredientStockDto)
                .toList();

        return new PageResponse<>(dtos, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public List<LowStockAlertDto> getLowStockAlerts(Integer explicitBranchId) {
        Integer targetBranchId = resolveBranchId(explicitBranchId);
        List<Inventory> lowStockItems = inventoryRepository.findByBranchIdAndLowStockFlagTrue(targetBranchId);

        return lowStockItems.stream()
                .map(this::toLowStockAlertDto)
                .toList();
    }

    private IngredientStockDto toIngredientStockDto(Inventory inv) {
        Ingredient ing = inv.getIngredient();
        BigDecimal unitCost = ing.getUnitCost() != null ? ing.getUnitCost() : BigDecimal.ZERO;
        BigDecimal currentQty = inv.getCurrentQtyBase() != null ? inv.getCurrentQtyBase() : BigDecimal.ZERO;
        BigDecimal holdingValue = currentQty.multiply(unitCost).setScale(0, RoundingMode.HALF_UP);
        String ingredientCode = String.format("ING-%03d", ing.getId());

        return new IngredientStockDto(
                inv.getId(),
                ing.getId(),
                ingredientCode,
                ing.getName(),
                DEFAULT_CATEGORY_NAME,
                ing.getPackUnit(),
                ing.getBaseUnit(),
                ing.getUnitsPerPack(),
                inv.getCurrentPackUnits(),
                inv.getCurrentQtyBase(),
                inv.getSafetyStockLevel(),
                inv.getLowStockFlag(),
                unitCost,
                holdingValue,
                inv.getLastUpdated()
        );
    }

    private LowStockAlertDto toLowStockAlertDto(Inventory inv) {
        Ingredient ing = inv.getIngredient();
        BigDecimal unitsPerPack = ing.getUnitsPerPack() != null && ing.getUnitsPerPack().compareTo(BigDecimal.ZERO) > 0
                ? ing.getUnitsPerPack() : BigDecimal.ONE;

        BigDecimal currentQty = inv.getCurrentQtyBase() != null ? inv.getCurrentQtyBase() : BigDecimal.ZERO;
        BigDecimal safetyStock = inv.getSafetyStockLevel() != null ? inv.getSafetyStockLevel() : BigDecimal.ZERO;

        BigDecimal deficit = safetyStock.subtract(currentQty);
        if (deficit.compareTo(BigDecimal.ZERO) < 0) {
            deficit = BigDecimal.ZERO;
        }

        int suggestedPacks = deficit.divide(unitsPerPack, 0, RoundingMode.CEILING).intValue();
        BigDecimal unitCost = ing.getUnitCost() != null ? ing.getUnitCost() : BigDecimal.ZERO;
        BigDecimal estimatedCost = BigDecimal.valueOf(suggestedPacks)
                .multiply(unitsPerPack)
                .multiply(unitCost)
                .setScale(0, RoundingMode.HALF_UP);

        String ingredientCode = String.format("ING-%03d", ing.getId());

        return new LowStockAlertDto(
                ing.getId(),
                ingredientCode,
                ing.getName(),
                ing.getPackUnit(),
                ing.getBaseUnit(),
                unitsPerPack,
                currentQty,
                safetyStock,
                deficit,
                suggestedPacks,
                estimatedCost
        );
    }

    private Integer resolveBranchId(Integer requestedBranchId) {
        if (BranchContext.current().isPresent()) {
            Integer scoped = BranchContext.resolveBranchScope(requestedBranchId);
            if (scoped != null) {
                return scoped;
            }
        }
        if (requestedBranchId != null) {
            return requestedBranchId;
        }
        return branchRepository.findAll().stream()
                .findFirst()
                .map(Branch::getId)
                .orElse(1);
    }
}
