package edu.fpt.sba.scms.config;

import edu.fpt.sba.scms.common.enums.BranchStatus;
import edu.fpt.sba.scms.common.enums.Roles;
import edu.fpt.sba.scms.common.enums.UserStatus;
import edu.fpt.sba.scms.model.Branch;
import edu.fpt.sba.scms.model.Ingredient;
import edu.fpt.sba.scms.model.Inventory;
import edu.fpt.sba.scms.model.User;
import edu.fpt.sba.scms.repository.BranchRepository;
import edu.fpt.sba.scms.repository.IngredientRepository;
import edu.fpt.sba.scms.repository.InventoryRepository;
import edu.fpt.sba.scms.repository.RoleRepository;
import edu.fpt.sba.scms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Seed 4 chi nhánh (3 Hub Kho Tổng Vùng + 1 Cửa hàng bán lẻ Cầu Giấy),
 * 10 nguyên vật liệu mẫu chuẩn F&B và tồn kho ban đầu cho môi trường DEV (CHECKLIST.md Bước 0).
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
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    private record BranchSeedInfo(String code, String name, String address, String phone) {
    }

    private static final List<BranchSeedInfo> SEED_BRANCHES = List.of(
            new BranchSeedInfo("DC-HN", "Kho Tổng Miền Bắc (Hà Nội)", "Kho Logistics Gia Lâm, Hà Nội", "02438888001"),
            new BranchSeedInfo("DC-DN", "Kho Tổng Miền Trung (Đà Nẵng)", "KCN Hòa Khánh, Liên Chiểu, Đà Nẵng", "02368888002"),
            new BranchSeedInfo("DC-HCM", "Kho Tổng Miền Nam (TP.HCM)", "Khu Chế Xuất Tân Thuận, Quận 7, TP.HCM", "02838888003"),
            new BranchSeedInfo("CG-STORE", "Chi nhánh Highlands Cầu Giấy", "101 Cầu Giấy, Quan Hoa, Cầu Giấy, Hà Nội", "02438888004")
    );

    private record SeedIngredient(
            String name,
            String baseUnit,
            String packUnit,
            BigDecimal unitsPerPack,
            BigDecimal unitCost
    ) {
    }

    private static final List<SeedIngredient> SAMPLE_INGREDIENTS = List.of(
            // 1. Đường cát trắng Biên Hòa: 1 Thùng = 4 gói (mỗi gói 5kg = 5.000g), tổng = 20.000g
            new SeedIngredient("Đường cát trắng Biên Hòa (Gói 5kg)", "g", "Thùng 4 gói 5kg",
                    new BigDecimal("20000.0000"), new BigDecimal("22.00")),

            // 2. Hạt Cà phê Robusta: 1 Thùng = 10 túi (mỗi túi 1kg = 1.000g), tổng = 10.000g
            new SeedIngredient("Hạt Cà phê Robusta (Túi 1kg)", "g", "Thùng 10 túi 1kg",
                    new BigDecimal("10000.0000"), new BigDecimal("150.00")),

            // 3. Hạt Cà phê Arabica: 1 Thùng = 10 túi (mỗi túi 1kg = 1.000g), tổng = 10.000g
            new SeedIngredient("Hạt Cà phê Arabica (Túi 1kg)", "g", "Thùng 10 túi 1kg",
                    new BigDecimal("10000.0000"), new BigDecimal("240.00")),

            // 4. Sữa tươi thanh trùng Dalat Milk: 1 Thùng = 12 hộp (mỗi hộp 1L = 1.000ml), tổng = 12.000ml
            new SeedIngredient("Sữa tươi thanh trùng Dalat Milk (Hộp 1L)", "ml", "Thùng 12 hộp 1L",
                    new BigDecimal("12000.0000"), new BigDecimal("35.00")),

            // 5. Sữa đặc Ông Thọ đỏ: 1 Thùng = 24 lon (mỗi lon 380g), tổng = 9.120g
            new SeedIngredient("Sữa đặc Ông Thọ đỏ (Lon 380g)", "g", "Thùng 24 lon 380g",
                    new BigDecimal("9120.0000"), new BigDecimal("65.79")),

            // 6. Siro Đào Monin: 1 Thùng = 6 chai (mỗi chai 700ml), tổng = 4.200ml
            new SeedIngredient("Siro Đào Monin (Chai 700ml)", "ml", "Thùng 6 chai 700ml",
                    new BigDecimal("4200.0000"), new BigDecimal("314.29")),

            // 7. Bột Matcha Uji Nhật Bản: 1 Thùng = 10 túi (mỗi túi 500g = 500g), tổng = 5.000g
            new SeedIngredient("Bột Matcha Uji Nhật Bản (Túi 500g)", "g", "Thùng 10 túi 500g",
                    new BigDecimal("5000.0000"), new BigDecimal("650.00")),

            // 8. Trà đen Phúc Long: 1 Thùng = 10 gói (mỗi gói 500g), tổng = 5.000g
            new SeedIngredient("Trà đen Phúc Long (Gói 500g)", "g", "Thùng 10 gói 500g",
                    new BigDecimal("5000.0000"), new BigDecimal("90.00")),

            // 9. Ly nhựa PET 500ml: 1 Thùng = 20 cây (mỗi cây 50 ly), tổng = 1.000 cái
            new SeedIngredient("Ly nhựa PET 500ml (Cây 50 ly)", "cái", "Thùng 20 cây 50 ly",
                    new BigDecimal("1000.0000"), new BigDecimal("400.00")),

            // 10. Ống hút giấy: 1 Thùng = 20 bịch (mỗi bịch 100 cái), tổng = 2.000 cái
            new SeedIngredient("Ống hút giấy (Bịch 100 cái)", "cái", "Thùng 20 bịch",
                    new BigDecimal("2000.0000"), new BigDecimal("100.00"))
    );

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (jdbcTemplate != null) {
            try {
                jdbcTemplate.execute("ALTER TABLE ingredient MODIFY COLUMN pack_unit VARCHAR(100)");
                jdbcTemplate.execute("UPDATE ingredient SET base_unit = 'ml', units_per_pack = 12000.0000, unit_cost = 35.00 WHERE (name LIKE '%Dalat Milk%' OR name LIKE '%Sữa tươi%') AND (base_unit = 'hộp' OR units_per_pack < 1000)");
                jdbcTemplate.execute("UPDATE ingredient SET base_unit = 'g', units_per_pack = 9120.0000, unit_cost = 65.79 WHERE (name LIKE '%Ông Thọ%' OR name LIKE '%Sữa đặc%') AND (base_unit = 'lon' OR units_per_pack < 1000)");
            } catch (Exception e) {
                log.debug("Bỏ qua tự động cập nhật: {}", e.getMessage());
            }
        }

        log.info("Bắt đầu nạp dữ liệu nền móng: 4 chi nhánh & 10 mặt hàng kho tổng...");

        // 1. Đảm bảo đủ 4 Branch: DC-HN, DC-DN, DC-HCM, CG-STORE (idempotent findByCode)
        List<Branch> seededBranches = new ArrayList<>();
        for (BranchSeedInfo info : SEED_BRANCHES) {
            Branch branch = branchRepository.findByCode(info.code())
                    .map(existing -> {
                        existing.setName(info.name());
                        existing.setAddress(info.address());
                        existing.setPhone(info.phone());
                        return branchRepository.save(existing);
                    })
                    .orElseGet(() -> branchRepository.save(
                            Branch.builder()
                                    .code(info.code())
                                    .name(info.name())
                                    .address(info.address())
                                    .phone(info.phone())
                                    .status(BranchStatus.OPEN)
                                    .build()
                    ));
            seededBranches.add(branch);
        }

        // 2. Nạp 10 nguyên vật liệu chuẩn
        List<Ingredient> seededIngredients = new ArrayList<>();
        for (SeedIngredient item : SAMPLE_INGREDIENTS) {
            Ingredient ingredient = ingredientRepository.findByName(item.name())
                    .map(existing -> {
                        existing.setBaseUnit(item.baseUnit());
                        existing.setPackUnit(item.packUnit());
                        existing.setUnitsPerPack(item.unitsPerPack());
                        existing.setUnitCost(item.unitCost());
                        existing.setStatus("ACTIVE");
                        return ingredientRepository.save(existing);
                    })
                    .orElseGet(() -> ingredientRepository.save(Ingredient.builder()
                            .name(item.name())
                            .baseUnit(item.baseUnit())
                            .packUnit(item.packUnit())
                            .unitsPerPack(item.unitsPerPack())
                            .unitCost(item.unitCost())
                            .status("ACTIVE")
                            .build()));
            seededIngredients.add(ingredient);
        }

        // 3. Nạp tồn kho (inventory):
        //    - Tại 3 Hub (DC-HN, DC-DN, DC-HCM): Tồn dồi dào (50 - 200 thùng cho mỗi món) để COO có hàng điều phối
        //    - Tại Quán Cầu Giấy (CG-STORE): Tồn thấp (chạm ngưỡng safetyStockLevel) để tạo kịch bản cần xin cấp hàng
        for (Ingredient ingredient : seededIngredients) {
            for (Branch branch : seededBranches) {
                BigDecimal qtyBase;
                BigDecimal safetyStock;

                if ("CG-STORE".equalsIgnoreCase(branch.getCode())) {
                    // Cửa hàng bán lẻ: Ngưỡng an toàn 3 thùng, tồn hiện tại chỉ 2 thùng (<= 3 thùng) -> Chạm ngưỡng cần xin cấp hàng
                    safetyStock = ingredient.getUnitsPerPack().multiply(new BigDecimal("3.0000"));
                    qtyBase = ingredient.getUnitsPerPack().multiply(new BigDecimal("2.0000"));
                } else {
                    // 3 Hub Kho Tổng Vùng: Tồn dồi dào (DC-HN: 120 thùng, DC-DN: 80 thùng, DC-HCM: 150 thùng)
                    BigDecimal packUnits = switch (branch.getCode()) {
                        case "DC-HN" -> new BigDecimal("120.0000");
                        case "DC-DN" -> new BigDecimal("80.0000");
                        case "DC-HCM" -> new BigDecimal("150.0000");
                        default -> new BigDecimal("100.0000");
                    };
                    qtyBase = ingredient.getUnitsPerPack().multiply(packUnits);
                    safetyStock = ingredient.getUnitsPerPack().multiply(new BigDecimal("10.0000")); // Ngưỡng an toàn 10 thùng
                }

                Inventory inventory = inventoryRepository.findByBranchIdAndIngredientId(branch.getId(), ingredient.getId())
                        .orElseGet(() -> Inventory.builder()
                                .branch(branch)
                                .ingredient(ingredient)
                                .build());

                inventory.setCurrentQtyBase(qtyBase);
                inventory.setSafetyStockLevel(safetyStock);
                inventory.recalculateStockAndFlag(ingredient.getUnitsPerPack());
                inventory.setLastUpdated(Instant.now());
                inventoryRepository.save(inventory);
            }
        }

        log.info("Hoàn tất nạp dữ liệu nền móng: 4 chi nhánh, {} nguyên vật liệu, {} bản ghi tồn kho.",
                seededIngredients.size(), seededIngredients.size() * seededBranches.size());

        // 4. Đảm bảo có tài khoản Quản lý chi nhánh Cầu Giấy 'manager_cg'
        Branch cgBranch = branchRepository.findByCode("CG-STORE").orElse(null);
        if (cgBranch != null && !userRepository.existsByUsername("manager_cg")) {
            roleRepository.findByCode(Roles.BRANCH_MANAGER.code()).ifPresent(bmRole -> {
                User bm = userRepository.save(User.builder()
                        .roleId(bmRole.getId())
                        .branchId(cgBranch.getId())
                        .fullName("Trần Thị Quản Lý")
                        .email("manager.cg@scms.local")
                        .phone("0988880001")
                        .username("manager_cg")
                        .passwordHash(passwordEncoder.encode("Admin@123"))
                        .position(Roles.BRANCH_MANAGER)
                        .status(UserStatus.ACTIVE)
                        .build());
                cgBranch.setManagerId(bm.getId());
                branchRepository.save(cgBranch);
                log.info("Đã seed tài khoản Quản lý chi nhánh Cầu Giấy: 'manager_cg' (mật khẩu: Admin@123)");
            });
        }
    }
}
