package edu.fpt.sba.scms.config;

import edu.fpt.sba.scms.common.enums.BranchStatus;
import edu.fpt.sba.scms.model.Branch;
import edu.fpt.sba.scms.model.Ingredient;
import edu.fpt.sba.scms.model.Inventory;
import edu.fpt.sba.scms.repository.BranchRepository;
import edu.fpt.sba.scms.repository.IngredientRepository;
import edu.fpt.sba.scms.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Seed 10 nguyên vật liệu mẫu chuẩn F&B và tồn kho ban đầu cho môi trường DEV (CHECKLIST.md Phase 0).
 * Bật cùng cấu hình {@code app.auth.seed.enabled=true}.
 */
@Slf4j
@Component
@Order(20)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.auth.seed.enabled", havingValue = "true")
public class InventoryDataSeeder implements ApplicationRunner {

    private final IngredientRepository ingredientRepository;
    private final InventoryRepository inventoryRepository;
    private final BranchRepository branchRepository;

    private record SeedIngredient(
            String name,
            String baseUnit,
            String packUnit,
            BigDecimal unitsPerPack,
            BigDecimal unitCost,
            BigDecimal initialQtyBase,
            BigDecimal safetyStockLevel
    ) {
    }

    private static final List<SeedIngredient> SAMPLE_INGREDIENTS = List.of(
            // 1. Cà phê Robusta Đắk Lắk (Bao 5kg = 5,000g, giá vốn 750k/bao -> 150đ/g) - Case chạm ngưỡng Low Stock UC33
            new SeedIngredient("Hạt Cà phê Robusta Đắk Lắk", "g", "Bao 5kg",
                    new BigDecimal("5000.0000"), new BigDecimal("150.00"),
                    new BigDecimal("12000.0000"), new BigDecimal("20000.0000")),

            // 2. Cà phê Arabica Cầu Đất (Bao 5kg = 5,000g, giá vốn 1,200k/bao -> 240đ/g)
            new SeedIngredient("Hạt Cà phê Arabica Cầu Đất", "g", "Bao 5kg",
                    new BigDecimal("5000.0000"), new BigDecimal("240.00"),
                    new BigDecimal("15000.0000"), new BigDecimal("10000.0000")),

            // 3. Sữa tươi thanh trùng Dalat Milk (Thùng 12 hộp 1L, giá vốn 360k/thùng -> 30k/hộp) - Đủ hàng
            new SeedIngredient("Sữa tươi thanh trùng Dalat Milk", "hộp", "Thùng 12 hộp",
                    new BigDecimal("12.0000"), new BigDecimal("30000.00"),
                    new BigDecimal("98.0000"), new BigDecimal("50.0000")),

            // 4. Sữa đặc Ông Thọ đỏ (Thùng 24 lon, giá vốn 600k/thùng -> 25k/lon)
            new SeedIngredient("Sữa đặc Ông Thọ đỏ", "lon", "Thùng 24 lon",
                    new BigDecimal("24.0000"), new BigDecimal("25000.00"),
                    new BigDecimal("72.0000"), new BigDecimal("48.0000")),

            // 5. Đường cát Biên Hòa (Bao 50kg = 50,000g, giá vốn 1,100k/bao -> 22đ/g)
            new SeedIngredient("Đường cát Biên Hòa", "g", "Bao 50kg",
                    new BigDecimal("50000.0000"), new BigDecimal("22.00"),
                    new BigDecimal("120000.0000"), new BigDecimal("50000.0000")),

            // 6. Siro Đào Monin (Chai 700ml, giá vốn 220k/chai -> ~314.29đ/ml)
            new SeedIngredient("Siro Đào Monin", "ml", "Chai 700ml",
                    new BigDecimal("700.0000"), new BigDecimal("314.29"),
                    new BigDecimal("2100.0000"), new BigDecimal("1400.0000")),

            // 7. Bột Matcha Uji Nhật Bản (Túi 1kg = 1,000g, giá vốn 650k/túi -> 650đ/g) - Case Low Stock
            new SeedIngredient("Bột Matcha Uji Nhật Bản", "g", "Túi 1kg",
                    new BigDecimal("1000.0000"), new BigDecimal("650.00"),
                    new BigDecimal("400.0000"), new BigDecimal("1000.0000")),

            // 8. Trà đen Phúc Long (Bao 5kg = 5,000g, giá vốn 450k/bao -> 90đ/g)
            new SeedIngredient("Trà đen Phúc Long", "g", "Bao 5kg",
                    new BigDecimal("5000.0000"), new BigDecimal("90.00"),
                    new BigDecimal("10000.0000"), new BigDecimal("5000.0000")),

            // 9. Ly nhựa PET 500ml (Thùng 1,000 ly = 1,000 cái, giá vốn 400k/thùng -> 400đ/cái)
            new SeedIngredient("Ly nhựa PET 500ml", "cái", "Thùng 1,000 ly",
                    new BigDecimal("1000.0000"), new BigDecimal("400.00"),
                    new BigDecimal("3500.0000"), new BigDecimal("2000.0000")),

            // 10. Ống hút giấy bọc màng (Thùng 2,000 cái = 2,000 cái, giá vốn 200k/thùng -> 100đ/cái)
            new SeedIngredient("Ống hút giấy bọc màng", "cái", "Thùng 2,000 cái",
                    new BigDecimal("2000.0000"), new BigDecimal("100.00"),
                    new BigDecimal("6000.0000"), new BigDecimal("3000.0000"))
    );

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("Bắt đầu nạp 10 nguyên vật liệu mẫu F&B chuẩn...");

        List<Branch> branches = branchRepository.findAll();
        if (branches.isEmpty()) {
            Branch defaultBranch = branchRepository.findByCode("BR01").orElseGet(() -> branchRepository.save(
                    Branch.builder()
                            .code("BR01")
                            .name("Chi nhánh Flagship Quận 1")
                            .address("123 Nguyễn Huệ, Phường Bến Nghé, Quận 1, TP.HCM")
                            .phone("02838221234")
                            .status(BranchStatus.OPEN)
                            .build()));
            branches = List.of(defaultBranch);
        }

        for (SeedIngredient item : SAMPLE_INGREDIENTS) {
            Ingredient ingredient = ingredientRepository.findByName(item.name())
                    .orElseGet(() -> ingredientRepository.save(Ingredient.builder()
                            .name(item.name())
                            .baseUnit(item.baseUnit())
                            .packUnit(item.packUnit())
                            .unitsPerPack(item.unitsPerPack())
                            .unitCost(item.unitCost())
                            .status("ACTIVE")
                            .build()));

            for (Branch branch : branches) {
                if (!inventoryRepository.existsByBranch_IdAndIngredient_Id(branch.getId(), ingredient.getId())) {
                    Inventory inventory = Inventory.builder()
                            .branch(branch)
                            .ingredient(ingredient)
                            .currentQtyBase(item.initialQtyBase())
                            .currentPackUnits(BigDecimal.ZERO)
                            .safetyStockLevel(item.safetyStockLevel())
                            .lowStockFlag(false)
                            .lastUpdated(Instant.now())
                            .build();

                    inventory.recalculateStockAndFlag(ingredient.getUnitsPerPack());
                    inventoryRepository.save(inventory);
                }
            }
        }

        log.info("Hoàn tất nạp hạt giống dữ liệu Kho F&B (10 nguyên vật liệu cho {} chi nhánh).", branches.size());
    }
}
