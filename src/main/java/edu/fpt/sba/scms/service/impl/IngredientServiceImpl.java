package edu.fpt.sba.scms.service.impl;

import edu.fpt.sba.scms.common.enums.ErrorCode;
import edu.fpt.sba.scms.common.exception.ConflictException;
import edu.fpt.sba.scms.common.exception.NotFoundException;
import edu.fpt.sba.scms.dto.request.CreateIngredientRequest;
import edu.fpt.sba.scms.dto.request.UpdateIngredientRequest;
import edu.fpt.sba.scms.dto.response.IngredientDto;
import edu.fpt.sba.scms.model.Branch;
import edu.fpt.sba.scms.model.Ingredient;
import edu.fpt.sba.scms.model.Inventory;
import edu.fpt.sba.scms.repository.BranchRepository;
import edu.fpt.sba.scms.repository.IngredientRepository;
import edu.fpt.sba.scms.repository.InventoryRepository;
import edu.fpt.sba.scms.service.IngredientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IngredientServiceImpl implements IngredientService {

    private final IngredientRepository ingredientRepository;
    private final InventoryRepository inventoryRepository;
    private final BranchRepository branchRepository;

    @Override
    public List<IngredientDto> list(String status) {
        List<Ingredient> ingredients;
        if (status == null || status.isBlank() || "ACTIVE".equalsIgnoreCase(status)) {
            ingredients = ingredientRepository.findAllByStatusOrderByNameAsc("ACTIVE");
        } else if ("ALL".equalsIgnoreCase(status)) {
            ingredients = ingredientRepository.findAllByOrderByNameAsc();
        } else {
            ingredients = ingredientRepository.findAllByStatusOrderByNameAsc(status.trim().toUpperCase());
        }
        return ingredients.stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public IngredientDto getById(Long id) {
        Ingredient ingredient = ingredientRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.INGREDIENT_NOT_FOUND));
        return toDto(ingredient);
    }

    @Override
    @Transactional
    public IngredientDto create(CreateIngredientRequest request) {
        String trimmedName = request.name().trim();
        if (ingredientRepository.existsByNameIgnoreCase(trimmedName)) {
            throw new ConflictException(ErrorCode.DUPLICATE_NAME, "Tên nguyên vật liệu đã tồn tại.");
        }

        Ingredient ingredient = Ingredient.builder()
                .name(trimmedName)
                .baseUnit(request.baseUnit().trim())
                .packUnit(request.packUnit().trim())
                .unitsPerPack(request.unitsPerPack())
                .unitCost(request.unitCost())
                .status("ACTIVE")
                .build();

        Ingredient saved = ingredientRepository.save(ingredient);

        // Tự động khởi tạo bản ghi Inventory cho 3 Kho Tổng (branch_id = 1, 2, 3 / DC-HN, DC-DN, DC-HCM)
        initHubInventories(saved);

        return toDto(saved);
    }

    @Override
    @Transactional
    public IngredientDto update(Long id, UpdateIngredientRequest request) {
        Ingredient ingredient = ingredientRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.INGREDIENT_NOT_FOUND));

        String trimmedName = request.name().trim();
        if (ingredientRepository.existsByNameIgnoreCaseAndIdNot(trimmedName, id)) {
            throw new ConflictException(ErrorCode.DUPLICATE_NAME, "Tên nguyên vật liệu đã tồn tại.");
        }

        ingredient.setName(trimmedName);
        ingredient.setPackUnit(request.packUnit().trim());
        ingredient.setUnitsPerPack(request.unitsPerPack());
        ingredient.setUnitCost(request.unitCost());
        if (request.status() != null && !request.status().isBlank()) {
            ingredient.setStatus(request.status().trim().toUpperCase());
        }

        Ingredient updated = ingredientRepository.save(ingredient);
        return toDto(updated);
    }

    @Override
    @Transactional
    public IngredientDto toggleStatus(Long id) {
        return toggleStatus(id, null);
    }

    @Override
    @Transactional
    public IngredientDto toggleStatus(Long id, String status) {
        Ingredient ingredient = ingredientRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.INGREDIENT_NOT_FOUND));

        String newStatus;
        if (status == null || status.isBlank()) {
            newStatus = "ACTIVE".equalsIgnoreCase(ingredient.getStatus()) ? "INACTIVE" : "ACTIVE";
        } else {
            String target = status.trim().toUpperCase();
            if (!"ACTIVE".equals(target) && !"INACTIVE".equals(target)) {
                throw new IllegalArgumentException("Trạng thái phải là ACTIVE hoặc INACTIVE.");
            }
            newStatus = target;
        }

        ingredient.setStatus(newStatus);
        Ingredient updated = ingredientRepository.save(ingredient);
        log.info("Chuyển trạng thái nguyên vật liệu ID {} ({}) sang {}", id, updated.getName(), newStatus);
        return toDto(updated);
    }

    private void initHubInventories(Ingredient ingredient) {
        Set<Branch> targetBranches = new LinkedHashSet<>();

        // Tìm 3 Hub Kho Tổng Vùng theo code
        List<String> hubCodes = List.of("DC-HN", "DC-DN", "DC-HCM");
        for (String code : hubCodes) {
            branchRepository.findByCode(code).ifPresent(targetBranches::add);
        }

        // Tìm theo branch_id 1, 2, 3
        List<Integer> hubIds = List.of(1, 2, 3);
        for (Integer hubId : hubIds) {
            branchRepository.findById(hubId).ifPresent(targetBranches::add);
        }

        for (Branch branch : targetBranches) {
            if (!inventoryRepository.existsByBranch_IdAndIngredient_Id(branch.getId(), ingredient.getId())) {
                Inventory inv = Inventory.builder()
                        .branch(branch)
                        .ingredient(ingredient)
                        .currentQtyBase(BigDecimal.ZERO)
                        .currentPackUnits(BigDecimal.ZERO)
                        .safetyStockLevel(BigDecimal.ZERO)
                        .lowStockFlag(false)
                        .lastUpdated(Instant.now())
                        .build();
                inventoryRepository.save(inv);
                log.info("Khởi tạo Inventory cho Hub {} ({}) với nguyên vật liệu {}",
                        branch.getId(), branch.getCode(), ingredient.getName());
            }
        }
    }

    private IngredientDto toDto(Ingredient entity) {
        if (entity == null) {
            return null;
        }
        return new IngredientDto(
                entity.getId(),
                entity.getName(),
                entity.getBaseUnit(),
                entity.getPackUnit(),
                entity.getUnitsPerPack(),
                entity.getUnitCost(),
                entity.getStatus()
        );
    }
}
