package edu.fpt.sba.scms.config;

import edu.fpt.sba.scms.common.enums.AttendanceStatus;
import edu.fpt.sba.scms.common.enums.BranchStatus;
import edu.fpt.sba.scms.common.enums.CashTransactionType;
import edu.fpt.sba.scms.common.enums.OrderStatus;
import edu.fpt.sba.scms.common.enums.PaymentMethod;
import edu.fpt.sba.scms.common.enums.PaymentStatus;
import edu.fpt.sba.scms.common.enums.ProductSizeName;
import edu.fpt.sba.scms.common.enums.ProductStatus;
import edu.fpt.sba.scms.common.enums.RecordStatus;
import edu.fpt.sba.scms.common.enums.RefundStatus;
import edu.fpt.sba.scms.common.enums.Roles;
import edu.fpt.sba.scms.common.enums.ShiftStatus;
import edu.fpt.sba.scms.common.enums.ShiftType;
import edu.fpt.sba.scms.common.enums.StockTransactionType;
import edu.fpt.sba.scms.common.enums.StocktakeStatus;
import edu.fpt.sba.scms.common.enums.UserStatus;
import edu.fpt.sba.scms.common.util.DateTimeUtil;
import edu.fpt.sba.scms.model.Attendance;
import edu.fpt.sba.scms.model.Branch;
import edu.fpt.sba.scms.model.CashTransaction;
import edu.fpt.sba.scms.model.Category;
import edu.fpt.sba.scms.model.Ingredient;
import edu.fpt.sba.scms.model.Inventory;
import edu.fpt.sba.scms.model.Order;
import edu.fpt.sba.scms.model.OrderLine;
import edu.fpt.sba.scms.model.Payment;
import edu.fpt.sba.scms.model.Product;
import edu.fpt.sba.scms.model.ProductSize;
import edu.fpt.sba.scms.model.Refund;
import edu.fpt.sba.scms.model.Role;
import edu.fpt.sba.scms.model.Shift;
import edu.fpt.sba.scms.model.ShiftAssignment;
import edu.fpt.sba.scms.model.ShiftAssignmentId;
import edu.fpt.sba.scms.model.StockTransaction;
import edu.fpt.sba.scms.model.Stocktake;
import edu.fpt.sba.scms.model.StocktakeLine;
import edu.fpt.sba.scms.model.StocktakeLineId;
import edu.fpt.sba.scms.model.User;
import edu.fpt.sba.scms.repository.AttendanceRepository;
import edu.fpt.sba.scms.repository.BranchRepository;
import edu.fpt.sba.scms.repository.CashTransactionRepository;
import edu.fpt.sba.scms.repository.CategoryRepository;
import edu.fpt.sba.scms.repository.IngredientRepository;
import edu.fpt.sba.scms.repository.InventoryRepository;
import edu.fpt.sba.scms.repository.OrderLineRepository;
import edu.fpt.sba.scms.repository.OrderRepository;
import edu.fpt.sba.scms.repository.PaymentRepository;
import edu.fpt.sba.scms.repository.ProductRepository;
import edu.fpt.sba.scms.repository.ProductSizeRepository;
import edu.fpt.sba.scms.repository.RefundRepository;
import edu.fpt.sba.scms.repository.RoleRepository;
import edu.fpt.sba.scms.repository.ShiftAssignmentRepository;
import edu.fpt.sba.scms.repository.ShiftRepository;
import edu.fpt.sba.scms.repository.StockTransactionRepository;
import edu.fpt.sba.scms.repository.StocktakeLineRepository;
import edu.fpt.sba.scms.repository.StocktakeRepository;
import edu.fpt.sba.scms.repository.UserRepository;
import edu.fpt.sba.scms.service.support.PinHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Seed dữ liệu hoàn chỉnh cho môi trường DEV phục vụ hiển thị toàn bộ hệ thống Báo cáo &amp; Phân tích (UC34–UC42).
 * <p>
 * Bao gồm:
 * 1. Đa chi nhánh (BR01 Quận 1 &amp; BR02 Thảo Điền) để kiểm tra báo cáo hợp nhất chuỗi (UC40).
 * 2. Nhân sự đầy đủ vai trò (Quản lý, Thu ngân, Pha chế, Phục vụ) kèm mật khẩu "Admin@123" và mã PIN Kiosk.
 * 3. Thực đơn chuẩn F&amp;B với danh mục, sản phẩm, kích thước (S/M/L) và công thức định lượng (recipe).
 * 4. Biến động kho &amp; kiểm kê gần nhất: nhập, xuất bar, hủy hàng, điều chỉnh kiểm kê (khớp, lệch, chưa đếm) (UC38).
 * 5. Phiên ca &amp; dòng tiền két: ca đã đóng đối soát cân bằng/lệch tiền, và ca đang mở để hiển thị két tiền trên Dashboard (UC34, UC37).
 * 6. Chấm công: có mặt đúng giờ, đi muộn, vắng mặt, ca đang trực và quên check-out (UC34, UC39).
 * 7. Lịch sử đơn hàng 7 ngày gần nhất đến hôm nay: xu hướng doanh thu, thanh toán Cash/VietQR, giờ cao điểm sáng/trưa (UC42),
 *    top sản phẩm bán chạy/bán chậm (UC41), đơn hoàn tiền một phần (UC35).
 */
@Slf4j
@Component
@org.springframework.core.annotation.Order(30)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.auth.seed.enabled", havingValue = "true")
public class ReportDataSeeder implements ApplicationRunner {

    private final BranchRepository branchRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductSizeRepository productSizeRepository;
    private final IngredientRepository ingredientRepository;
    private final InventoryRepository inventoryRepository;
    private final ShiftRepository shiftRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final CashTransactionRepository cashTransactionRepository;
    private final OrderRepository orderRepository;
    private final OrderLineRepository orderLineRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final StockTransactionRepository stockTransactionRepository;
    private final StocktakeRepository stocktakeRepository;
    private final StocktakeLineRepository stocktakeLineRepository;
    private final AttendanceRepository attendanceRepository;
    private final PasswordEncoder passwordEncoder;
    private final PinHasher pinHasher;
    private final JdbcTemplate jdbcTemplate;

    private static final String DEFAULT_PASSWORD = "Admin@123";

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("=== Bắt đầu nạp dữ liệu mẫu cho hệ thống Báo cáo & Phân tích (Report & Analytics Seed) ===");

        // 1. Cấu hình chi nhánh
        Branch br01 = ensureBranch("BR01", "Chi nhánh Flagship Quận 1", "123 Nguyễn Huệ, Phường Bến Nghé, Quận 1, TP.HCM", "02838221234");
        Branch br02 = ensureBranch("BR02", "Chi nhánh Thảo Điền", "68 Xuân Thủy, Phường Thảo Điền, TP. Thủ Đức, TP.HCM", "02838995678");

        // Đảm bảo nguyên liệu đã được phân bổ tồn kho cho cả BR02
        syncInventoryForBranch(br02);

        // 2. Nhân sự các chi nhánh
        Role bmRole = roleRepository.findByCode(Roles.BRANCH_MANAGER.code()).orElseThrow();
        Role cashierRole = roleRepository.findByCode(Roles.CASHIER.code()).orElseThrow();
        Role baristaRole = roleRepository.findByCode(Roles.BARISTA.code()).orElseThrow();
        Role waiterRole = roleRepository.findByCode(Roles.WAITER.code()).orElseThrow();

        User manager1 = ensureUser("manager1", "Nguyễn Văn Quản Lý", "manager1@scms.local", "0901234567", br01, bmRole, "1234");
        User manager2 = ensureUser("manager2", "Trần Thị Quản Lý", "manager2@scms.local", "0901234568", br02, bmRole, "1234");

        User cashier1 = ensureUser("cashier1", "Lê Thu Ngân", "cashier1@scms.local", "0902345678", br01, cashierRole, "1111");
        User cashier2 = ensureUser("cashier2", "Phạm Minh Thu", "cashier2@scms.local", "0902345679", br01, cashierRole, "2222");
        User cashier3 = ensureUser("cashier3", "Đỗ Hải Đăng", "cashier3@scms.local", "0902345680", br02, cashierRole, "1111");

        User barista1 = ensureUser("barista1", "Hoàng Pha Chế", "barista1@scms.local", "0903456789", br01, baristaRole, "3333");
        User barista2 = ensureUser("barista2", "Đặng Văn Trà", "barista2@scms.local", "0903456790", br02, baristaRole, "3333");

        User waiter1 = ensureUser("waiter1", "Vũ Phục Vụ", "waiter1@scms.local", "0904567890", br01, waiterRole, "4444");
        User waiter2 = ensureUser("waiter2", "Bùi Văn Nhanh", "waiter2@scms.local", "0904567891", br02, waiterRole, "4444");

        br01.setManagerId(manager1.getId());
        br02.setManagerId(manager2.getId());
        branchRepository.saveAll(List.of(br01, br02));

        // 3. Thực đơn, Danh mục, Kích thước, Công thức
        Map<String, Product> menu = seedMenuAndRecipes();

        // 4. Biến động kho & Kiểm kê định kỳ (UC38, UC34)
        seedStockMovementsAndStocktake(br01, manager1);

        // 5. Phiên ca, Chấm công, Đơn hàng, Thanh toán & Dòng tiền
        if (orderRepository.count() == 0) {
            log.info("Chưa có đơn hàng trong hệ thống. Tiến hành seed lịch sử 7 ngày, ca làm việc, chấm công và doanh số...");
            seedShiftsAttendanceAndOrders(br01, br02, manager1, cashier1, cashier2, cashier3, barista1, waiter1, menu);
        } else {
            log.info("Hệ thống đã có dữ liệu đơn hàng. Bỏ qua bước tạo lại đơn mẫu.");
            // Đảm bảo vẫn có ca đang mở hôm nay cho BR01 để két tiền hiển thị trên Dashboard
            ensureTodayOpenShift(br01, cashier1);
        }

        log.info("=== Hoàn tất nạp dữ liệu mẫu Báo cáo & Phân tích thành công! ===");
    }

    private Branch ensureBranch(String code, String name, String address, String phone) {
        return branchRepository.findByCode(code).orElseGet(() -> branchRepository.save(
                Branch.builder()
                        .code(code)
                        .name(name)
                        .address(address)
                        .phone(phone)
                        .openingTime(LocalTime.of(6, 0))
                        .closingTime(LocalTime.of(23, 0))
                        .tableCount(25)
                        .status(BranchStatus.OPEN)
                        .build()));
    }

    private void syncInventoryForBranch(Branch branch) {
        List<Ingredient> ingredients = ingredientRepository.findAll();
        for (Ingredient ing : ingredients) {
            if (!inventoryRepository.existsByBranch_IdAndIngredient_Id(branch.getId(), ing.getId())) {
                Inventory inv = Inventory.builder()
                        .branch(branch)
                        .ingredient(ing)
                        .currentQtyBase(new BigDecimal("10000.0000"))
                        .currentPackUnits(BigDecimal.ZERO)
                        .safetyStockLevel(new BigDecimal("2000.0000"))
                        .lowStockFlag(false)
                        .lastUpdated(Instant.now())
                        .build();
                inv.recalculateStockAndFlag(ing.getUnitsPerPack());
                inventoryRepository.save(inv);
            }
        }
    }

    private User ensureUser(String username, String fullName, String email, String phone,
                            Branch branch, Role role, String pin) {
        return userRepository.findByUsername(username).orElseGet(() -> {
            String passHash = passwordEncoder.encode(DEFAULT_PASSWORD);
            String pinHash = pinHasher.hash(pin);
            String pinLookupHash = pinHasher.lookupHash(branch.getId(), pin);
            Roles roleEnum = Roles.valueOf(role.getCode());

            return userRepository.save(User.builder()
                    .branchId(branch.getId())
                    .roleId(role.getId())
                    .role(role)
                    .fullName(fullName)
                    .email(email)
                    .phone(phone)
                    .username(username)
                    .passwordHash(passHash)
                    .pinHash(pinHash)
                    .pinLookupHash(pinLookupHash)
                    .pinSetAt(Instant.now())
                    .position(roleEnum)
                    .status(UserStatus.ACTIVE)
                    .build());
        });
    }

    private Map<String, Product> seedMenuAndRecipes() {
        Category catCoffee = categoryRepository.findByName("Cà phê & Bạc xỉu").orElseGet(() -> categoryRepository.save(
                Category.builder().name("Cà phê & Bạc xỉu").description("Cà phê Robusta & Arabica truyền thống và sáng tạo")
                        .sortOrder(1).status(RecordStatus.ACTIVE).build()));
        catCoffee.assignGeneratedCode();
        categoryRepository.save(catCoffee);

        Category catTea = categoryRepository.findByName("Trà & Trà sữa").orElseGet(() -> categoryRepository.save(
                Category.builder().name("Trà & Trà sữa").description("Trà trái cây nhiệt đới và trà sữa đậm đà")
                        .sortOrder(2).status(RecordStatus.ACTIVE).build()));
        catTea.assignGeneratedCode();
        categoryRepository.save(catTea);

        Category catBakery = categoryRepository.findByName("Bánh & Ăn nhẹ").orElseGet(() -> categoryRepository.save(
                Category.builder().name("Bánh & Ăn nhẹ").description("Bánh nướng thơm ngon ăn kèm")
                        .sortOrder(3).status(RecordStatus.ACTIVE).build()));
        catBakery.assignGeneratedCode();
        categoryRepository.save(catBakery);

        Map<String, Product> products = new HashMap<>();

        // 1. Cà phê Sữa Đá (Best Seller)
        Product pCfSua = ensureProduct("Cà phê Sữa Đá", catCoffee.getId(), ProductSizeName.M, ProductStatus.AVAILABLE,
                "Cà phê Robusta Đắk Lắk đậm đà hòa quyện cùng sữa đặc béo ngậy");
        ensureProductSize(pCfSua.getId(), ProductSizeName.S, new BigDecimal("29000"));
        ensureProductSize(pCfSua.getId(), ProductSizeName.M, new BigDecimal("35000"));
        ensureProductSize(pCfSua.getId(), ProductSizeName.L, new BigDecimal("42000"));
        products.put("CF_SUADA", pCfSua);

        // 2. Cà phê Đen Đá
        Product pCfDen = ensureProduct("Cà phê Đen Đá", catCoffee.getId(), ProductSizeName.M, ProductStatus.AVAILABLE,
                "Hạt Robusta nguyên chất pha phin truyền thống chuẩn gu đậm đà");
        ensureProductSize(pCfDen.getId(), ProductSizeName.S, new BigDecimal("25000"));
        ensureProductSize(pCfDen.getId(), ProductSizeName.M, new BigDecimal("29000"));
        ensureProductSize(pCfDen.getId(), ProductSizeName.L, new BigDecimal("35000"));
        products.put("CF_DENDA", pCfDen);

        // 3. Bạc Xỉu Sài Gòn
        Product pBacXiu = ensureProduct("Bạc Xỉu Sài Gòn", catCoffee.getId(), ProductSizeName.M, ProductStatus.AVAILABLE,
                "Vị ngọt béo từ sữa tươi và sữa đặc điểm xuyết chút hương cà phê nhẹ nhàng");
        ensureProductSize(pBacXiu.getId(), ProductSizeName.S, new BigDecimal("32000"));
        ensureProductSize(pBacXiu.getId(), ProductSizeName.M, new BigDecimal("39000"));
        ensureProductSize(pBacXiu.getId(), ProductSizeName.L, new BigDecimal("45000"));
        products.put("CF_BACXIU", pBacXiu);

        // 4. Cà phê Muối Cố Đô
        Product pCfMuoi = ensureProduct("Cà phê Muối Cố Đô", catCoffee.getId(), ProductSizeName.M, ProductStatus.AVAILABLE,
                "Lớp kem muối béo mặn kết hợp cà phê Robusta đậm vị hấp dẫn");
        ensureProductSize(pCfMuoi.getId(), ProductSizeName.M, new BigDecimal("39000"));
        ensureProductSize(pCfMuoi.getId(), ProductSizeName.L, new BigDecimal("45000"));
        products.put("CF_MUOI", pCfMuoi);

        // 5. Trà Đào Cam Sả
        Product pTraDao = ensureProduct("Trà Đào Cam Sả", catTea.getId(), ProductSizeName.M, ProductStatus.AVAILABLE,
                "Trà đen thanh mát kết hợp siro đào Monin và cam sả sảng khoái");
        ensureProductSize(pTraDao.getId(), ProductSizeName.M, new BigDecimal("45000"));
        ensureProductSize(pTraDao.getId(), ProductSizeName.L, new BigDecimal("52000"));
        products.put("TEA_DAO", pTraDao);

        // 6. Trà Sữa Oolong Phúc Long
        Product pTraSua = ensureProduct("Trà Sữa Oolong Phúc Long", catTea.getId(), ProductSizeName.M, ProductStatus.AVAILABLE,
                "Hương trà nồng đượm quyện cùng vị sữa thanh tao hấp dẫn");
        ensureProductSize(pTraSua.getId(), ProductSizeName.M, new BigDecimal("42000"));
        ensureProductSize(pTraSua.getId(), ProductSizeName.L, new BigDecimal("49000"));
        products.put("TEA_OOLONG", pTraSua);

        // 7. Matcha Latte Uji Nhật Bản
        Product pMatcha = ensureProduct("Matcha Latte Uji Nhật Bản", catTea.getId(), ProductSizeName.M, ProductStatus.AVAILABLE,
                "Bột Matcha Uji hảo hạng kết hợp sữa tươi thanh trùng Dalat Milk");
        ensureProductSize(pMatcha.getId(), ProductSizeName.M, new BigDecimal("49000"));
        ensureProductSize(pMatcha.getId(), ProductSizeName.L, new BigDecimal("59000"));
        products.put("TEA_MATCHA", pMatcha);

        // 8. Trà Hoa Cúc Mật Ong (Món bán chậm / 0 đơn để kiểm tra UC41 SLOW_SELLING)
        Product pTraCuc = ensureProduct("Trà Hoa Cúc Mật Ong", catTea.getId(), ProductSizeName.M, ProductStatus.AVAILABLE,
                "Trà thanh lọc cơ thể, an thần dịu nhẹ với mật ong nguyên chất");
        ensureProductSize(pTraCuc.getId(), ProductSizeName.M, new BigDecimal("39000"));
        products.put("TEA_HOACUC", pTraCuc);

        // 9. Bánh Croissant Bơ Tỏi (Cố tình KHÔNG tạo công thức để kiểm tra cảnh báo thiếu giá vốn UC40)
        Product pCroissant = ensureProduct("Bánh Croissant Bơ Tỏi", catBakery.getId(), ProductSizeName.M, ProductStatus.AVAILABLE,
                "Bánh sừng bò nướng giòn thơm lừng bơ tỏi đặc biệt");
        ensureProductSize(pCroissant.getId(), ProductSizeName.M, new BigDecimal("35000"));
        products.put("BAKERY_CROISSANT", pCroissant);

        // 10. Bánh Tiramisu Ý
        Product pTiramisu = ensureProduct("Bánh Tiramisu Ý", catBakery.getId(), ProductSizeName.M, ProductStatus.AVAILABLE,
                "Bánh mềm thơm vị cà phê và phô mai mascarpone béo ngậy");
        ensureProductSize(pTiramisu.getId(), ProductSizeName.M, new BigDecimal("45000"));
        products.put("BAKERY_TIRAMISU", pTiramisu);

        // 11. Sinh tố Bơ (UNAVAILABLE để kiểm tra bộ lọc thực đơn)
        ensureProduct("Sinh Tố Bơ Đắk Lắk", catTea.getId(), ProductSizeName.M, ProductStatus.UNAVAILABLE,
                "Sinh tố bơ sáp thơm ngậy nguyên chất (Tạm ngưng phục vụ do hết mùa)");

        // Tạo bảng recipe nếu chưa có (Native query vì bảng recipe chưa có entity JPA)
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS recipe (
                    recipe_id INT AUTO_INCREMENT PRIMARY KEY,
                    product_id INT NOT NULL,
                    ingredient_id INT NOT NULL,
                    quantity_base DECIMAL(19, 4) NOT NULL,
                    size VARCHAR(5)
                )
                """);

        // Seed công thức định lượng (để tính giá vốn COGS cho UC40)
        seedRecipeIfEmpty(products);

        return products;
    }

    private Product ensureProduct(String name, Integer categoryId, ProductSizeName defaultSize,
                                  ProductStatus status, String description) {
        return productRepository.findByName(name).orElseGet(() -> productRepository.save(
                Product.builder()
                        .name(name)
                        .categoryId(categoryId)
                        .defaultSize(defaultSize)
                        .status(status)
                        .description(description)
                        .build()));
    }

    private void ensureProductSize(Integer productId, ProductSizeName size, BigDecimal price) {
        if (productSizeRepository.findByProductIdAndSize(productId, size).isEmpty()) {
            productSizeRepository.save(ProductSize.builder()
                    .productId(productId)
                    .size(size)
                    .price(price)
                    .build());
        }
    }

    private void seedRecipeIfEmpty(Map<String, Product> products) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM recipe", Integer.class);
        if (count != null && count > 0) {
            return;
        }

        Ingredient robusta = ingredientRepository.findByName("Hạt Cà phê Robusta Đắk Lắk").orElse(null);
        Ingredient suaDac = ingredientRepository.findByName("Sữa đặc Ông Thọ đỏ").orElse(null);
        Ingredient suaTuoi = ingredientRepository.findByName("Sữa tươi thanh trùng Dalat Milk").orElse(null);
        Ingredient duong = ingredientRepository.findByName("Đường cát Biên Hòa").orElse(null);
        Ingredient siroDao = ingredientRepository.findByName("Siro Đào Monin").orElse(null);
        Ingredient matcha = ingredientRepository.findByName("Bột Matcha Uji Nhật Bản").orElse(null);
        Ingredient traDen = ingredientRepository.findByName("Trà đen Phúc Long").orElse(null);

        if (robusta == null || suaDac == null || suaTuoi == null || duong == null || traDen == null) {
            log.warn("Chưa đủ 10 nguyên liệu để tạo công thức recipe.");
            return;
        }

        // Cà phê Sữa Đá: 25g Robusta, 35ml Sữa đặc, 20ml Sữa tươi
        insertRecipe(products.get("CF_SUADA").getId(), robusta.getId(), new BigDecimal("25.0000"), null);
        insertRecipe(products.get("CF_SUADA").getId(), suaDac.getId(), new BigDecimal("35.0000"), null);
        insertRecipe(products.get("CF_SUADA").getId(), suaTuoi.getId(), new BigDecimal("20.0000"), null);

        // Cà phê Đen Đá: 30g Robusta, 15g Đường
        insertRecipe(products.get("CF_DENDA").getId(), robusta.getId(), new BigDecimal("30.0000"), null);
        insertRecipe(products.get("CF_DENDA").getId(), duong.getId(), new BigDecimal("15.0000"), null);

        // Bạc Xỉu Sài Gòn: 15g Robusta, 45ml Sữa đặc, 60ml Sữa tươi
        insertRecipe(products.get("CF_BACXIU").getId(), robusta.getId(), new BigDecimal("15.0000"), null);
        insertRecipe(products.get("CF_BACXIU").getId(), suaDac.getId(), new BigDecimal("45.0000"), null);
        insertRecipe(products.get("CF_BACXIU").getId(), suaTuoi.getId(), new BigDecimal("60.0000"), null);

        // Cà phê Muối Cố Đô: 20g Robusta, 30ml Sữa đặc, 40ml Sữa tươi
        insertRecipe(products.get("CF_MUOI").getId(), robusta.getId(), new BigDecimal("20.0000"), null);
        insertRecipe(products.get("CF_MUOI").getId(), suaDac.getId(), new BigDecimal("30.0000"), null);
        insertRecipe(products.get("CF_MUOI").getId(), suaTuoi.getId(), new BigDecimal("40.0000"), null);

        // Trà Đào Cam Sả: 15g Trà đen, 30ml Siro đào, 20g Đường
        insertRecipe(products.get("TEA_DAO").getId(), traDen.getId(), new BigDecimal("15.0000"), null);
        if (siroDao != null) {
            insertRecipe(products.get("TEA_DAO").getId(), siroDao.getId(), new BigDecimal("30.0000"), null);
        }
        insertRecipe(products.get("TEA_DAO").getId(), duong.getId(), new BigDecimal("20.0000"), null);

        // Trà Sữa Oolong: 20g Trà đen, 30ml Sữa đặc, 50ml Sữa tươi
        insertRecipe(products.get("TEA_OOLONG").getId(), traDen.getId(), new BigDecimal("20.0000"), null);
        insertRecipe(products.get("TEA_OOLONG").getId(), suaDac.getId(), new BigDecimal("30.0000"), null);
        insertRecipe(products.get("TEA_OOLONG").getId(), suaTuoi.getId(), new BigDecimal("50.0000"), null);

        // Matcha Latte: 12g Bột Matcha, 150ml Sữa tươi, 15g Đường
        if (matcha != null) {
            insertRecipe(products.get("TEA_MATCHA").getId(), matcha.getId(), new BigDecimal("12.0000"), null);
        }
        insertRecipe(products.get("TEA_MATCHA").getId(), suaTuoi.getId(), new BigDecimal("150.0000"), null);
        insertRecipe(products.get("TEA_MATCHA").getId(), duong.getId(), new BigDecimal("15.0000"), null);

        // Bánh Tiramisu: 40ml Sữa tươi, 25g Đường
        insertRecipe(products.get("BAKERY_TIRAMISU").getId(), suaTuoi.getId(), new BigDecimal("40.0000"), null);
        insertRecipe(products.get("BAKERY_TIRAMISU").getId(), duong.getId(), new BigDecimal("25.0000"), null);

        log.info("Đã seed công thức định lượng (recipe) cho các món đồ uống chính.");
    }

    private void insertRecipe(Integer productId, Long ingredientId, BigDecimal quantityBase, String size) {
        jdbcTemplate.update("INSERT INTO recipe (product_id, ingredient_id, quantity_base, size) VALUES (?, ?, ?, ?)",
                productId, ingredientId, quantityBase, size);
    }

    private void seedStockMovementsAndStocktake(Branch branch, User manager) {
        if (stockTransactionRepository.count() > 0) {
            return;
        }

        Ingredient robusta = ingredientRepository.findByName("Hạt Cà phê Robusta Đắk Lắk").orElse(null);
        Ingredient arabica = ingredientRepository.findByName("Hạt Cà phê Arabica Cầu Đất").orElse(null);
        Ingredient dalatMilk = ingredientRepository.findByName("Sữa tươi thanh trùng Dalat Milk").orElse(null);
        Ingredient suaDac = ingredientRepository.findByName("Sữa đặc Ông Thọ đỏ").orElse(null);
        Ingredient duong = ingredientRepository.findByName("Đường cát Biên Hòa").orElse(null);
        Ingredient siroDao = ingredientRepository.findByName("Siro Đào Monin").orElse(null);
        Ingredient matcha = ingredientRepository.findByName("Bột Matcha Uji Nhật Bản").orElse(null);
        Ingredient traDen = ingredientRepository.findByName("Trà đen Phúc Long").orElse(null);
        Ingredient lyNhua = ingredientRepository.findByName("Ly nhựa PET 500ml").orElse(null);
        Ingredient ongHut = ingredientRepository.findByName("Ống hút giấy bọc màng").orElse(null);

        LocalDate today = DateTimeUtil.todayVN();
        Instant fiveDaysAgo = today.minusDays(5).atTime(8, 0).atZone(DateTimeUtil.VN).toInstant();
        Instant threeDaysAgo = today.minusDays(3).atTime(9, 30).atZone(DateTimeUtil.VN).toInstant();
        Instant twoDaysAgo = today.minusDays(2).atTime(11, 0).atZone(DateTimeUtil.VN).toInstant();

        // 1. Nhập hàng (RECEIVE)
        if (robusta != null) saveStockTx(branch, robusta, StockTransactionType.RECEIVE, new BigDecimal("10000.0000"), "Nhập hàng nhà cung cấp Đắk Lắk (HĐ #NK0891)", fiveDaysAgo, manager);
        if (arabica != null) saveStockTx(branch, arabica, StockTransactionType.RECEIVE, new BigDecimal("10000.0000"), "Nhập hàng Cầu Đất (HĐ #NK0892)", fiveDaysAgo, manager);
        if (dalatMilk != null) saveStockTx(branch, dalatMilk, StockTransactionType.RECEIVE, new BigDecimal("60.0000"), "Nhập sữa tươi thanh trùng lô mới", fiveDaysAgo, manager);
        if (suaDac != null) saveStockTx(branch, suaDac, StockTransactionType.RECEIVE, new BigDecimal("48.0000"), "Nhập sữa đặc Ông Thọ", fiveDaysAgo, manager);
        if (duong != null) saveStockTx(branch, duong, StockTransactionType.RECEIVE, new BigDecimal("50000.0000"), "Nhập đường Biên Hòa 1 bao 50kg", fiveDaysAgo, manager);
        if (siroDao != null) saveStockTx(branch, siroDao, StockTransactionType.RECEIVE, new BigDecimal("2100.0000"), "Nhập 3 chai siro Đào Monin", fiveDaysAgo, manager);
        if (matcha != null) saveStockTx(branch, matcha, StockTransactionType.RECEIVE, new BigDecimal("500.0000"), "Nhập bột Matcha Uji túi 500g", fiveDaysAgo, manager);
        if (traDen != null) saveStockTx(branch, traDen, StockTransactionType.RECEIVE, new BigDecimal("5000.0000"), "Nhập trà đen Phúc Long", fiveDaysAgo, manager);
        if (lyNhua != null) saveStockTx(branch, lyNhua, StockTransactionType.RECEIVE, new BigDecimal("2000.0000"), "Nhập 2 thùng ly nhựa PET", fiveDaysAgo, manager);
        if (ongHut != null) saveStockTx(branch, ongHut, StockTransactionType.RECEIVE, new BigDecimal("3000.0000"), "Nhập ống hút giấy thân thiện", fiveDaysAgo, manager);

        // 2. Xuất quầy pha chế (ISSUE)
        if (robusta != null) saveStockTx(branch, robusta, StockTransactionType.ISSUE, new BigDecimal("4000.0000"), "Xuất quầy bar pha chế", threeDaysAgo, manager);
        if (dalatMilk != null) saveStockTx(branch, dalatMilk, StockTransactionType.ISSUE, new BigDecimal("35.0000"), "Xuất quầy bar pha chế", threeDaysAgo, manager);
        if (suaDac != null) saveStockTx(branch, suaDac, StockTransactionType.ISSUE, new BigDecimal("24.0000"), "Xuất quầy bar pha chế", threeDaysAgo, manager);
        if (duong != null) saveStockTx(branch, duong, StockTransactionType.ISSUE, new BigDecimal("12000.0000"), "Xuất quầy bar pha chế", threeDaysAgo, manager);
        if (siroDao != null) saveStockTx(branch, siroDao, StockTransactionType.ISSUE, new BigDecimal("700.0000"), "Xuất 1 chai quầy bar", threeDaysAgo, manager);
        if (traDen != null) saveStockTx(branch, traDen, StockTransactionType.ISSUE, new BigDecimal("1200.0000"), "Xuất quầy bar pha chế", threeDaysAgo, manager);
        if (lyNhua != null) saveStockTx(branch, lyNhua, StockTransactionType.ISSUE, new BigDecimal("600.0000"), "Xuất quầy bar phục vụ", threeDaysAgo, manager);
        if (ongHut != null) saveStockTx(branch, ongHut, StockTransactionType.ISSUE, new BigDecimal("600.0000"), "Xuất quầy bar phục vụ", threeDaysAgo, manager);

        // 3. Hủy hàng (DISPOSAL)
        if (robusta != null) saveStockTx(branch, robusta, StockTransactionType.DISPOSAL, new BigDecimal("300.0000"), "Hạt cà phê bị rách bao bì ẩm mốc", twoDaysAgo, manager);
        if (dalatMilk != null) saveStockTx(branch, dalatMilk, StockTransactionType.DISPOSAL, new BigDecimal("2.0000"), "Hộp sữa bị móp vỡ khi vận chuyển", twoDaysAgo, manager);

        // 4. Điều chỉnh sau kiểm kê (ADJUST)
        if (robusta != null) saveStockTx(branch, robusta, StockTransactionType.ADJUST, new BigDecimal("-100.0000"), "Điều chỉnh sau kiểm kê định kỳ", twoDaysAgo, manager);

        // 5. Phiên kiểm kê gần nhất đã chốt (Stocktake)
        Stocktake stocktake = stocktakeRepository.save(Stocktake.builder()
                .branch(branch)
                .startedBy(manager)
                .startedAt(today.minusDays(2).atTime(11, 30).atZone(DateTimeUtil.VN).toInstant())
                .closedAt(today.minusDays(2).atTime(12, 0).atZone(DateTimeUtil.VN).toInstant())
                .status(StocktakeStatus.ADJUSTED)
                .build());

        if (robusta != null) {
            stocktakeLineRepository.save(StocktakeLine.builder()
                    .id(new StocktakeLineId(stocktake.getId(), robusta.getId()))
                    .stocktake(stocktake).ingredient(robusta)
                    .bookQuantity(new BigDecimal("12100.0000"))
                    .countedQuantity(new BigDecimal("12000.0000"))
                    .variance(new BigDecimal("-100.0000"))
                    .build());
        }

        if (arabica != null) {
            stocktakeLineRepository.save(StocktakeLine.builder()
                    .id(new StocktakeLineId(stocktake.getId(), arabica.getId()))
                    .stocktake(stocktake).ingredient(arabica)
                    .bookQuantity(new BigDecimal("15000.0000"))
                    .countedQuantity(new BigDecimal("15000.0000"))
                    .variance(BigDecimal.ZERO)
                    .build());
        }

        if (dalatMilk != null) {
            stocktakeLineRepository.save(StocktakeLine.builder()
                    .id(new StocktakeLineId(stocktake.getId(), dalatMilk.getId()))
                    .stocktake(stocktake).ingredient(dalatMilk)
                    .bookQuantity(new BigDecimal("98.0000"))
                    .countedQuantity(new BigDecimal("98.0000"))
                    .variance(BigDecimal.ZERO)
                    .build());
        }

        log.info("Đã seed biến động xuất/nhập/hủy/điều chỉnh kho và phiên kiểm kê stocktake cho BR01.");
    }

    private void saveStockTx(Branch branch, Ingredient ingredient, StockTransactionType type,
                             BigDecimal qty, String reason, Instant at, User user) {
        stockTransactionRepository.save(StockTransaction.builder()
                .branch(branch)
                .ingredient(ingredient)
                .type(type)
                .quantityBase(qty)
                .reason(reason)
                .createdAt(at)
                .createdBy(user)
                .build());
    }

    private void seedShiftsAttendanceAndOrders(Branch br01, Branch br02, User manager1,
                                               User cashier1, User cashier2, User cashier3,
                                               User barista1, User waiter1,
                                               Map<String, Product> menu) {
        LocalDate today = DateTimeUtil.todayVN();
        Random random = new Random(42); // Seed cố định để dữ liệu tái lập đẹp mắt

        // Seed 6 ngày trong quá khứ (từ today - 6 đến today - 1)
        for (int i = 6; i >= 1; i--) {
            LocalDate date = today.minusDays(i);
            seedHistoricalDay(br01, date, cashier1, cashier2, barista1, waiter1, manager1, menu, random);
        }

        // Seed ngày hôm nay (today): Ca sáng đã đóng, ca chiều đang mở (Open)
        seedToday(br01, today, cashier1, barista1, waiter1, menu, random);

        // Seed thêm doanh số cho BR02 để báo cáo hợp nhất chuỗi (UC40) có dữ liệu so sánh
        seedBranch2Sales(br02, today, cashier3, menu, random);
    }

    private void seedHistoricalDay(Branch branch, LocalDate date, User cashier1, User cashier2,
                                   User barista1, User waiter1, User manager1,
                                   Map<String, Product> menu, Random random) {
        // --- 1. Ca sáng (Morning Shift: 07:00 - 12:30, CLOSED) ---
        Instant morningStart = date.atTime(7, 0).atZone(DateTimeUtil.VN).toInstant();
        Instant morningEnd = date.atTime(12, 30).atZone(DateTimeUtil.VN).toInstant();

        Shift morningShift = shiftRepository.save(Shift.builder()
                .branchId(branch.getId())
                .shiftDate(date)
                .shiftType(ShiftType.MORNING)
                .plannedStart(morningStart)
                .plannedEnd(morningEnd)
                .openedAt(morningStart)
                .closedAt(morningEnd)
                .openedBy(cashier1.getId())
                .closedBy(cashier1.getId())
                .openingCash(new BigDecimal("1000000.00"))
                .status(ShiftStatus.CLOSED)
                .build());

        // Phân ca
        assignShift(morningShift, cashier1);
        assignShift(morningShift, barista1);
        assignShift(morningShift, waiter1);

        // Chấm công
        recordAttendance(branch, cashier1, morningStart, morningEnd, 330L, date, AttendanceStatus.COMPLETED);
        recordAttendance(branch, barista1, morningStart, morningEnd, 330L, date, AttendanceStatus.COMPLETED);
        recordAttendance(branch, waiter1, morningStart, morningEnd, 330L, date, AttendanceStatus.COMPLETED);

        // Giao dịch tiền mặt đầu ca
        saveCashTx(morningShift, CashTransactionType.OPEN, new BigDecimal("1000000.00"), "Mở ca sáng với tiền két chuẩn", morningStart, cashier1);
        saveCashTx(morningShift, CashTransactionType.CASH_IN, new BigDecimal("100000.00"), "Nạp bổ sung tiền lẻ mệnh giá 10k-20k", morningStart.plusSeconds(1800), cashier1);
        saveCashTx(morningShift, CashTransactionType.CASH_OUT, new BigDecimal("50000.00"), "Mua chanh sả và túi đựng rác", morningStart.plusSeconds(7200), cashier1);

        // Tạo 8-12 đơn hàng ca sáng (Tập trung giờ cao điểm 08:00 - 09:00)
        ShiftSalesTotals morningTotals = generateOrdersForShift(branch, morningShift, cashier1, date, 7, 30, 12, 0, 10, menu, random);

        // Tính tiền két & đối soát
        BigDecimal expectedMorningCash = new BigDecimal("1000000.00")
                .add(morningTotals.cashSales)
                .add(new BigDecimal("100000.00"))
                .subtract(new BigDecimal("50000.00"));

        morningShift.setCashRevenue(morningTotals.cashSales);
        morningShift.setTransferRevenue(morningTotals.transferSales);
        morningShift.setExpectedCash(expectedMorningCash);
        morningShift.setCountedCash(expectedMorningCash);
        morningShift.setVariance(BigDecimal.ZERO);
        shiftRepository.save(morningShift);

        saveCashTx(morningShift, CashTransactionType.CLOSE, expectedMorningCash, "Kiểm đếm két ca sáng khớp 100%", morningEnd, cashier1);

        // --- 2. Ca chiều/tối (Evening Shift: 13:00 - 21:30, CLOSED) ---
        Instant eveningStart = date.atTime(13, 0).atZone(DateTimeUtil.VN).toInstant();
        Instant eveningEnd = date.atTime(21, 30).atZone(DateTimeUtil.VN).toInstant();

        Shift eveningShift = shiftRepository.save(Shift.builder()
                .branchId(branch.getId())
                .shiftDate(date)
                .shiftType(ShiftType.EVENING)
                .plannedStart(eveningStart)
                .plannedEnd(eveningEnd)
                .openedAt(eveningStart)
                .closedAt(eveningEnd)
                .openedBy(cashier2.getId())
                .closedBy(cashier2.getId())
                .openingCash(new BigDecimal("1000000.00"))
                .status(ShiftStatus.CLOSED)
                .build());

        assignShift(eveningShift, cashier2);
        assignShift(eveningShift, barista1);
        assignShift(eveningShift, waiter1);

        recordAttendance(branch, cashier2, eveningStart, eveningEnd, 510L, date, AttendanceStatus.COMPLETED);
        recordAttendance(branch, barista1, eveningStart, eveningEnd, 510L, date, AttendanceStatus.COMPLETED);
        recordAttendance(branch, waiter1, eveningStart, eveningEnd, 510L, date, AttendanceStatus.COMPLETED);

        saveCashTx(eveningShift, CashTransactionType.OPEN, new BigDecimal("1000000.00"), "Mở két ca chiều", eveningStart, cashier2);
        saveCashTx(eveningShift, CashTransactionType.CASH_IN, new BigDecimal("50000.00"), "Bổ sung tiền lẻ", eveningStart.plusSeconds(3600), cashier2);

        // Tạo 9-14 đơn hàng ca chiều (Tập trung giờ cao điểm 13:30 - 15:00)
        ShiftSalesTotals eveningTotals = generateOrdersForShift(branch, eveningShift, cashier2, date, 13, 15, 21, 0, 11, menu, random);

        BigDecimal expectedEveningCash = new BigDecimal("1000000.00")
                .add(eveningTotals.cashSales)
                .add(new BigDecimal("50000.00"));

        eveningShift.setCashRevenue(eveningTotals.cashSales);
        eveningShift.setTransferRevenue(eveningTotals.transferSales);
        eveningShift.setExpectedCash(expectedEveningCash);

        // Vào ngày hôm qua (yesterday) tạo một case chênh lệch nhẹ -10.000đ để hiển thị Variance trên báo cáo dòng tiền UC37
        if (date.equals(DateTimeUtil.todayVN().minusDays(1))) {
            BigDecimal counted = expectedEveningCash.subtract(new BigDecimal("10000.00"));
            eveningShift.setCountedCash(counted);
            eveningShift.setVariance(new BigDecimal("-10000.00"));
            eveningShift.setDiscrepancyReason("Thối nhầm tiền thừa cho khách bàn 4");
            saveCashTx(eveningShift, CashTransactionType.CLOSE, counted, "Đóng ca lệch 10k tiền lẻ", eveningEnd, cashier2);
        } else {
            eveningShift.setCountedCash(expectedEveningCash);
            eveningShift.setVariance(BigDecimal.ZERO);
            saveCashTx(eveningShift, CashTransactionType.CLOSE, expectedEveningCash, "Đóng két ca chiều khớp", eveningEnd, cashier2);
        }
        shiftRepository.save(eveningShift);
    }

    private void seedToday(Branch branch, LocalDate today, User cashier1,
                           User barista1, User waiter1, Map<String, Product> menu, Random random) {
        // 1. Ca sáng hôm nay (ĐÃ ĐÓNG: 07:00 - 12:00)
        Instant morningStart = today.atTime(6, 55).atZone(DateTimeUtil.VN).toInstant();
        Instant morningEnd = today.atTime(12, 5).atZone(DateTimeUtil.VN).toInstant();

        Shift morningShift = shiftRepository.save(Shift.builder()
                .branchId(branch.getId())
                .shiftDate(today)
                .shiftType(ShiftType.MORNING)
                .plannedStart(today.atTime(7, 0).atZone(DateTimeUtil.VN).toInstant())
                .plannedEnd(today.atTime(12, 0).atZone(DateTimeUtil.VN).toInstant())
                .openedAt(morningStart)
                .closedAt(morningEnd)
                .openedBy(cashier1.getId())
                .closedBy(cashier1.getId())
                .openingCash(new BigDecimal("1000000.00"))
                .status(ShiftStatus.CLOSED)
                .build());

        assignShift(morningShift, cashier1);
        assignShift(morningShift, barista1);

        // Chấm công: Thu ngân đúng giờ; Barista đi muộn 15 phút (07:15) để lên KPI "Đi muộn" UC34 & UC39
        recordAttendance(branch, cashier1, morningStart, morningEnd, 310L, today, AttendanceStatus.COMPLETED);
        recordAttendance(branch, barista1, today.atTime(7, 15).atZone(DateTimeUtil.VN).toInstant(), morningEnd, 290L, today, AttendanceStatus.COMPLETED);

        saveCashTx(morningShift, CashTransactionType.OPEN, new BigDecimal("1000000.00"), "Mở két ca sáng", morningStart, cashier1);
        saveCashTx(morningShift, CashTransactionType.CASH_IN, new BigDecimal("150000.00"), "Thêm tiền lẻ đầu ngày", morningStart.plusSeconds(1800), cashier1);
        saveCashTx(morningShift, CashTransactionType.CASH_OUT, new BigDecimal("40000.00"), "Mua đá viên khẩn cấp", morningStart.plusSeconds(7200), cashier1);

        // 10 đơn hàng sáng (Có 5 đơn trong khung 08:00 - 09:00 để tạo Giờ cao điểm sáng UC42)
        ShiftSalesTotals morningTotals = generateOrdersForShift(branch, morningShift, cashier1, today, 7, 30, 11, 45, 10, menu, random);

        BigDecimal expectedMorningCash = new BigDecimal("1000000.00")
                .add(morningTotals.cashSales)
                .add(new BigDecimal("150000.00"))
                .subtract(new BigDecimal("40000.00"));

        morningShift.setCashRevenue(morningTotals.cashSales);
        morningShift.setTransferRevenue(morningTotals.transferSales);
        morningShift.setExpectedCash(expectedMorningCash);
        morningShift.setCountedCash(expectedMorningCash);
        morningShift.setVariance(BigDecimal.ZERO);
        shiftRepository.save(morningShift);

        saveCashTx(morningShift, CashTransactionType.CLOSE, expectedMorningCash, "Đóng két ca sáng cân đối", morningEnd, cashier1);

        // 2. Ca chiều hôm nay (ĐANG MỞ - STATUS = OPEN - PHỤC VỤ DASHBOARD & KÉT TIỀN)
        Instant afternoonStart = today.atTime(12, 25).atZone(DateTimeUtil.VN).toInstant();

        Shift afternoonShift = shiftRepository.save(Shift.builder()
                .branchId(branch.getId())
                .shiftDate(today)
                .shiftType(ShiftType.EVENING)
                .plannedStart(today.atTime(12, 30).atZone(DateTimeUtil.VN).toInstant())
                .plannedEnd(today.atTime(22, 0).atZone(DateTimeUtil.VN).toInstant())
                .openedAt(afternoonStart)
                .openedBy(cashier1.getId())
                .openingCash(new BigDecimal("1000000.00"))
                .status(ShiftStatus.OPEN) // OPEN để widget Két tiền ca đang mở hiển thị trên Dashboard!
                .build());

        assignShift(afternoonShift, cashier1);
        assignShift(afternoonShift, waiter1);

        // Chấm công: Thu ngân đã check-in ca chiều (CHECKED_IN, chưa check-out -> onDutyNow = 1); Phục vụ vắng (absent)
        attendanceRepository.save(Attendance.builder()
                .branchId(branch.getId())
                .userId(cashier1.getId())
                .checkInTime(afternoonStart)
                .workDate(today)
                .method("PIN")
                .status(AttendanceStatus.CHECKED_IN)
                .build());

        saveCashTx(afternoonShift, CashTransactionType.OPEN, new BigDecimal("1000000.00"), "Mở két ca chiều hôm nay", afternoonStart, cashier1);
        saveCashTx(afternoonShift, CashTransactionType.CASH_IN, new BigDecimal("200000.00"), "Bổ sung tiền lẻ ca chiều", afternoonStart.plusSeconds(1200), cashier1);

        // 8 đơn hàng chiều hôm nay (Tập trung giờ trưa 12:45 - 13:45 tạo Giờ cao điểm trưa)
        ShiftSalesTotals afternoonTotals = generateOrdersForShift(branch, afternoonShift, cashier1, today, 12, 45, 15, 30, 8, menu, random);

        // 1 đơn hoàn tiền một phần (PARTIALLY_REFUNDED) để kiểm tra cột Hoàn tiền / Doanh thu thuần
        createPartialRefundOrder(branch, afternoonShift, cashier1, today, menu);

        BigDecimal expectedAfternoonCash = new BigDecimal("1000000.00")
                .add(afternoonTotals.cashSales)
                .add(new BigDecimal("200000.00"));

        afternoonShift.setCashRevenue(afternoonTotals.cashSales);
        afternoonShift.setTransferRevenue(afternoonTotals.transferSales);
        afternoonShift.setExpectedCash(expectedAfternoonCash);
        afternoonShift.setCountedCash(null); // Chưa đóng ca -> null
        afternoonShift.setVariance(null);
        shiftRepository.save(afternoonShift);

        log.info("Đã tạo ca chiều ĐANG MỞ (status=OPEN) hôm nay cho BR01 để kích hoạt widget két tiền Dashboard.");
    }

    private void seedBranch2Sales(Branch br02, LocalDate today, User cashier3, Map<String, Product> menu, Random random) {
        for (int i = 3; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            Instant start = date.atTime(8, 0).atZone(DateTimeUtil.VN).toInstant();
            Instant end = date.atTime(21, 0).atZone(DateTimeUtil.VN).toInstant();

            Shift shift = shiftRepository.save(Shift.builder()
                    .branchId(br02.getId())
                    .shiftDate(date)
                    .shiftType(ShiftType.MORNING)
                    .plannedStart(start)
                    .plannedEnd(end)
                    .openedAt(start)
                    .closedAt(i == 0 ? null : end)
                    .openedBy(cashier3.getId())
                    .closedBy(i == 0 ? null : cashier3.getId())
                    .openingCash(new BigDecimal("1000000.00"))
                    .status(i == 0 ? ShiftStatus.OPEN : ShiftStatus.CLOSED)
                    .build());

            assignShift(shift, cashier3);
            recordAttendance(br02, cashier3, start, i == 0 ? null : end, i == 0 ? null : 780L, date,
                    i == 0 ? AttendanceStatus.CHECKED_IN : AttendanceStatus.COMPLETED);

            saveCashTx(shift, CashTransactionType.OPEN, new BigDecimal("1000000.00"), "Mở két BR02", start, cashier3);

            ShiftSalesTotals totals = generateOrdersForShift(br02, shift, cashier3, date, 8, 30, 18, 0, 6, menu, random);
            BigDecimal expected = new BigDecimal("1000000.00").add(totals.cashSales);
            shift.setCashRevenue(totals.cashSales);
            shift.setTransferRevenue(totals.transferSales);
            shift.setExpectedCash(expected);
            if (i > 0) {
                shift.setCountedCash(expected);
                shift.setVariance(BigDecimal.ZERO);
                saveCashTx(shift, CashTransactionType.CLOSE, expected, "Đóng két BR02", end, cashier3);
            }
            shiftRepository.save(shift);
        }
    }

    private void ensureTodayOpenShift(Branch branch, User cashier) {
        LocalDate today = DateTimeUtil.todayVN();
        if (shiftRepository.findFirstByBranchIdAndStatusOrderByOpenedAtDesc(branch.getId(), ShiftStatus.OPEN).isEmpty()) {
            Instant now = Instant.now();
            Shift shift = shiftRepository.save(Shift.builder()
                    .branchId(branch.getId())
                    .shiftDate(today)
                    .shiftType(ShiftType.EVENING)
                    .plannedStart(today.atTime(12, 30).atZone(DateTimeUtil.VN).toInstant())
                    .plannedEnd(today.atTime(22, 0).atZone(DateTimeUtil.VN).toInstant())
                    .openedAt(now)
                    .openedBy(cashier.getId())
                    .openingCash(new BigDecimal("1000000.00"))
                    .cashRevenue(BigDecimal.ZERO)
                    .transferRevenue(BigDecimal.ZERO)
                    .expectedCash(new BigDecimal("1000000.00"))
                    .status(ShiftStatus.OPEN)
                    .build());
            saveCashTx(shift, CashTransactionType.OPEN, new BigDecimal("1000000.00"), "Mở ca làm việc hiện tại", now, cashier);
        }
    }

    private record ShiftSalesTotals(BigDecimal cashSales, BigDecimal transferSales) {
    }

    private ShiftSalesTotals generateOrdersForShift(Branch branch, Shift shift, User cashier,
                                                    LocalDate date, int startH, int startM,
                                                    int endH, int endM, int orderCount,
                                                    Map<String, Product> menu, Random random) {
        BigDecimal cash = BigDecimal.ZERO;
        BigDecimal transfer = BigDecimal.ZERO;

        int totalMinutes = (endH * 60 + endM) - (startH * 60 + startM);
        int step = Math.max(totalMinutes / (orderCount + 1), 5);

        for (int i = 0; i < orderCount; i++) {
            int currentMinuteOffset = (startH * 60 + startM) + (i + 1) * step;
            int h = currentMinuteOffset / 60;
            int m = currentMinuteOffset % 60;
            Instant orderTime = date.atTime(h, m).atZone(DateTimeUtil.VN).toInstant();

            // Phân bổ tỷ lệ thanh toán: 60% Tiền mặt, 40% Chuyển khoản VietQR
            PaymentMethod method = (i % 5 < 3) ? PaymentMethod.CASH : PaymentMethod.VIETQR;

            // Chọn sản phẩm dựa trên độ phổ biến thực tế
            List<OrderLineSeed> lines = selectRealisticOrderLines(menu, random);
            BigDecimal totalAmount = lines.stream().map(OrderLineSeed::total).reduce(BigDecimal.ZERO, BigDecimal::add);

            // Thỉnh thoảng có chiết khấu voucher/khuyến mãi 10.000đ - 15.000đ
            BigDecimal discount = (i % 4 == 0) ? new BigDecimal("10000.00") : BigDecimal.ZERO;
            if (discount.compareTo(totalAmount) >= 0) {
                discount = BigDecimal.ZERO;
            }
            BigDecimal finalAmount = totalAmount.subtract(discount);

            String receiptNo = "HD%s-%s-%03d".formatted(branch.getCode(), date.toString().replace("-", ""), random.nextInt(900) + 100);

            Order order = orderRepository.save(Order.builder()
                    .branchId(branch.getId())
                    .shiftId(shift.getId())
                    .cashierId(cashier.getId())
                    .status(OrderStatus.PAID)
                    .totalAmount(totalAmount)
                    .discountAmount(discount)
                    .finalAmount(finalAmount)
                    .receiptNo(receiptNo)
                    .createdAt(orderTime)
                    .paidAt(orderTime.plusSeconds(30))
                    .build());

            for (OrderLineSeed l : lines) {
                orderLineRepository.save(OrderLine.builder()
                        .orderId(order.getId())
                        .productId(l.product().getId())
                        .size(l.size().name())
                        .quantity(l.qty())
                        .unitPrice(l.unitPrice())
                        .toppingTotal(BigDecimal.ZERO)
                        .lineTotal(l.total())
                        .build());
            }

            paymentRepository.save(Payment.builder()
                    .orderId(order.getId())
                    .method(method)
                    .amount(finalAmount)
                    .status(PaymentStatus.SUCCESS)
                    .referenceCode(method == PaymentMethod.VIETQR ? "VQR" + order.getId() : null)
                    .createdAt(orderTime.plusSeconds(30))
                    .build());

            if (method == PaymentMethod.CASH) {
                cash = cash.add(finalAmount);
            } else {
                transfer = transfer.add(finalAmount);
            }
        }

        return new ShiftSalesTotals(cash, transfer);
    }

    private void createPartialRefundOrder(Branch branch, Shift shift, User cashier, LocalDate date, Map<String, Product> menu) {
        Instant orderTime = date.atTime(13, 15).atZone(DateTimeUtil.VN).toInstant();
        Product cfSua = menu.get("CF_SUADA");
        Product traDao = menu.get("TEA_DAO");

        BigDecimal cfSuaTotal = new BigDecimal("70000.00"); // 2 ly size M = 70k
        BigDecimal traDaoTotal = new BigDecimal("45000.00"); // 1 ly size M = 45k
        BigDecimal total = cfSuaTotal.add(traDaoTotal); // 115k

        Order order = orderRepository.save(Order.builder()
                .branchId(branch.getId())
                .shiftId(shift.getId())
                .cashierId(cashier.getId())
                .status(OrderStatus.PARTIALLY_REFUNDED)
                .totalAmount(total)
                .discountAmount(BigDecimal.ZERO)
                .finalAmount(total)
                .receiptNo("HD-REFUND-" + date.toString().replace("-", ""))
                .createdAt(orderTime)
                .paidAt(orderTime.plusSeconds(20))
                .build());

        orderLineRepository.save(OrderLine.builder()
                .orderId(order.getId()).productId(cfSua.getId())
                .size("M").quantity(2).unitPrice(new BigDecimal("35000.00"))
                .toppingTotal(BigDecimal.ZERO).lineTotal(cfSuaTotal).build());

        orderLineRepository.save(OrderLine.builder()
                .orderId(order.getId()).productId(traDao.getId())
                .size("M").quantity(1).unitPrice(new BigDecimal("45000.00"))
                .toppingTotal(BigDecimal.ZERO).lineTotal(traDaoTotal).build());

        Payment p = paymentRepository.save(Payment.builder()
                .orderId(order.getId())
                .method(PaymentMethod.CASH)
                .amount(total)
                .status(PaymentStatus.SUCCESS)
                .createdAt(orderTime.plusSeconds(20))
                .build());

        refundRepository.save(Refund.builder()
                .orderId(order.getId())
                .paymentId(p.getId())
                .amount(new BigDecimal("45000.00"))
                .reason("Khách đổi ý trả lại ly Trà Đào do vội đi làm")
                .status(RefundStatus.DONE)
                .requestedBy(cashier.getId())
                .createdAt(orderTime.plusSeconds(300))
                .approvedAt(orderTime.plusSeconds(320))
                .build());
    }

    private record OrderLineSeed(Product product, ProductSizeName size, int qty, BigDecimal unitPrice, BigDecimal total) {
    }

    private List<OrderLineSeed> selectRealisticOrderLines(Map<String, Product> menu, Random random) {
        List<OrderLineSeed> lines = new ArrayList<>();
        // Sản phẩm được chọn theo tỷ trọng phổ biến F&B
        // Cà phê sữa đá có tần suất cao nhất (Top 1)
        int dice = random.nextInt(100);
        if (dice < 40) {
            lines.add(makeLine(menu.get("CF_SUADA"), ProductSizeName.M, new BigDecimal("35000.00"), 1 + random.nextInt(2)));
        } else if (dice < 65) {
            lines.add(makeLine(menu.get("TEA_DAO"), ProductSizeName.M, new BigDecimal("45000.00"), 1));
        } else if (dice < 80) {
            lines.add(makeLine(menu.get("CF_BACXIU"), ProductSizeName.M, new BigDecimal("39000.00"), 1));
        } else if (dice < 90) {
            lines.add(makeLine(menu.get("CF_DENDA"), ProductSizeName.M, new BigDecimal("29000.00"), 1));
        } else {
            lines.add(makeLine(menu.get("TEA_MATCHA"), ProductSizeName.M, new BigDecimal("49000.00"), 1));
        }

        // 30% đơn hàng gọi thêm món thứ hai (bánh hoặc nước thứ hai)
        if (random.nextInt(10) < 3) {
            if (random.nextBoolean()) {
                lines.add(makeLine(menu.get("BAKERY_TIRAMISU"), ProductSizeName.M, new BigDecimal("45000.00"), 1));
            } else {
                lines.add(makeLine(menu.get("BAKERY_CROISSANT"), ProductSizeName.M, new BigDecimal("35000.00"), 1));
            }
        }

        return lines;
    }

    private OrderLineSeed makeLine(Product product, ProductSizeName size, BigDecimal price, int qty) {
        BigDecimal total = price.multiply(BigDecimal.valueOf(qty));
        return new OrderLineSeed(product, size, qty, price, total);
    }

    private void assignShift(Shift shift, User employee) {
        shiftAssignmentRepository.save(ShiftAssignment.builder()
                .id(new ShiftAssignmentId(shift.getId(), employee.getId()))
                .shift(shift)
                .employee(employee)
                .build());
    }

    private void recordAttendance(Branch branch, User user, Instant checkIn, Instant checkOut,
                                  Long workedMinutes, LocalDate date, AttendanceStatus status) {
        attendanceRepository.save(Attendance.builder()
                .branchId(branch.getId())
                .userId(user.getId())
                .checkInTime(checkIn)
                .checkOutTime(checkOut)
                .workedMinutes(workedMinutes)
                .workDate(date)
                .method("PIN")
                .status(status)
                .build());
    }

    private void saveCashTx(Shift shift, CashTransactionType type, BigDecimal amount,
                            String reason, Instant at, User user) {
        cashTransactionRepository.save(CashTransaction.builder()
                .shiftId(shift.getId())
                .type(type)
                .amount(amount)
                .reason(reason)
                .createdAt(at)
                .createdBy(user.getId())
                .build());
    }
}
