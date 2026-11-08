package edu.fpt.sba.scms.controller;

import edu.fpt.sba.scms.common.enums.BranchStatus;
import edu.fpt.sba.scms.common.enums.Roles;
import edu.fpt.sba.scms.common.enums.StockTransactionType;
import edu.fpt.sba.scms.common.enums.StocktakeStatus;
import edu.fpt.sba.scms.common.enums.UserStatus;
import edu.fpt.sba.scms.dto.request.CreateStocktakeSessionRequest;
import edu.fpt.sba.scms.dto.request.SubmitStocktakeCountsRequest;
import edu.fpt.sba.scms.model.Branch;
import edu.fpt.sba.scms.model.Ingredient;
import edu.fpt.sba.scms.model.Inventory;
import edu.fpt.sba.scms.model.Role;
import edu.fpt.sba.scms.model.StockTransaction;
import edu.fpt.sba.scms.model.Stocktake;
import edu.fpt.sba.scms.model.User;
import edu.fpt.sba.scms.repository.BranchRepository;
import edu.fpt.sba.scms.repository.IngredientRepository;
import edu.fpt.sba.scms.repository.InventoryRepository;
import edu.fpt.sba.scms.repository.RoleRepository;
import edu.fpt.sba.scms.repository.StockTransactionRepository;
import edu.fpt.sba.scms.repository.StocktakeRepository;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StocktakeIT {

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
    private StocktakeRepository stocktakeRepository;

    @Autowired
    private edu.fpt.sba.scms.repository.StocktakeLineRepository stocktakeLineRepository;

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

    @BeforeEach
    void setupData() {
        stocktakeRepository.deleteAll();

        branch1 = branchRepository.findByCode("BR_STK").orElseGet(() -> branchRepository.save(
                Branch.builder()
                        .code("BR_STK")
                        .name("Chi nhánh Kiểm kê Kho")
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

        Inventory inv1 = inventoryRepository.findByBranchIdAndIngredientId(branch1.getId(), robustaCoffee.getId())
                .orElseGet(() -> inventoryRepository.save(Inventory.builder()
                        .branch(branch1)
                        .ingredient(robustaCoffee)
                        .currentQtyBase(new BigDecimal("32000.0000"))
                        .currentPackUnits(new BigDecimal("6.4000"))
                        .safetyStockLevel(new BigDecimal("10000.0000"))
                        .lowStockFlag(false)
                        .lastUpdated(Instant.now())
                        .build()));
        inv1.setCurrentQtyBase(new BigDecimal("32000.0000"));
        inv1.recalculateStockAndFlag(robustaCoffee.getUnitsPerPack());
        inventoryRepository.save(inv1);

        Inventory inv2 = inventoryRepository.findByBranchIdAndIngredientId(branch1.getId(), dalatMilk.getId())
                .orElseGet(() -> inventoryRepository.save(Inventory.builder()
                        .branch(branch1)
                        .ingredient(dalatMilk)
                        .currentQtyBase(new BigDecimal("98.0000"))
                        .currentPackUnits(new BigDecimal("8.1700"))
                        .safetyStockLevel(new BigDecimal("20.0000"))
                        .lowStockFlag(false)
                        .lastUpdated(Instant.now())
                        .build()));
        inv2.setCurrentQtyBase(new BigDecimal("98.0000"));
        inv2.recalculateStockAndFlag(dalatMilk.getUnitsPerPack());
        inventoryRepository.save(inv2);
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

    // ==========================================
    // UC31 — MỞ ĐỢT KIỂM KÊ (STOCKTAKE SESSION)
    // ==========================================

    @Test
    @DisplayName("TC-01 (UC31): Mở đợt kiểm kê thành công -> Trạng thái OPEN, snapshot book_quantity, mã PKK-YYYYMMDD-XXX")
    void createStocktakeSession_happyPath_success() throws Exception {
        String bmToken = createAndLoginBm(branch1, "bm_uc31_tc01");

        CreateStocktakeSessionRequest request = new CreateStocktakeSessionRequest(
                "DAILY_CLOSING",
                "Nguyễn Văn Pha Chế (Trưởng ca)",
                "Kiểm kê định kỳ chốt sổ ca tối ngày 07/11"
        );

        mockMvc.perform(authed(post("/api/v1/stocktake/session").contentType(JSON)
                        .content(objectMapper.writeValueAsString(request)), bmToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.slipCode", containsString("PKK-")))
                .andExpect(jsonPath("$.data.branchId").value(branch1.getId()))
                .andExpect(jsonPath("$.data.roundType").value("DAILY_CLOSING"))
                .andExpect(jsonPath("$.data.witnessName").value("Nguyễn Văn Pha Chế (Trưởng ca)"))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.totalItems").value(2))
                .andExpect(jsonPath("$.data.lines", hasSize(2)))
                .andExpect(jsonPath("$.data.lines[0].bookQuantity").isNotEmpty())
                .andExpect(jsonPath("$.data.lines[0].countedQuantity", is(nullValue())))
                .andExpect(jsonPath("$.data.lines[0].variance", is(nullValue())));

        // Kiểm tra trong database
        List<Stocktake> stocktakes = stocktakeRepository.findAll();
        assertThat(stocktakes).hasSize(1);
        Stocktake saved = stocktakes.get(0);
        assertThat(saved.getStatus()).isEqualTo(StocktakeStatus.OPEN);
        assertThat(stocktakeLineRepository.findByStocktake_Id(saved.getId())).hasSize(2);
    }

    @Test
    @DisplayName("TC-02 (UC31): Chặn mở đợt kép BR-44 -> Đang có đợt OPEN ném 409 Conflict ACTIVE_STOCKTAKE_EXISTS")
    void createStocktakeSession_duplicateOpen_returns409() throws Exception {
        String bmToken = createAndLoginBm(branch1, "bm_uc31_tc02");

        CreateStocktakeSessionRequest request = new CreateStocktakeSessionRequest(
                "DAILY_CLOSING",
                "Người làm chứng 1",
                "Đợt 1"
        );

        // Lần 1: Thành công
        mockMvc.perform(authed(post("/api/v1/stocktake/session").contentType(JSON)
                        .content(objectMapper.writeValueAsString(request)), bmToken))
                .andExpect(status().isCreated());

        // Lần 2: Ném lỗi 409 Conflict
        mockMvc.perform(authed(post("/api/v1/stocktake/session").contentType(JSON)
                        .content(objectMapper.writeValueAsString(request)), bmToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTIVE_STOCKTAKE_EXISTS"))
                .andExpect(jsonPath("$.message", containsString("đang có một phiên kiểm kê đang mở")));
    }

    @Test
    @DisplayName("TC-03 (UC31): Lấy thông tin đợt kiểm kê đang mở (GET /api/v1/stocktake/active)")
    void getActiveStocktakeSession_success() throws Exception {
        String bmToken = createAndLoginBm(branch1, "bm_uc31_tc03");

        // Khi chưa có đợt kiểm kê -> Trả về data: null
        mockMvc.perform(authed(get("/api/v1/stocktake/active"), bmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", is(nullValue())));

        // Mở đợt kiểm kê
        CreateStocktakeSessionRequest request = new CreateStocktakeSessionRequest(
                "SPOT_AUDIT",
                "Trần Giám Sát",
                "Kiểm kê đột xuất"
        );
        mockMvc.perform(authed(post("/api/v1/stocktake/session").contentType(JSON)
                        .content(objectMapper.writeValueAsString(request)), bmToken))
                .andExpect(status().isCreated());

        // Lấy lại đợt active -> Trả về phiên đang OPEN
        mockMvc.perform(authed(get("/api/v1/stocktake/active"), bmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.totalItems").value(2));
    }

    @Test
    @DisplayName("TC-04 (UC31): Phân quyền - CASHIER bị từ chối 403 Forbidden")
    void createStocktakeSession_cashierForbidden() throws Exception {
        String cashierToken = createAndLoginCashier(branch1, "cashier_uc31");

        CreateStocktakeSessionRequest request = new CreateStocktakeSessionRequest(
                "DAILY_CLOSING",
                "Nhân viên",
                "Thử mở đợt"
        );

        mockMvc.perform(authed(post("/api/v1/stocktake/session").contentType(JSON)
                        .content(objectMapper.writeValueAsString(request)), cashierToken))
                .andExpect(status().isForbidden());
    }

    // ==========================================
    // UC32 — CÂN ĐỐI TỒN KHO (ADJUST INVENTORY)
    // ==========================================

    @Test
    @DisplayName("TC-01 (UC32): Cân đối thành công -> Cập nhật tồn kho thật, variance != 0 sinh StockTransaction ADJUST, trạng thái ADJUSTED")
    void adjustInventory_happyPath_success() throws Exception {
        String bmToken = createAndLoginBm(branch1, "bm_uc32_tc01");

        // 1. Mở đợt kiểm kê
        MvcResult openResult = mockMvc.perform(authed(post("/api/v1/stocktake/session").contentType(JSON)
                        .content(objectMapper.writeValueAsString(new CreateStocktakeSessionRequest(
                                "DAILY_CLOSING", "Barista A", "Chốt ca"))), bmToken))
                .andExpect(status().isCreated())
                .andReturn();

        long stocktakeId = objectMapper.readTree(openResult.getResponse().getContentAsString())
                .path("data").path("stocktakeId").asLong();

        // 2. Nhập số đếm:
        // Cà phê robusta: Sổ sách 32,000g -> Đếm thực tế 30,500g (Lệch thiếu 1,500g)
        // Sữa tươi: Sổ sách 98 hộp -> Đếm thực tế 98 hộp (Khớp 100%)
        SubmitStocktakeCountsRequest countsRequest = new SubmitStocktakeCountsRequest(List.of(
                new SubmitStocktakeCountsRequest.CountItem(
                        robustaCoffee.getId(),
                        new BigDecimal("30500.0"),
                        "Hao hụt 1.5kg do rơi vãi khi xay"
                ),
                new SubmitStocktakeCountsRequest.CountItem(
                        dalatMilk.getId(),
                        new BigDecimal("98.0"),
                        "Khớp 100%"
                )
        ));

        mockMvc.perform(authed(post("/api/v1/stocktake/" + stocktakeId + "/adjust").contentType(JSON)
                        .content(objectMapper.writeValueAsString(countsRequest)), bmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.stocktakeId").value(stocktakeId))
                .andExpect(jsonPath("$.data.status").value("ADJUSTED"))
                .andExpect(jsonPath("$.data.totalItems").value(2))
                .andExpect(jsonPath("$.data.matchedCount").value(1))
                .andExpect(jsonPath("$.data.discrepancyCount").value(1))
                .andExpect(jsonPath("$.data.netVarianceValue").value(-225000.0))
                .andExpect(jsonPath("$.data.adjustments", hasSize(2)));

        // 3. Kiểm tra tồn kho sau cân đối: Sổ kho bị ghi đè về đúng số đếm thực tế
        Inventory updatedCoffeeInv = inventoryRepository.findByBranchIdAndIngredientId(branch1.getId(), robustaCoffee.getId()).orElseThrow();
        assertThat(updatedCoffeeInv.getCurrentQtyBase()).isEqualByComparingTo("30500.0000");

        Inventory updatedMilkInv = inventoryRepository.findByBranchIdAndIngredientId(branch1.getId(), dalatMilk.getId()).orElseThrow();
        assertThat(updatedMilkInv.getCurrentQtyBase()).isEqualByComparingTo("98.0000");

        // 4. Kiểm tra StockTransaction sinh ra: Chỉ món lệch (cà phê) mới sinh bản ghi type = ADJUST
        List<StockTransaction> adjustTxs = stockTransactionRepository.findAll().stream()
                .filter(t -> t.getType() == StockTransactionType.ADJUST && stocktakeId == (t.getStocktakeId() != null ? t.getStocktakeId() : 0))
                .toList();

        assertThat(adjustTxs).hasSize(1);
        StockTransaction coffeeTx = adjustTxs.get(0);
        assertThat(coffeeTx.getIngredient().getId()).isEqualTo(robustaCoffee.getId());
        assertThat(coffeeTx.getQuantityBase()).isEqualByComparingTo("-1500.0000");
        assertThat(coffeeTx.getReason()).contains("Cân đối kiểm kê").contains("-1500");

        // 5. Kiểm tra trạng thái đợt kiểm kê
        Stocktake finalStocktake = stocktakeRepository.findById(stocktakeId).orElseThrow();
        assertThat(finalStocktake.getStatus()).isEqualTo(StocktakeStatus.ADJUSTED);
        assertThat(finalStocktake.getClosedAt()).isNotNull();
    }

    @Test
    @DisplayName("TC-02 (UC32): Chống sửa đổi 2 lần AF-01 -> Cân đối đợt đã đóng ném 409 Conflict STOCKTAKE_ALREADY_CLOSED")
    void adjustInventory_alreadyClosed_returns409() throws Exception {
        String bmToken = createAndLoginBm(branch1, "bm_uc32_tc02");

        // Mở đợt
        MvcResult openResult = mockMvc.perform(authed(post("/api/v1/stocktake/session").contentType(JSON)
                        .content(objectMapper.writeValueAsString(new CreateStocktakeSessionRequest(
                                "DAILY_CLOSING", "Barista B", "Chốt ca"))), bmToken))
                .andExpect(status().isCreated())
                .andReturn();

        long stocktakeId = objectMapper.readTree(openResult.getResponse().getContentAsString())
                .path("data").path("stocktakeId").asLong();

        SubmitStocktakeCountsRequest countsRequest = new SubmitStocktakeCountsRequest(List.of(
                new SubmitStocktakeCountsRequest.CountItem(robustaCoffee.getId(), new BigDecimal("32000.0"), "Khớp"),
                new SubmitStocktakeCountsRequest.CountItem(dalatMilk.getId(), new BigDecimal("98.0"), "Khớp")
        ));

        // Cân đối lần 1: Thành công
        mockMvc.perform(authed(post("/api/v1/stocktake/" + stocktakeId + "/adjust").contentType(JSON)
                        .content(objectMapper.writeValueAsString(countsRequest)), bmToken))
                .andExpect(status().isOk());

        // Cân đối lần 2: Ném lỗi 409 Conflict
        mockMvc.perform(authed(post("/api/v1/stocktake/" + stocktakeId + "/adjust").contentType(JSON)
                        .content(objectMapper.writeValueAsString(countsRequest)), bmToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STOCKTAKE_ALREADY_CLOSED"))
                .andExpect(jsonPath("$.message", containsString("đã được cân đối hoặc đóng trước đó")));
    }

    @Test
    @DisplayName("TC-03 (UC32): Thiếu mặt hàng chưa đếm AF-03 -> Ném 400 Bad Request")
    void adjustInventory_missingCountItem_returns400() throws Exception {
        String bmToken = createAndLoginBm(branch1, "bm_uc32_tc03");

        // Mở đợt (có 2 mặt hàng)
        MvcResult openResult = mockMvc.perform(authed(post("/api/v1/stocktake/session").contentType(JSON)
                        .content(objectMapper.writeValueAsString(new CreateStocktakeSessionRequest(
                                "DAILY_CLOSING", "Barista C", "Chốt ca"))), bmToken))
                .andExpect(status().isCreated())
                .andReturn();

        long stocktakeId = objectMapper.readTree(openResult.getResponse().getContentAsString())
                .path("data").path("stocktakeId").asLong();

        // Gửi thiếu mặt hàng Dalat Milk
        SubmitStocktakeCountsRequest partialRequest = new SubmitStocktakeCountsRequest(List.of(
                new SubmitStocktakeCountsRequest.CountItem(robustaCoffee.getId(), new BigDecimal("32000.0"), "Khớp")
        ));

        mockMvc.perform(authed(post("/api/v1/stocktake/" + stocktakeId + "/adjust").contentType(JSON)
                        .content(objectMapper.writeValueAsString(partialRequest)), bmToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Vui lòng nhập đủ số lượng đếm của toàn bộ danh mục kiểm kê")));
    }

    @Test
    @DisplayName("TC-04 (UC32): Số lượng đếm âm AF-02 -> Ném 400 Bad Request")
    void adjustInventory_negativeCount_returns400() throws Exception {
        String bmToken = createAndLoginBm(branch1, "bm_uc32_tc04");

        // Mở đợt
        MvcResult openResult = mockMvc.perform(authed(post("/api/v1/stocktake/session").contentType(JSON)
                        .content(objectMapper.writeValueAsString(new CreateStocktakeSessionRequest(
                                "DAILY_CLOSING", "Barista D", "Chốt ca"))), bmToken))
                .andExpect(status().isCreated())
                .andReturn();

        long stocktakeId = objectMapper.readTree(openResult.getResponse().getContentAsString())
                .path("data").path("stocktakeId").asLong();

        // Số lượng âm
        SubmitStocktakeCountsRequest negRequest = new SubmitStocktakeCountsRequest(List.of(
                new SubmitStocktakeCountsRequest.CountItem(robustaCoffee.getId(), new BigDecimal("-100.0"), "Lỗi"),
                new SubmitStocktakeCountsRequest.CountItem(dalatMilk.getId(), new BigDecimal("98.0"), "Khớp")
        ));

        mockMvc.perform(authed(post("/api/v1/stocktake/" + stocktakeId + "/adjust").contentType(JSON)
                        .content(objectMapper.writeValueAsString(negRequest)), bmToken))
                .andExpect(status().isBadRequest());
    }
}
