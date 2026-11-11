package edu.fpt.sba.scms.service.impl;

import edu.fpt.sba.scms.common.enums.ProductSizeName;
import edu.fpt.sba.scms.common.enums.StockTransactionType;
import edu.fpt.sba.scms.dto.PageResponse;
import edu.fpt.sba.scms.dto.request.query.InventoryQuery;
import edu.fpt.sba.scms.dto.response.IngredientStockDto;
import edu.fpt.sba.scms.dto.response.LowStockAlertDto;
import edu.fpt.sba.scms.model.Branch;
import edu.fpt.sba.scms.model.Ingredient;
import edu.fpt.sba.scms.model.Inventory;
import edu.fpt.sba.scms.model.Order;
import edu.fpt.sba.scms.model.OrderItemTopping;
import edu.fpt.sba.scms.model.OrderLine;
import edu.fpt.sba.scms.model.Recipe;
import edu.fpt.sba.scms.model.StockTransaction;
import edu.fpt.sba.scms.model.Topping;
import edu.fpt.sba.scms.model.User;
import edu.fpt.sba.scms.repository.BranchRepository;
import edu.fpt.sba.scms.repository.IngredientRepository;
import edu.fpt.sba.scms.repository.InventoryRepository;
import edu.fpt.sba.scms.repository.OrderItemToppingRepository;
import edu.fpt.sba.scms.repository.OrderLineRepository;
import edu.fpt.sba.scms.repository.OrderRepository;
import edu.fpt.sba.scms.repository.RecipeRepository;
import edu.fpt.sba.scms.repository.StockTransactionRepository;
import edu.fpt.sba.scms.repository.ToppingRepository;
import edu.fpt.sba.scms.repository.UserRepository;
import edu.fpt.sba.scms.security.BranchContext;
import edu.fpt.sba.scms.security.principal.UserPrincipal;
import edu.fpt.sba.scms.service.InventoryService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Triển khai nghiệp vụ UC33 (Tra cứu sổ kho & Cảnh báo tồn thấp)
 * và FEA-03 (Khấu trừ tồn quầy bar tự động theo công thức pha chế BOM khi bán POS).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private static final String DEFAULT_CATEGORY_NAME = "Nguyên vật liệu";
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final InventoryRepository inventoryRepository;
    private final BranchRepository branchRepository;
    private final OrderRepository orderRepository;
    private final OrderLineRepository orderLineRepository;
    private final OrderItemToppingRepository orderItemToppingRepository;
    private final RecipeRepository recipeRepository;
    private final IngredientRepository ingredientRepository;
    private final StockTransactionRepository stockTransactionRepository;
    private final ToppingRepository toppingRepository;
    private final UserRepository userRepository;

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

    /**
     * FEA-03: Khấu trừ tồn kho Quầy Bar tự động theo BOM khi bán POS.
     * Áp dụng Pessimistic Write Lock findForUpdate, Rule 1 (Non-blocking POS cho phép âm kho),
     * Rule 2 (Ghi vết StockTransaction referenceCode qua reason), và Rule 3 (Xử lý Topping).
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deductStockForOrder(Long orderId) {
        if (orderId == null) {
            log.warn("deductStockForOrder: orderId is null, bỏ qua trừ kho.");
            return;
        }

        Order order = orderRepository.findById(orderId.intValue()).orElse(null);
        if (order == null) {
            log.warn("deductStockForOrder: Không tìm thấy đơn hàng ID={}, bỏ qua trừ kho.", orderId);
            return;
        }

        Integer branchId = order.getBranchId();
        if (branchId == null) {
            log.warn("deductStockForOrder: Đơn hàng ID={} không có branchId, bỏ qua trừ kho.", orderId);
            return;
        }

        Branch branch = branchRepository.findById(branchId).orElse(null);
        if (branch == null) {
            log.warn("deductStockForOrder: Không tìm thấy chi nhánh ID={} cho đơn hàng ID={}, bỏ qua trừ kho.",
                    branchId, orderId);
            return;
        }

        List<OrderLine> lines = orderLineRepository.findByOrderId(order.getId());
        if (lines == null || lines.isEmpty()) {
            log.info("deductStockForOrder: Đơn hàng ID={} không có món nào (lines rỗng).", orderId);
            return;
        }

        // Mã hóa đơn / referenceCode phục vụ kiểm kê cuối ngày
        String orderCode = (order.getReceiptNo() != null && !order.getReceiptNo().isBlank())
                ? order.getReceiptNo()
                : ("ORD-" + order.getId());

        // Phân giải thông tin người lập giao dịch (Thu ngân / Cashier)
        User createdByUser = null;
        if (order.getCashierId() != null) {
            createdByUser = userRepository.findById(order.getCashierId()).orElse(null);
        }
        if (createdByUser == null) {
            createdByUser = resolveCurrentUser();
        }

        // 1. Duyệt từng món trong orderLine và orderItemTopping để tính tổng lượng trừ theo nguyên vật liệu
        // Sử dụng TreeMap (sắp xếp tăng dần theo ingredientId) để tránh deadlock DB khi nhiều POS chạy đồng thời
        Map<Long, BigDecimal> deductionsByIngredient = new TreeMap<>();

        for (OrderLine line : lines) {
            int lineQty = line.getQuantity() != null ? line.getQuantity() : 0;
            if (lineQty <= 0) {
                continue;
            }

            // Phân giải size của sản phẩm (S, M, L hoặc null nếu chung)
            ProductSizeName sizeEnum = null;
            if (line.getSize() != null && !line.getSize().isBlank()) {
                try {
                    sizeEnum = ProductSizeName.valueOf(line.getSize().trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                    // Không khớp enum S/M/L -> dùng công thức chung size = null
                }
            }

            // Tra cứu bảng recipe theo productId và size
            if (line.getProductId() != null) {
                List<Recipe> productRecipes = recipeRepository.findApplicableRecipes(line.getProductId(), sizeEnum);
                if (productRecipes != null) {
                    for (Recipe recipe : productRecipes) {
                        if (recipe.getIngredientId() != null && recipe.getQuantityBase() != null) {
                            BigDecimal deductAmount = recipe.getQuantityBase().multiply(BigDecimal.valueOf(lineQty));
                            deductionsByIngredient.merge(Long.valueOf(recipe.getIngredientId()), deductAmount, BigDecimal::add);
                        }
                    }
                }
            }

            // Rule 3: Xử lý Topping đi kèm cho món
            List<OrderItemTopping> orderItemToppings = orderItemToppingRepository.findByOrderLineId(line.getId());
            if (orderItemToppings != null) {
                for (OrderItemTopping oit : orderItemToppings) {
                    Integer toppingId = oit.getToppingId();
                    if (toppingId == null) {
                        continue;
                    }

                    // Tra cứu công thức định lượng của Topping trong Recipe
                    List<Recipe> toppingRecipes = recipeRepository.findApplicableRecipes(toppingId, null);
                    if (toppingRecipes != null && !toppingRecipes.isEmpty()) {
                        for (Recipe tr : toppingRecipes) {
                            if (tr.getIngredientId() != null && tr.getQuantityBase() != null) {
                                BigDecimal deductAmount = tr.getQuantityBase().multiply(BigDecimal.valueOf(lineQty));
                                deductionsByIngredient.merge(Long.valueOf(tr.getIngredientId()), deductAmount, BigDecimal::add);
                            }
                        }
                    } else {
                        // Nếu không có Recipe riêng theo toppingId, map qua bảng Topping -> Ingredient cùng tên hoặc ID
                        Topping topping = toppingRepository.findById(toppingId).orElse(null);
                        if (topping != null) {
                            Ingredient matchedIng = ingredientRepository.findByNameIgnoreCase(topping.getName())
                                    .orElseGet(() -> ingredientRepository.findById(Long.valueOf(topping.getId())).orElse(null));
                            if (matchedIng != null) {
                                BigDecimal deductAmount = BigDecimal.valueOf(lineQty);
                                deductionsByIngredient.merge(matchedIng.getId(), deductAmount, BigDecimal::add);
                            } else {
                                log.warn("FEA-03: Không tìm thấy công thức hoặc nguyên vật liệu cho topping ID={} ('{}')",
                                        toppingId, topping.getName());
                            }
                        }
                    }
                }
            }
        }

        if (deductionsByIngredient.isEmpty()) {
            log.info("deductStockForOrder: Không có nguyên vật liệu BOM nào cần trừ cho đơn hàng ID={}.", orderId);
            return;
        }

        // 2. Khóa giao dịch Pessimistic Write Lock và trừ tồn
        Instant now = Instant.now();
        Long shiftIdLong = order.getShiftId() != null ? Long.valueOf(order.getShiftId()) : null;

        for (Map.Entry<Long, BigDecimal> entry : deductionsByIngredient.entrySet()) {
            Long ingredientId = entry.getKey();
            BigDecimal deductAmount = entry.getValue();
            if (deductAmount == null || deductAmount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            Ingredient ingredient = ingredientRepository.findById(ingredientId).orElse(null);
            if (ingredient == null) {
                log.warn("FEA-03: Bỏ qua trừ kho do không tìm thấy nguyên vật liệu ID={}", ingredientId);
                continue;
            }

            // Rule 4: Sử dụng findForUpdate (Pessimistic Write Lock)
            Inventory inventory = inventoryRepository.findForUpdate(branchId, ingredientId)
                    .orElseGet(() -> createDefaultInventory(branch, ingredient));

            BigDecimal currentQty = inventory.getCurrentQtyBase() != null
                    ? inventory.getCurrentQtyBase()
                    : BigDecimal.ZERO;

            // Rule 1 (Non-blocking POS): VẪN CHO PHÉP trừ âm kho tạm thời, TUYỆT ĐỐI KHÔNG rollback hay chặn thanh toán
            BigDecimal newQty = currentQty.subtract(deductAmount);
            inventory.setCurrentQtyBase(newQty);
            inventory.recalculateStockAndFlag(ingredient.getUnitsPerPack());
            inventoryRepository.save(inventory);

            // Rule 2 (Ghi vết minh bạch):
            // Lưu StockTransaction với type = ISSUE, reason = "AUTO_BOM: Order #" + order.getCode()
            BigDecimal unitCost = ingredient.getUnitCost() != null ? ingredient.getUnitCost() : BigDecimal.ZERO;
            BigDecimal lineTotal = deductAmount.multiply(unitCost).setScale(2, RoundingMode.HALF_UP);

            StockTransaction tx = StockTransaction.builder()
                    .branch(branch)
                    .ingredient(ingredient)
                    .type(StockTransactionType.ISSUE)
                    .shiftId(shiftIdLong)
                    .stocktakeId(null)
                    .createdBy(createdByUser)
                    .createdAt(now)
                    .cartons(BigDecimal.ZERO)
                    .looseUnits(deductAmount)
                    .quantityPacks(BigDecimal.ZERO)
                    .quantityBase(deductAmount)
                    .purchasePrice(unitCost)
                    .lineTotal(lineTotal)
                    .reason("AUTO_BOM: Order #" + orderCode)
                    .build();

            stockTransactionRepository.save(tx);

            log.info("FEA-03 BOM: Trừ kho thành công nguyên liệu '{}' (ID={}): lượng trừ={}, tồn mới={} (Order #{})",
                    ingredient.getName(), ingredient.getId(), deductAmount, newQty, orderCode);
        }
    }

    private Inventory createDefaultInventory(Branch branch, Ingredient ingredient) {
        BigDecimal safetyStock = ingredient.getUnitsPerPack() != null
                ? ingredient.getUnitsPerPack().multiply(new BigDecimal("3.0000"))
                : new BigDecimal("10.0000");

        Inventory inv = Inventory.builder()
                .branch(branch)
                .ingredient(ingredient)
                .currentQtyBase(BigDecimal.ZERO)
                .currentPackUnits(BigDecimal.ZERO)
                .safetyStockLevel(safetyStock)
                .lowStockFlag(true)
                .lastUpdated(Instant.now())
                .build();
        return inventoryRepository.saveAndFlush(inv);
    }

    private User resolveCurrentUser() {
        Integer branchUserId = BranchContext.current().map(BranchContext.Info::userId).orElse(null);
        if (branchUserId != null) {
            User user = userRepository.findById(branchUserId).orElse(null);
            if (user != null) {
                return user;
            }
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            User user = userRepository.findById(principal.userId()).orElse(null);
            if (user != null) {
                return user;
            }
        }

        return userRepository.findAll().stream().findFirst().orElse(null);
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
