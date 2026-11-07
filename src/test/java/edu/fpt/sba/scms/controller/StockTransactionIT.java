package edu.fpt.sba.scms.controller;

import edu.fpt.sba.scms.common.enums.BranchStatus;
import edu.fpt.sba.scms.common.enums.Roles;
import edu.fpt.sba.scms.common.enums.AuditAction;
import edu.fpt.sba.scms.common.enums.UserStatus;
import edu.fpt.sba.scms.dto.request.CreateReceiveSlipRequest;
import edu.fpt.sba.scms.dto.request.CreateIssueSlipRequest;
import edu.fpt.sba.scms.dto.request.CreateDisposalSlipRequest;
import edu.fpt.sba.scms.dto.response.StockIssueSlipResponse;
import edu.fpt.sba.scms.dto.response.StockDisposalSlipResponse;
import edu.fpt.sba.scms.common.enums.StockTransactionType;
import edu.fpt.sba.scms.model.Branch;
import edu.fpt.sba.scms.model.Ingredient;
import edu.fpt.sba.scms.model.Inventory;
import edu.fpt.sba.scms.model.Role;
import edu.fpt.sba.scms.model.StockTransaction;
import edu.fpt.sba.scms.model.User;
import edu.fpt.sba.scms.repository.BranchRepository;
import edu.fpt.sba.scms.repository.IngredientRepository;
import edu.fpt.sba.scms.repository.InventoryRepository;
import edu.fpt.sba.scms.repository.RoleRepository;
import edu.fpt.sba.scms.repository.StockTransactionRepository;
import edu.fpt.sba.scms.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StockTransactionIT {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private IngredientRepository ingredientRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private StockTransactionRepository stockTransactionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Branch branch1;
    private Ingredient robustaCoffee;
    private Ingredient dalatMilk;
    private Ingredient inactiveSugar;

    @BeforeEach
    void setupData() {
        branch1 = branchRepository.findByCode("BR01").orElseGet(() -> branchRepository.save(
                Branch.builder()
                        .code("BR01")
                        .name("Chi nhánh Quận 1")
                        .status(BranchStatus.OPEN)
                        .build()));

        robustaCoffee = ingredientRepository.findByName("Hạt Cà phê Robusta Đắk Lắk")
                .orElseGet(() -> ingredientRepository.save(Ingredient.builder()
                        .name("Hạt Cà phê Robusta Đắk Lắk")
                        .baseUnit("g")
                        .packUnit("Bao 5kg")
                        .unitsPerPack(new BigDecimal("5000.0000"))
                        .unitCost(new BigDecimal("150.00"))
                        .status("ACTIVE")
                        .build()));

        dalatMilk = ingredientRepository.findByName("Sữa tươi thanh trùng Dalat Milk")
                .orElseGet(() -> ingredientRepository.save(Ingredient.builder()
                        .name("Sữa tươi thanh trùng Dalat Milk")
                        .baseUnit("hộp")
                        .packUnit("Thùng 12 hộp")
                        .unitsPerPack(new BigDecimal("12.0000"))
                        .unitCost(new BigDecimal("30000.00"))
                        .status("ACTIVE")
                        .build()));

        inactiveSugar = ingredientRepository.findByName("Đường đen Hàn Quốc (Hết dùng)")
                .orElseGet(() -> ingredientRepository.save(Ingredient.builder()
                        .name("Đường đen Hàn Quốc (Hết dùng)")
                        .baseUnit("g")
                        .packUnit("Gói 1kg")
                        .unitsPerPack(new BigDecimal("1000.0000"))
                        .unitCost(new BigDecimal("45000.00"))
                        .status("INACTIVE")
                        .build()));

        // Thiết lập tồn ban đầu cho robustaCoffee chạm ngưỡng tồn thấp
        Inventory inv1 = inventoryRepository.findByBranchIdAndIngredientId(branch1.getId(), robustaCoffee.getId())
                .orElseGet(() -> inventoryRepository.save(Inventory.builder()
                        .branch(branch1)
                        .ingredient(robustaCoffee)
                        .currentQtyBase(new BigDecimal("12000.0000"))
                        .currentPackUnits(new BigDecimal("2.4000"))
                        .safetyStockLevel(new BigDecimal("20000.0000"))
                        .lowStockFlag(true)
                        .lastUpdated(Instant.now())
                        .build()));
        inv1.setCurrentQtyBase(new BigDecimal("12000.0000"));
        inv1.setSafetyStockLevel(new BigDecimal("20000.0000"));
        inv1.recalculateStockAndFlag(robustaCoffee.getUnitsPerPack());
        inventoryRepository.save(inv1);
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login").contentType(JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("accessToken")
                .asText();
    }

    private String createAndLoginBm(Branch branch, String username) throws Exception {
        Role bmRole = roleRepository.findByCode(Roles.BRANCH_MANAGER.code()).orElseThrow();
        if (!userRepository.existsByUsername(username)) {
            userRepository.save(User.builder()
                    .branchId(branch.getId())
                    .roleId(bmRole.getId())
                    .fullName("BM " + branch.getCode())
                    .email(username + "@scms.local")
                    .username(username)
                    .passwordHash(passwordEncoder.encode("Bm@123456"))
                    .position(Roles.BRANCH_MANAGER)
                    .status(UserStatus.ACTIVE)
                    .build());
        }
        return login(username, "Bm@123456");
    }

    private String createAndLoginCashier(Branch branch, String username) throws Exception {
        Role cashierRole = roleRepository.findByCode(Roles.CASHIER.code()).orElseThrow();
        if (!userRepository.existsByUsername(username)) {
            userRepository.save(User.builder()
                    .branchId(branch.getId())
                    .roleId(cashierRole.getId())
                    .fullName("Cashier " + branch.getCode())
                    .email(username + "@scms.local")
                    .username(username)
                    .passwordHash(passwordEncoder.encode("Cashier@123456"))
                    .position(Roles.CASHIER)
                    .status(UserStatus.ACTIVE)
                    .build());
        }
        return login(username, "Cashier@123456");
    }

    private static MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder, String token) {
        return builder.header("Authorization", "Bearer " + token);
    }

    @Test
    @DisplayName("TC-01: Nhập 1 món thành công (Quy đổi kiện -> cơ sở, tính VAT 10%, sinh mã phiếu PNK-...)")
    void receiveStock_singleItem_success() throws Exception {
        String bmToken = createAndLoginBm(branch1, "bm_uc28_tc01");

        CreateReceiveSlipRequest request = new CreateReceiveSlipRequest(
                "SUPPLIER",
                "Công ty Cầu Đất Farm",
                "HD-CD-001",
                "Nhập cà phê phục vụ tuần mới",
                List.of(
                        new CreateReceiveSlipRequest.Item(
                                robustaCoffee.getId(),
                                new BigDecimal("5.0"),
                                BigDecimal.ZERO,
                                new BigDecimal("750000"),
                                LocalDate.now().plusMonths(6)
                        )
                )
        );

        // Subtotal = 5 * 750000 = 3,750,000
        // VAT 10% = 375,000
        // Total = 4,125,000
        // Added qty base = 5 * 5000 = 25,000g. New current qty base = 12,000 + 25,000 = 37,000g
        // 37,000g > 20,000g (safetyStockLevel) -> lowStockFlag becomes false!

        mockMvc.perform(authed(post("/api/v1/stock-transactions/receive").contentType(JSON)
                        .content(objectMapper.writeValueAsString(request)), bmToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.slipCode", containsString("PNK-")))
                .andExpect(jsonPath("$.data.branchId").value(branch1.getId()))
                .andExpect(jsonPath("$.data.branchName").value(branch1.getName()))
                .andExpect(jsonPath("$.data.source").value("SUPPLIER"))
                .andExpect(jsonPath("$.data.supplierName").value("Công ty Cầu Đất Farm"))
                .andExpect(jsonPath("$.data.invoiceRef").value("HD-CD-001"))
                .andExpect(jsonPath("$.data.subtotalAmount").value(3750000))
                .andExpect(jsonPath("$.data.vatAmount").value(375000))
                .andExpect(jsonPath("$.data.totalPayable").value(4125000))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].ingredientId").value(robustaCoffee.getId()))
                .andExpect(jsonPath("$.data.items[0].quantityBaseAdded").value(25000.0))
                .andExpect(jsonPath("$.data.items[0].lineTotal").value(3750000.0))
                .andExpect(jsonPath("$.data.items[0].newCurrentQtyBase").value(37000.0))
                .andExpect(jsonPath("$.data.items[0].newCurrentPackUnits").value(7.4))
                .andExpect(jsonPath("$.data.items[0].lowStockFlag").value(false));

        // Kiểm tra bản ghi trong StockTransaction repository
        List<StockTransaction> txs = stockTransactionRepository.findByBranch_IdOrderByCreatedAtDesc(branch1.getId());
        assertThat(txs).isNotEmpty();
        StockTransaction latestTx = txs.get(0);
        assertThat(latestTx.getQuantityBase()).isEqualByComparingTo(new BigDecimal("25000.0000"));
        assertThat(latestTx.getReason()).contains("PNK-").contains("HD-CD-001").contains("Công ty Cầu Đất Farm");
    }

    @Test
    @DisplayName("TC-02: Nhập nhiều món cùng lúc (Cà phê và Sữa tươi theo đúng ví dụ BR-40)")
    void receiveStock_multipleItems_success() throws Exception {
        String bmToken = createAndLoginBm(branch1, "bm_uc28_tc02");

        CreateReceiveSlipRequest request = new CreateReceiveSlipRequest(
                "SUPPLIER",
                "Công ty Cổ phần Sữa Đà Lạt (Dalat Milk)",
                "HD-20261107-99",
                "Nhập lô sữa tươi thanh trùng và cà phê",
                List.of(
                        new CreateReceiveSlipRequest.Item(
                                robustaCoffee.getId(),
                                new BigDecimal("5.0"),
                                BigDecimal.ZERO,
                                new BigDecimal("750000"),
                                LocalDate.of(2026, 12, 15)
                        ),
                        new CreateReceiveSlipRequest.Item(
                                dalatMilk.getId(),
                                new BigDecimal("10.0"),
                                new BigDecimal("2.0"),
                                new BigDecimal("360000"),
                                LocalDate.of(2026, 11, 20)
                        )
                )
        );

        // Món 1: 5 * 750,000 = 3,750,000
        // Món 2: 10 * 360,000 + 2 * (360,000 / 12) = 3,600,000 + 60,000 = 3,660,000
        // Subtotal = 3,750,000 + 3,660,000 = 7,410,000
        // VAT 10% = 741,000
        // Total = 8,151,000
        mockMvc.perform(authed(post("/api/v1/stock-transactions/receive").contentType(JSON)
                        .content(objectMapper.writeValueAsString(request)), bmToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.subtotalAmount").value(7410000))
                .andExpect(jsonPath("$.data.vatAmount").value(741000))
                .andExpect(jsonPath("$.data.totalPayable").value(8151000))
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.items[0].lineTotal").value(3750000.0))
                .andExpect(jsonPath("$.data.items[1].quantityBaseAdded").value(122.0))
                .andExpect(jsonPath("$.data.items[1].lineTotal").value(3660000.0));
    }

    @Test
    @DisplayName("TC-03: Chống trùng hóa đơn trong vòng 7 ngày -> Trả về 409 Conflict")
    void receiveStock_duplicateInvoice_returns409() throws Exception {
        String bmToken = createAndLoginBm(branch1, "bm_uc28_tc03");

        CreateReceiveSlipRequest request = new CreateReceiveSlipRequest(
                "SUPPLIER",
                "Nhà cung cấp Test",
                "HD-DUP-12345",
                "Lần nhập thứ nhất",
                List.of(
                        new CreateReceiveSlipRequest.Item(
                                robustaCoffee.getId(),
                                new BigDecimal("1.0"),
                                BigDecimal.ZERO,
                                new BigDecimal("750000"),
                                null
                        )
                )
        );

        // Lần 1: Thành công
        mockMvc.perform(authed(post("/api/v1/stock-transactions/receive").contentType(JSON)
                        .content(objectMapper.writeValueAsString(request)), bmToken))
                .andExpect(status().isCreated());

        // Lần 2 với cùng số hóa đơn HD-DUP-12345 -> Phải trả về 409 Conflict
        mockMvc.perform(authed(post("/api/v1/stock-transactions/receive").contentType(JSON)
                        .content(objectMapper.writeValueAsString(request)), bmToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_CONFLICT"))
                .andExpect(jsonPath("$.message", containsString("HD-DUP-12345")));
    }

    @Test
    @DisplayName("TC-04: Tự động gỡ cờ Low Stock khi nhập hàng vượt mức an toàn (BR-45)")
    void receiveStock_unsetsLowStockFlag() throws Exception {
        String bmToken = createAndLoginBm(branch1, "bm_uc28_tc04");

        // Đảm bảo ban đầu robusta đang chạm ngưỡng an toàn
        Inventory inv = inventoryRepository.findByBranchIdAndIngredientId(branch1.getId(), robustaCoffee.getId()).orElseThrow();
        inv.setCurrentQtyBase(new BigDecimal("5000.0"));
        inv.setSafetyStockLevel(new BigDecimal("10000.0"));
        inv.recalculateStockAndFlag(robustaCoffee.getUnitsPerPack());
        inventoryRepository.save(inv);
        assertThat(inv.getLowStockFlag()).isTrue();

        // Nhập thêm 2 thùng (10,000g) -> Tồn mới = 15,000g > 10,000g an toàn
        CreateReceiveSlipRequest request = new CreateReceiveSlipRequest(
                "SUPPLIER",
                "Cầu Đất",
                "HD-RESTOCK-01",
                "Bù tồn thấp",
                List.of(
                        new CreateReceiveSlipRequest.Item(
                                robustaCoffee.getId(),
                                new BigDecimal("2.0"),
                                BigDecimal.ZERO,
                                new BigDecimal("750000"),
                                null
                        )
                )
        );

        mockMvc.perform(authed(post("/api/v1/stock-transactions/receive").contentType(JSON)
                        .content(objectMapper.writeValueAsString(request)), bmToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.items[0].lowStockFlag").value(false));

        Inventory updatedInv = inventoryRepository.findByBranchIdAndIngredientId(branch1.getId(), robustaCoffee.getId()).orElseThrow();
        assertThat(updatedInv.getLowStockFlag()).isFalse();
    }

    @Test
    @DisplayName("TC-05: AF-01 - Dữ liệu không hợp lệ (Số lượng = 0 hoặc đơn giá âm)")
    void receiveStock_invalidQuantityOrPrice_returns400() throws Exception {
        String bmToken = createAndLoginBm(branch1, "bm_uc28_af01");

        // 1. Số lượng kiện = 0 và số lẻ = 0
        CreateReceiveSlipRequest zeroQtyRequest = new CreateReceiveSlipRequest(
                "SUPPLIER",
                "NCC",
                "HD-ZERO",
                null,
                List.of(
                        new CreateReceiveSlipRequest.Item(
                                robustaCoffee.getId(),
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                new BigDecimal("500000"),
                                null
                        )
                )
        );
        mockMvc.perform(authed(post("/api/v1/stock-transactions/receive").contentType(JSON)
                        .content(objectMapper.writeValueAsString(zeroQtyRequest)), bmToken))
                .andExpect(status().isBadRequest());

        // 2. Đơn giá âm
        CreateReceiveSlipRequest negPriceRequest = new CreateReceiveSlipRequest(
                "SUPPLIER",
                "NCC",
                "HD-NEG",
                null,
                List.of(
                        new CreateReceiveSlipRequest.Item(
                                robustaCoffee.getId(),
                                BigDecimal.ONE,
                                BigDecimal.ZERO,
                                new BigDecimal("-1000"),
                                null
                        )
                )
        );
        mockMvc.perform(authed(post("/api/v1/stock-transactions/receive").contentType(JSON)
                        .content(objectMapper.writeValueAsString(negPriceRequest)), bmToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-06: AF-02 - Nguyên liệu INACTIVE không được phép nhập kho (422)")
    void receiveStock_inactiveIngredient_returns422() throws Exception {
        String bmToken = createAndLoginBm(branch1, "bm_uc28_af02");

        CreateReceiveSlipRequest request = new CreateReceiveSlipRequest(
                "SUPPLIER",
                "NCC",
                "HD-INACTIVE",
                null,
                List.of(
                        new CreateReceiveSlipRequest.Item(
                                inactiveSugar.getId(),
                                BigDecimal.ONE,
                                BigDecimal.ZERO,
                                new BigDecimal("45000"),
                                null
                        )
                )
        );

        mockMvc.perform(authed(post("/api/v1/stock-transactions/receive").contentType(JSON)
                        .content(objectMapper.writeValueAsString(request)), bmToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    @DisplayName("TC-07: Phân quyền - CASHIER hoặc không có Token bị từ chối (403 hoặc 401)")
    void receiveStock_unauthorizedOrForbidden() throws Exception {
        CreateReceiveSlipRequest request = new CreateReceiveSlipRequest(
                "SUPPLIER",
                "NCC",
                "HD-AUTH",
                null,
                List.of(
                        new CreateReceiveSlipRequest.Item(
                                robustaCoffee.getId(),
                                BigDecimal.ONE,
                                BigDecimal.ZERO,
                                new BigDecimal("750000"),
                                null
                        )
                )
        );

        // 1. Không có Token -> 401 Unauthorized
        mockMvc.perform(post("/api/v1/stock-transactions/receive").contentType(JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        // 2. Đăng nhập tài khoản CASHIER -> 403 Forbidden
        String cashierToken = createAndLoginCashier(branch1, "cashier_uc28");
        mockMvc.perform(authed(post("/api/v1/stock-transactions/receive").contentType(JSON)
                        .content(objectMapper.writeValueAsString(request)), cashierToken))
                .andExpect(status().isForbidden());
    }

    // ==========================================
    // UC29 — XUẤT KHO CHO QUẦY BAR (ISSUE STOCK)
    // ==========================================

    @Test
    @DisplayName("TC-01 (UC29): Xuất 2 thùng sữa (đủ tồn) -> Tồn kho giảm đúng 24 hộp, sinh mã PXK-..., trả về 201 Created")
    void issueStock_happyPath_success() throws Exception {
        String bmToken = createAndLoginBm(branch1, "bm_uc29_tc01");

        // Thiết lập tồn sữa: 120 hộp (= 10 thùng), an toàn 20 hộp
        Inventory milkInv = inventoryRepository.findByBranchIdAndIngredientId(branch1.getId(), dalatMilk.getId())
                .orElseGet(() -> inventoryRepository.save(Inventory.builder()
                        .branch(branch1)
                        .ingredient(dalatMilk)
                        .currentQtyBase(new BigDecimal("120.0000"))
                        .currentPackUnits(new BigDecimal("10.0000"))
                        .safetyStockLevel(new BigDecimal("20.0000"))
                        .lowStockFlag(false)
                        .lastUpdated(Instant.now())
                        .build()));
        milkInv.setCurrentQtyBase(new BigDecimal("120.0000"));
        milkInv.setSafetyStockLevel(new BigDecimal("20.0000"));
        milkInv.recalculateStockAndFlag(dalatMilk.getUnitsPerPack());
        inventoryRepository.save(milkInv);

        CreateIssueSlipRequest request = new CreateIssueSlipRequest(
                "BAR_DISPENSING",
                "Quầy Bar Chính - Tầng 1",
                "Trần Thị Pha Chế",
                "Xuất phục vụ ca sáng",
                List.of(
                        new CreateIssueSlipRequest.Item(
                                dalatMilk.getId(),
                                new BigDecimal("2.0")
                        )
                )
        );

        mockMvc.perform(authed(post("/api/v1/stock-transactions/issue").contentType(JSON)
                        .content(objectMapper.writeValueAsString(request)), bmToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.slipCode", containsString("PXK-")))
                .andExpect(jsonPath("$.data.purpose").value("BAR_DISPENSING"))
                .andExpect(jsonPath("$.data.destinationBar").value("Quầy Bar Chính - Tầng 1"))
                .andExpect(jsonPath("$.data.baristaName").value("Trần Thị Pha Chế"))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].ingredientId").value(dalatMilk.getId()))
                .andExpect(jsonPath("$.data.items[0].quantityPacks").value(2.0))
                .andExpect(jsonPath("$.data.items[0].quantityBaseDeducted").value(24.0))
                .andExpect(jsonPath("$.data.items[0].remainingQtyBase").value(96.0))
                .andExpect(jsonPath("$.data.items[0].remainingPackUnits").value(8.0))
                .andExpect(jsonPath("$.data.items[0].lowStockFlag").value(false));

        // Kiểm tra database sau giao dịch
        Inventory updatedInv = inventoryRepository.findByBranchIdAndIngredientId(branch1.getId(), dalatMilk.getId()).orElseThrow();
        assertThat(updatedInv.getCurrentQtyBase()).isEqualByComparingTo("96.0000");
        assertThat(updatedInv.getCurrentPackUnits()).isEqualByComparingTo("8.0000");
        assertThat(updatedInv.getLowStockFlag()).isFalse();

        List<StockTransaction> txs = stockTransactionRepository.findByBranch_IdOrderByCreatedAtDesc(branch1.getId());
        StockTransaction latestTx = txs.stream().filter(t -> t.getType() == StockTransactionType.ISSUE).findFirst().orElseThrow();
        assertThat(latestTx.getQuantityPacks()).isEqualByComparingTo("2.0000");
        assertThat(latestTx.getQuantityBase()).isEqualByComparingTo("24.0000");
        assertThat(latestTx.getReason()).contains("PXK-").contains("Quầy Bar Chính - Tầng 1").contains("Trần Thị Pha Chế");
    }

    @Test
    @DisplayName("TC-02 (UC29): Chặn xuất âm kho BR-42 -> Tồn còn 10 hộp, xuất 1 thùng (12 hộp) bị ném 422 INSUFFICIENT_STOCK")
    void issueStock_insufficientStock_throws422() throws Exception {
        String bmToken = createAndLoginBm(branch1, "bm_uc29_tc02");

        // Thiết lập tồn: 10 hộp sữa (< 12 hộp của 1 thùng)
        Inventory milkInv = inventoryRepository.findByBranchIdAndIngredientId(branch1.getId(), dalatMilk.getId())
                .orElseGet(() -> inventoryRepository.save(Inventory.builder()
                        .branch(branch1)
                        .ingredient(dalatMilk)
                        .currentQtyBase(new BigDecimal("10.0000"))
                        .currentPackUnits(new BigDecimal("0.8333"))
                        .safetyStockLevel(new BigDecimal("5.0000"))
                        .lowStockFlag(false)
                        .lastUpdated(Instant.now())
                        .build()));
        milkInv.setCurrentQtyBase(new BigDecimal("10.0000"));
        milkInv.recalculateStockAndFlag(dalatMilk.getUnitsPerPack());
        inventoryRepository.save(milkInv);

        CreateIssueSlipRequest request = new CreateIssueSlipRequest(
                "BAR_DISPENSING",
                "Quầy Bar Phụ",
                "Nguyễn Văn Barista",
                "Thử xuất quá tồn",
                List.of(
                        new CreateIssueSlipRequest.Item(
                                dalatMilk.getId(),
                                BigDecimal.ONE
                        )
                )
        );

        mockMvc.perform(authed(post("/api/v1/stock-transactions/issue").contentType(JSON)
                        .content(objectMapper.writeValueAsString(request)), bmToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.detailCode").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.message", containsString("Tồn kho nguyên liệu 'Sữa tươi thanh trùng Dalat Milk' hiện tại chỉ còn")));

        // Tồn kho không đổi (Rollback)
        Inventory unchangedInv = inventoryRepository.findByBranchIdAndIngredientId(branch1.getId(), dalatMilk.getId()).orElseThrow();
        assertThat(unchangedInv.getCurrentQtyBase()).isEqualByComparingTo("10.0000");
    }

    @Test
    @DisplayName("TC-03 (UC29): Tự động bật cờ Low Stock BR-45 khi tồn sau xuất <= safety_stock_level")
    void issueStock_triggersLowStockFlag() throws Exception {
        String bmToken = createAndLoginBm(branch1, "bm_uc29_tc03");

        // Thiết lập tồn: 30 hộp sữa, ngưỡng an toàn: 20 hộp. Đang an toàn (lowStockFlag = false)
        Inventory milkInv = inventoryRepository.findByBranchIdAndIngredientId(branch1.getId(), dalatMilk.getId())
                .orElseGet(() -> inventoryRepository.save(Inventory.builder()
                        .branch(branch1)
                        .ingredient(dalatMilk)
                        .currentQtyBase(new BigDecimal("30.0000"))
                        .currentPackUnits(new BigDecimal("2.5000"))
                        .safetyStockLevel(new BigDecimal("20.0000"))
                        .lowStockFlag(false)
                        .lastUpdated(Instant.now())
                        .build()));
        milkInv.setCurrentQtyBase(new BigDecimal("30.0000"));
        milkInv.setSafetyStockLevel(new BigDecimal("20.0000"));
        milkInv.recalculateStockAndFlag(dalatMilk.getUnitsPerPack());
        inventoryRepository.save(milkInv);
        assertThat(milkInv.getLowStockFlag()).isFalse();

        // Xuất 1 thùng (12 hộp) -> Còn 18 hộp <= 20 hộp ngưỡng an toàn
        CreateIssueSlipRequest request = new CreateIssueSlipRequest(
                "BAR_DISPENSING",
                "Quầy Bar Chính",
                "Lê Pha Chế",
                "Xuất làm chạm ngưỡng an toàn",
                List.of(
                        new CreateIssueSlipRequest.Item(
                                dalatMilk.getId(),
                                BigDecimal.ONE
                        )
                )
        );

        mockMvc.perform(authed(post("/api/v1/stock-transactions/issue").contentType(JSON)
                        .content(objectMapper.writeValueAsString(request)), bmToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.items[0].remainingQtyBase").value(18.0))
                .andExpect(jsonPath("$.data.items[0].lowStockFlag").value(true));

        Inventory updatedInv = inventoryRepository.findByBranchIdAndIngredientId(branch1.getId(), dalatMilk.getId()).orElseThrow();
        assertThat(updatedInv.getCurrentQtyBase()).isEqualByComparingTo("18.0000");
        assertThat(updatedInv.getLowStockFlag()).isTrue();
    }

    @Test
    @DisplayName("TC-04 (UC29): Pessimistic Lock bảo vệ đồng thời -> 2 request cùng xuất món cuối cùng, chỉ 1 thành công")
    void issueStock_concurrencyPessimisticLock() throws Exception {
        String bmToken = createAndLoginBm(branch1, "bm_uc29_tc04");

        // Thiết lập tồn: đúng 1 thùng sữa (12 hộp)
        Inventory milkInv = inventoryRepository.findByBranchIdAndIngredientId(branch1.getId(), dalatMilk.getId())
                .orElseGet(() -> inventoryRepository.save(Inventory.builder()
                        .branch(branch1)
                        .ingredient(dalatMilk)
                        .currentQtyBase(new BigDecimal("12.0000"))
                        .currentPackUnits(new BigDecimal("1.0000"))
                        .safetyStockLevel(new BigDecimal("5.0000"))
                        .lowStockFlag(false)
                        .lastUpdated(Instant.now())
                        .build()));
        milkInv.setCurrentQtyBase(new BigDecimal("12.0000"));
        milkInv.recalculateStockAndFlag(dalatMilk.getUnitsPerPack());
        inventoryRepository.save(milkInv);

        CreateIssueSlipRequest request = new CreateIssueSlipRequest(
                "BAR_DISPENSING",
                "Quầy Bar",
                "Barista Đồng Thời",
                "Tranh chấp món cuối",
                List.of(
                        new CreateIssueSlipRequest.Item(
                                dalatMilk.getId(),
                                BigDecimal.ONE
                        )
                )
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < 2; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    MvcResult res = mockMvc.perform(authed(post("/api/v1/stock-transactions/issue").contentType(JSON)
                                    .content(objectMapper.writeValueAsString(request)), bmToken))
                            .andReturn();
                    int statusCode = res.getResponse().getStatus();
                    if (statusCode == 201) {
                        successCount.incrementAndGet();
                    } else if (statusCode == 422) {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(1);

        // Kho về 0, không bao giờ bị âm kho
        Inventory finalInv = inventoryRepository.findByBranchIdAndIngredientId(branch1.getId(), dalatMilk.getId()).orElseThrow();
        assertThat(finalInv.getCurrentQtyBase()).isEqualByComparingTo("0.0000");
    }

    @Test
    @DisplayName("TC-05 (UC29): Lỗi 404 khi nguyên liệu không tồn tại hoặc chưa tạo tồn kho chi nhánh")
    void issueStock_notFound() throws Exception {
        String bmToken = createAndLoginBm(branch1, "bm_uc29_tc05");

        // 1. Ingredient không tồn tại
        CreateIssueSlipRequest req1 = new CreateIssueSlipRequest(
                "BAR_DISPENSING",
                "Quầy Bar",
                "Barista",
                null,
                List.of(new CreateIssueSlipRequest.Item(999999L, BigDecimal.ONE))
        );
        mockMvc.perform(authed(post("/api/v1/stock-transactions/issue").contentType(JSON)
                        .content(objectMapper.writeValueAsString(req1)), bmToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INGREDIENT_NOT_FOUND"));

        // 2. Ingredient có nhưng chưa có bản ghi tồn kho ở chi nhánh
        Ingredient orphanIngredient = ingredientRepository.save(Ingredient.builder()
                .name("Nguyên liệu chưa nhập bao giờ " + System.currentTimeMillis())
                .baseUnit("g")
                .packUnit("Hộp")
                .unitsPerPack(BigDecimal.TEN)
                .unitCost(BigDecimal.TEN)
                .status("ACTIVE")
                .build());

        CreateIssueSlipRequest req2 = new CreateIssueSlipRequest(
                "BAR_DISPENSING",
                "Quầy Bar",
                "Barista",
                null,
                List.of(new CreateIssueSlipRequest.Item(orphanIngredient.getId(), BigDecimal.ONE))
        );
        mockMvc.perform(authed(post("/api/v1/stock-transactions/issue").contentType(JSON)
                        .content(objectMapper.writeValueAsString(req2)), bmToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVENTORY_NOT_FOUND"));
    }

    @Test
    @DisplayName("TC-06 (UC29): Validation 400 Bad Request khi thiếu trường bắt buộc hoặc số lượng <= 0")
    void issueStock_validationError() throws Exception {
        String bmToken = createAndLoginBm(branch1, "bm_uc29_tc06");

        // Số lượng kiện <= 0
        CreateIssueSlipRequest req1 = new CreateIssueSlipRequest(
                "BAR_DISPENSING",
                "Quầy Bar",
                "Barista",
                null,
                List.of(new CreateIssueSlipRequest.Item(dalatMilk.getId(), BigDecimal.ZERO))
        );
        mockMvc.perform(authed(post("/api/v1/stock-transactions/issue").contentType(JSON)
                        .content(objectMapper.writeValueAsString(req1)), bmToken))
                .andExpect(status().isBadRequest());

        // Thiếu tên barista
        CreateIssueSlipRequest req2 = new CreateIssueSlipRequest(
                "BAR_DISPENSING",
                "Quầy Bar",
                "",
                null,
                List.of(new CreateIssueSlipRequest.Item(dalatMilk.getId(), BigDecimal.ONE))
        );
        mockMvc.perform(authed(post("/api/v1/stock-transactions/issue").contentType(JSON)
                        .content(objectMapper.writeValueAsString(req2)), bmToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-07 (UC29): Phân quyền - CASHIER hoặc không có Token bị từ chối (403 hoặc 401)")
    void issueStock_unauthorizedOrForbidden() throws Exception {
        CreateIssueSlipRequest request = new CreateIssueSlipRequest(
                "BAR_DISPENSING",
                "Quầy Bar",
                "Barista",
                null,
                List.of(new CreateIssueSlipRequest.Item(dalatMilk.getId(), BigDecimal.ONE))
        );

        // 1. Không có Token -> 401 Unauthorized
        mockMvc.perform(post("/api/v1/stock-transactions/issue").contentType(JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        // 2. Đăng nhập CASHIER -> 403 Forbidden
        String cashierToken = createAndLoginCashier(branch1, "cashier_uc29");
        mockMvc.perform(authed(post("/api/v1/stock-transactions/issue").contentType(JSON)
                        .content(objectMapper.writeValueAsString(request)), cashierToken))
                .andExpect(status().isForbidden());
    }

    // ==============================================================
    // UC30 — HỦY NGUYÊN VẬT LIỆU HỎNG / HẾT HẠN (DISPOSE STOCK)
    // ==============================================================

    @Test
    @DisplayName("TC-01 (UC30): Hủy 2 thùng sữa (đủ tồn, lý do >= 10 ký tự, có ảnh) -> Trừ kho 24 hộp, tính đúng tổng thiệt hại 720,000 VND, sinh mã PHK-..., lưu evidence_image_url")
    void disposeStock_happyPath_success() throws Exception {
        String bmToken = createAndLoginBm(branch1, "bm_uc30_tc01");

        // Thiết lập tồn sữa: 120 hộp (= 10 thùng), an toàn 20 hộp
        Inventory milkInv = inventoryRepository.findByBranchIdAndIngredientId(branch1.getId(), dalatMilk.getId())
                .orElseGet(() -> inventoryRepository.save(Inventory.builder()
                        .branch(branch1)
                        .ingredient(dalatMilk)
                        .currentQtyBase(new BigDecimal("120.0000"))
                        .currentPackUnits(new BigDecimal("10.0000"))
                        .safetyStockLevel(new BigDecimal("20.0000"))
                        .lowStockFlag(false)
                        .lastUpdated(Instant.now())
                        .build()));
        milkInv.setCurrentQtyBase(new BigDecimal("120.0000"));
        milkInv.setSafetyStockLevel(new BigDecimal("20.0000"));
        milkInv.recalculateStockAndFlag(dalatMilk.getUnitsPerPack());
        inventoryRepository.save(milkInv);

        CreateDisposalSlipRequest request = new CreateDisposalSlipRequest(
                "COOLING_FAILURE",
                "Sữa tươi Dalat Milk lô 01/11 bị chua do hỏng lốc làm mát tủ bảo quản đêm 06/11",
                "uploads/evidence/20261107_spoiled_milk_lot01.jpg",
                List.of(
                        new CreateDisposalSlipRequest.Item(
                                dalatMilk.getId(),
                                new BigDecimal("2.0"),
                                "Hộp sữa bị phồng nắp, sữa chua đặc"
                        )
                )
        );

        mockMvc.perform(authed(post("/api/v1/stock-transactions/dispose").contentType(JSON)
                        .content(objectMapper.writeValueAsString(request)), bmToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.slipCode", containsString("PHK-")))
                .andExpect(jsonPath("$.data.reasonCategory").value("COOLING_FAILURE"))
                .andExpect(jsonPath("$.data.reason").value("Sữa tươi Dalat Milk lô 01/11 bị chua do hỏng lốc làm mát tủ bảo quản đêm 06/11"))
                .andExpect(jsonPath("$.data.evidenceFileId").value("uploads/evidence/20261107_spoiled_milk_lot01.jpg"))
                .andExpect(jsonPath("$.data.totalFinancialLoss").value(720000.0))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].ingredientId").value(dalatMilk.getId()))
                .andExpect(jsonPath("$.data.items[0].quantityPacks").value(2.0))
                .andExpect(jsonPath("$.data.items[0].quantityBaseDeducted").value(24.0))
                .andExpect(jsonPath("$.data.items[0].unitCost").value(360000.0))
                .andExpect(jsonPath("$.data.items[0].lineLoss").value(720000.0))
                .andExpect(jsonPath("$.data.items[0].remainingQtyBase").value(96.0))
                .andExpect(jsonPath("$.data.items[0].remainingPackUnits").value(8.0))
                .andExpect(jsonPath("$.data.items[0].lowStockFlag").value(false));

        // Kiểm tra database sau giao dịch
        Inventory updatedInv = inventoryRepository.findByBranchIdAndIngredientId(branch1.getId(), dalatMilk.getId()).orElseThrow();
        assertThat(updatedInv.getCurrentQtyBase()).isEqualByComparingTo("96.0000");
        assertThat(updatedInv.getCurrentPackUnits()).isEqualByComparingTo("8.0000");
        assertThat(updatedInv.getLowStockFlag()).isFalse();

        List<StockTransaction> txs = stockTransactionRepository.findByBranch_IdOrderByCreatedAtDesc(branch1.getId());
        StockTransaction latestTx = txs.stream().filter(t -> t.getType() == StockTransactionType.DISPOSAL).findFirst().orElseThrow();
        assertThat(latestTx.getQuantityPacks()).isEqualByComparingTo("2.0000");
        assertThat(latestTx.getQuantityBase()).isEqualByComparingTo("24.0000");
        assertThat(latestTx.getPurchasePrice()).isEqualByComparingTo("360000.00");
        assertThat(latestTx.getLineTotal()).isEqualByComparingTo("720000.00");
        assertThat(latestTx.getEvidenceImageUrl()).isEqualTo("uploads/evidence/20261107_spoiled_milk_lot01.jpg");
        assertThat(latestTx.getReason()).contains("PHK-").contains("[COOLING_FAILURE]").contains("Sữa tươi Dalat Milk");
    }

    @Test
    @DisplayName("TC-02 (UC30): Chặn lý do ngắn/rỗng BR-43 -> Nhập 'hỏng' (4 ký tự) ném HTTP 400 Bad Request")
    void disposeStock_shortReason_throws400_BR43() throws Exception {
        String bmToken = createAndLoginBm(branch1, "bm_uc30_tc02");

        // 1. Lý do quá ngắn (< 10 ký tự)
        CreateDisposalSlipRequest req1 = new CreateDisposalSlipRequest(
                "EXPIRED",
                "hỏng",
                null,
                List.of(new CreateDisposalSlipRequest.Item(dalatMilk.getId(), BigDecimal.ONE, null))
        );
        mockMvc.perform(authed(post("/api/v1/stock-transactions/dispose").contentType(JSON)
                        .content(objectMapper.writeValueAsString(req1)), bmToken))
                .andExpect(status().isBadRequest());

        // 2. Lý do rỗng hoặc khoảng trắng
        CreateDisposalSlipRequest req2 = new CreateDisposalSlipRequest(
                "EXPIRED",
                "          ",
                null,
                List.of(new CreateDisposalSlipRequest.Item(dalatMilk.getId(), BigDecimal.ONE, null))
        );
        mockMvc.perform(authed(post("/api/v1/stock-transactions/dispose").contentType(JSON)
                        .content(objectMapper.writeValueAsString(req2)), bmToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-03 (UC30): Chặn hủy vượt tồn BR-42 -> Tồn còn 10 hộp, xin hủy 1 thùng (12 hộp) bị ném 422 INSUFFICIENT_STOCK")
    void disposeStock_insufficientStock_throws422_BR42() throws Exception {
        String bmToken = createAndLoginBm(branch1, "bm_uc30_tc03");

        // Thiết lập tồn: 10 hộp sữa (< 12 hộp của 1 thùng)
        Inventory milkInv = inventoryRepository.findByBranchIdAndIngredientId(branch1.getId(), dalatMilk.getId())
                .orElseGet(() -> inventoryRepository.save(Inventory.builder()
                        .branch(branch1)
                        .ingredient(dalatMilk)
                        .currentQtyBase(new BigDecimal("10.0000"))
                        .currentPackUnits(new BigDecimal("0.8333"))
                        .safetyStockLevel(new BigDecimal("5.0000"))
                        .lowStockFlag(false)
                        .lastUpdated(Instant.now())
                        .build()));
        milkInv.setCurrentQtyBase(new BigDecimal("10.0000"));
        milkInv.recalculateStockAndFlag(dalatMilk.getUnitsPerPack());
        inventoryRepository.save(milkInv);

        CreateDisposalSlipRequest request = new CreateDisposalSlipRequest(
                "EXPIRED",
                "Hết hạn sử dụng không thể phục vụ khách hàng",
                null,
                List.of(new CreateDisposalSlipRequest.Item(dalatMilk.getId(), BigDecimal.ONE, "Quá date"))
        );

        mockMvc.perform(authed(post("/api/v1/stock-transactions/dispose").contentType(JSON)
                        .content(objectMapper.writeValueAsString(request)), bmToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.detailCode").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.message", containsString("Tồn kho nguyên liệu 'Sữa tươi thanh trùng Dalat Milk' không đủ để hủy")));

        // Tồn kho không đổi (Rollback)
        Inventory unchangedInv = inventoryRepository.findByBranchIdAndIngredientId(branch1.getId(), dalatMilk.getId()).orElseThrow();
        assertThat(unchangedInv.getCurrentQtyBase()).isEqualByComparingTo("10.0000");
    }

    @Test
    @DisplayName("TC-04 (UC30): Tự động bật cờ Low Stock BR-45 khi tồn sau hủy <= safety_stock_level")
    void disposeStock_triggersLowStockFlag_BR45() throws Exception {
        String bmToken = createAndLoginBm(branch1, "bm_uc30_tc04");

        // Thiết lập tồn: 30 hộp sữa, ngưỡng an toàn: 20 hộp. Đang an toàn (lowStockFlag = false)
        Inventory milkInv = inventoryRepository.findByBranchIdAndIngredientId(branch1.getId(), dalatMilk.getId())
                .orElseGet(() -> inventoryRepository.save(Inventory.builder()
                        .branch(branch1)
                        .ingredient(dalatMilk)
                        .currentQtyBase(new BigDecimal("30.0000"))
                        .currentPackUnits(new BigDecimal("2.5000"))
                        .safetyStockLevel(new BigDecimal("20.0000"))
                        .lowStockFlag(false)
                        .lastUpdated(Instant.now())
                        .build()));
        milkInv.setCurrentQtyBase(new BigDecimal("30.0000"));
        milkInv.setSafetyStockLevel(new BigDecimal("20.0000"));
        milkInv.recalculateStockAndFlag(dalatMilk.getUnitsPerPack());
        inventoryRepository.save(milkInv);
        assertThat(milkInv.getLowStockFlag()).isFalse();

        // Hủy 1 thùng (12 hộp) -> Còn 18 hộp <= 20 hộp ngưỡng an toàn
        CreateDisposalSlipRequest request = new CreateDisposalSlipRequest(
                "PACKAGING_DAMAGE",
                "Bao bì bị rách thủng do vận chuyển va đập mạnh",
                null,
                List.of(new CreateDisposalSlipRequest.Item(dalatMilk.getId(), BigDecimal.ONE, "Thùng sữa bị móp và vỡ hộp"))
        );

        mockMvc.perform(authed(post("/api/v1/stock-transactions/dispose").contentType(JSON)
                        .content(objectMapper.writeValueAsString(request)), bmToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.items[0].remainingQtyBase").value(18.0))
                .andExpect(jsonPath("$.data.items[0].lowStockFlag").value(true));

        Inventory updatedInv = inventoryRepository.findByBranchIdAndIngredientId(branch1.getId(), dalatMilk.getId()).orElseThrow();
        assertThat(updatedInv.getCurrentQtyBase()).isEqualByComparingTo("18.0000");
        assertThat(updatedInv.getLowStockFlag()).isTrue();
    }

    @Test
    @DisplayName("TC-05 (UC30): Lỗi 404 khi nguyên liệu không tồn tại hoặc chưa tạo tồn kho chi nhánh")
    void disposeStock_notFound() throws Exception {
        String bmToken = createAndLoginBm(branch1, "bm_uc30_tc05");

        // 1. Ingredient không tồn tại
        CreateDisposalSlipRequest req1 = new CreateDisposalSlipRequest(
                "DEGRADATION",
                "Nguyên liệu suy giảm chất lượng không thể dùng",
                null,
                List.of(new CreateDisposalSlipRequest.Item(888888L, BigDecimal.ONE, null))
        );
        mockMvc.perform(authed(post("/api/v1/stock-transactions/dispose").contentType(JSON)
                        .content(objectMapper.writeValueAsString(req1)), bmToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INGREDIENT_NOT_FOUND"));

        // 2. Ingredient có nhưng chưa có bản ghi tồn kho ở chi nhánh
        Ingredient orphanIngredient = ingredientRepository.save(Ingredient.builder()
                .name("Nguyên liệu hủy chưa nhập " + System.currentTimeMillis())
                .baseUnit("g")
                .packUnit("Hộp")
                .unitsPerPack(BigDecimal.TEN)
                .unitCost(BigDecimal.TEN)
                .status("ACTIVE")
                .build());

        CreateDisposalSlipRequest req2 = new CreateDisposalSlipRequest(
                "DEGRADATION",
                "Nguyên liệu suy giảm chất lượng không thể dùng",
                null,
                List.of(new CreateDisposalSlipRequest.Item(orphanIngredient.getId(), BigDecimal.ONE, null))
        );
        mockMvc.perform(authed(post("/api/v1/stock-transactions/dispose").contentType(JSON)
                        .content(objectMapper.writeValueAsString(req2)), bmToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVENTORY_NOT_FOUND"));
    }

    @Test
    @DisplayName("TC-06 (UC30): Phân quyền - CASHIER hoặc không có Token bị từ chối (403 hoặc 401)")
    void disposeStock_unauthorizedOrForbidden() throws Exception {
        CreateDisposalSlipRequest request = new CreateDisposalSlipRequest(
                "EXPIRED",
                "Quá hạn sử dụng tiêu chuẩn quán cà phê",
                null,
                List.of(new CreateDisposalSlipRequest.Item(dalatMilk.getId(), BigDecimal.ONE, null))
        );

        // 1. Không có Token -> 401 Unauthorized
        mockMvc.perform(post("/api/v1/stock-transactions/dispose").contentType(JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        // 2. Đăng nhập CASHIER -> 403 Forbidden
        String cashierToken = createAndLoginCashier(branch1, "cashier_uc30");
        mockMvc.perform(authed(post("/api/v1/stock-transactions/dispose").contentType(JSON)
                        .content(objectMapper.writeValueAsString(request)), cashierToken))
                .andExpect(status().isForbidden());
    }
}
