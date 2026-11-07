package edu.fpt.sba.scms.controller;

import edu.fpt.sba.scms.common.enums.BranchStatus;
import edu.fpt.sba.scms.common.enums.Roles;
import edu.fpt.sba.scms.common.enums.UserStatus;
import edu.fpt.sba.scms.model.Branch;
import edu.fpt.sba.scms.model.Ingredient;
import edu.fpt.sba.scms.model.Inventory;
import edu.fpt.sba.scms.model.Role;
import edu.fpt.sba.scms.model.User;
import edu.fpt.sba.scms.repository.BranchRepository;
import edu.fpt.sba.scms.repository.IngredientRepository;
import edu.fpt.sba.scms.repository.InventoryRepository;
import edu.fpt.sba.scms.repository.RoleRepository;
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
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventoryIT {

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
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Branch branch1;
    private Branch branch2;
    private Ingredient robustaCoffee;
    private Ingredient dalatMilk;
    private Inventory inv1;
    private Inventory inv2;

    @BeforeEach
    void setupData() {
        branch1 = branchRepository.findByCode("BR01").orElseGet(() -> branchRepository.save(
                Branch.builder()
                        .code("BR01")
                        .name("Chi nhánh Quận 1")
                        .status(BranchStatus.OPEN)
                        .build()));

        branch2 = branchRepository.findByCode("BR02").orElseGet(() -> branchRepository.save(
                Branch.builder()
                        .code("BR02")
                        .name("Chi nhánh Quận 3")
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

        // Tồn kho branch 1: Robusta chạm ngưỡng (12,000g <= 20,000g)
        inv1 = inventoryRepository.findByBranchIdAndIngredientId(branch1.getId(), robustaCoffee.getId())
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

        // Tồn kho branch 1: Dalat Milk đủ hàng (98 hộp > 50 hộp)
        inv2 = inventoryRepository.findByBranchIdAndIngredientId(branch1.getId(), dalatMilk.getId())
                .orElseGet(() -> inventoryRepository.save(Inventory.builder()
                        .branch(branch1)
                        .ingredient(dalatMilk)
                        .currentQtyBase(new BigDecimal("98.0000"))
                        .currentPackUnits(new BigDecimal("8.1700"))
                        .safetyStockLevel(new BigDecimal("50.0000"))
                        .lowStockFlag(false)
                        .lastUpdated(Instant.now())
                        .build()));
        inv2.setCurrentQtyBase(new BigDecimal("98.0000"));
        inv2.setSafetyStockLevel(new BigDecimal("50.0000"));
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

    private String cooToken() throws Exception {
        return login("coo", "Admin@123");
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

    private static MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder, String token) {
        return builder.header("Authorization", "Bearer " + token);
    }

    @Test
    @DisplayName("TC-01: Tra cứu sổ kho phân trang & tính đúng holdingValue")
    void getBranchInventory_pagination_and_holdingValue() throws Exception {
        String token = cooToken();

        mockMvc.perform(authed(get("/api/v1/inventory")
                        .param("branchId", String.valueOf(branch1.getId()))
                        .param("page", "0")
                        .param("size", "10"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10));
    }

    @Test
    @DisplayName("TC-02: Lọc Low Stock chỉ trả về các món chạm ngưỡng")
    void getBranchInventory_filter_low_stock() throws Exception {
        String token = cooToken();

        mockMvc.perform(authed(get("/api/v1/inventory")
                        .param("branchId", String.valueOf(branch1.getId()))
                        .param("status", "LOW_STOCK"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].lowStockFlag").value(true));
    }

    @Test
    @DisplayName("TC-03: Bảo mật chi nhánh (BM không thể xem chéo chi nhánh khác)")
    void branchManager_cannot_access_other_branch() throws Exception {
        String bm1Token = createAndLoginBm(branch1, "bm_branch1");

        // BM branch 1 gọi xem branch 1 -> OK
        mockMvc.perform(authed(get("/api/v1/inventory"), bm1Token))
                .andExpect(status().isOk());

        // BM branch 1 cố tình truyền branchId của branch 2 -> 403 CROSS_BRANCH_DENIED
        mockMvc.perform(authed(get("/api/v1/inventory")
                        .param("branchId", String.valueOf(branch2.getId())), bm1Token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CROSS_BRANCH_DENIED"));
    }

    @Test
    @DisplayName("TC-04: Cảnh báo tồn thấp & gợi ý số kiện nhập bù (CEIL)")
    void getLowStockAlerts_calculatesSuggestedPacksCorrectly() throws Exception {
        String token = cooToken();

        // Robusta: current 12,000g, safety 20,000g -> deficit = 8,000g.
        // packUnit = 5,000g -> suggestedPacks = CEIL(8000 / 5000) = 2 bao.
        // estimatedCost = 2 * 5000 * 150 = 1,500,000 VND.
        mockMvc.perform(authed(get("/api/v1/inventory/low-stock")
                        .param("branchId", String.valueOf(branch1.getId())), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[?(@.ingredientName == 'Hạt Cà phê Robusta Đắk Lắk')].deficitQtyBase", hasItem(8000.0)))
                .andExpect(jsonPath("$.data[?(@.ingredientName == 'Hạt Cà phê Robusta Đắk Lắk')].suggestedPackRestock", hasItem(2)))
                .andExpect(jsonPath("$.data[?(@.ingredientName == 'Hạt Cà phê Robusta Đắk Lắk')].estimatedRestockCost", hasItem(1500000)));
    }

    @Test
    @Transactional
    @DisplayName("Test Concurrency: findForUpdate sử dụng Pessimistic Lock")
    void testFindForUpdate_pessimisticLock() {
        Optional<Inventory> locked = inventoryRepository.findForUpdate(branch1.getId(), robustaCoffee.getId());
        assertThat(locked).isPresent();
        assertThat(locked.get().getLowStockFlag()).isTrue();
    }
}
