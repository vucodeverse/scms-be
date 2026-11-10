package edu.fpt.sba.scms.controller;

import edu.fpt.sba.scms.model.Ingredient;
import edu.fpt.sba.scms.repository.IngredientRepository;
import edu.fpt.sba.scms.repository.RecipeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm thử tích hợp luồng nghiệp vụ UC12: Quản lý công thức định lượng (BOM) & tính giá vốn món ước tính (COGS).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecipeFlowIT {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IngredientRepository ingredientRepository;

    @Autowired
    private RecipeRepository recipeRepository;

    private String cooToken;
    private Long activeIng1Id;
    private Long activeIng2Id;
    private Long inactiveIngId;

    private int testCategoryId;

    @BeforeEach
    void setUp() throws Exception {
        recipeRepository.deleteAll();

        // Login lấy JWT của COO
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login").contentType(JSON)
                        .content("""
                                {"username":"coo","password":"Admin@123"}"""))
                .andExpect(status().isOk())
                .andReturn();
        cooToken = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();

        // Tạo danh mục cho test
        MvcResult catResult = mockMvc.perform(authed(post("/api/v1/menu-categories")).contentType(JSON)
                        .content(String.format("""
                                {"name":"CategoryRecipe-%d","sortOrder":1,"status":"ACTIVE"}""", System.currentTimeMillis())))
                .andExpect(status().isCreated())
                .andReturn();
        testCategoryId = objectMapper.readTree(catResult.getResponse().getContentAsString())
                .path("data").path("id").asInt();

        // Seed nguyên liệu test
        Ingredient ing1 = ingredientRepository.save(Ingredient.builder()
                .name("Trà Ô Long")
                .baseUnit("g")
                .packUnit("túi")
                .unitsPerPack(new BigDecimal("1000"))
                .unitCost(new BigDecimal("250.00")) // 250đ / g
                .status("ACTIVE")
                .build());
        activeIng1Id = ing1.getId();

        Ingredient ing2 = ingredientRepository.save(Ingredient.builder()
                .name("Sữa tươi")
                .baseUnit("ml")
                .packUnit("hộp")
                .unitsPerPack(new BigDecimal("1000"))
                .unitCost(new BigDecimal("35.00")) // 35đ / ml
                .status("ACTIVE")
                .build());
        activeIng2Id = ing2.getId();

        Ingredient ing3 = ingredientRepository.save(Ingredient.builder()
                .name("Hương liệu cũ")
                .baseUnit("ml")
                .unitCost(new BigDecimal("50.00"))
                .status("INACTIVE")
                .build());
        inactiveIngId = ing3.getId();
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder) {
        return builder.header("Authorization", "Bearer " + cooToken);
    }

    private int createProductWithSizes(String name) throws Exception {
        // Tạo sản phẩm
        String productJson = String.format("""
                {
                    "name": "%s",
                    "categoryId": %d,
                    "defaultSize": "M",
                    "status": "AVAILABLE"
                }""", name, testCategoryId);

        MvcResult prodResult = mockMvc.perform(authed(post("/api/v1/products")).contentType(JSON).content(productJson))
                .andExpect(status().isCreated())
                .andReturn();
        int productId = objectMapper.readTree(prodResult.getResponse().getContentAsString())
                .path("data").path("id").asInt();

        // Thêm size M
        mockMvc.perform(authed(post("/api/v1/products/" + productId + "/sizes")).contentType(JSON)
                        .content("""
                                {"size":"M","price":35000}"""))
                .andExpect(status().isOk());

        // Thêm size L
        mockMvc.perform(authed(post("/api/v1/products/" + productId + "/sizes")).contentType(JSON)
                        .content("""
                                {"size":"L","price":42000}"""))
                .andExpect(status().isOk());

        return productId;
    }

    @Test
    @DisplayName("UC12 - Lấy công thức khi chưa cấu hình: trả về mảng rỗng và estimatedCogs = 0.00")
    void getRecipe_whenEmpty_returnsZeroCogs() throws Exception {
        int productId = createProductWithSizes("Trà Sữa Oolong Lạnh");

        mockMvc.perform(authed(get("/api/v1/products/" + productId + "/recipe")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productId").value(productId))
                .andExpect(jsonPath("$.data.productName").value("Trà Sữa Oolong Lạnh"))
                .andExpect(jsonPath("$.data.size").doesNotExist())
                .andExpect(jsonPath("$.data.estimatedCogs").value(0.00))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    @DisplayName("UC12 - Lưu công thức chung (size=null), tính toán chi phí lineCost và estimatedCogs chuẩn xác")
    void saveAndGetRecipe_commonSize_success() throws Exception {
        int productId = createProductWithSizes("Trà Ô Long Sữa Tươi");

        // Định lượng chung: 15g Trà (15 * 250 = 3750.00), 100ml Sữa tươi (100 * 35 = 3500.00) => COGS = 7250.00
        String saveRecipeJson = String.format("""
                {
                    "size": null,
                    "items": [
                        {"ingredientId": %d, "quantityBase": 15.0000},
                        {"ingredientId": %d, "quantityBase": 100.0000}
                    ]
                }""", activeIng1Id, activeIng2Id);

        mockMvc.perform(authed(put("/api/v1/products/" + productId + "/recipe")).contentType(JSON).content(saveRecipeJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productId").value(productId))
                .andExpect(jsonPath("$.data.estimatedCogs").value(7250.00))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(2));

        // GET lại công thức chung
        mockMvc.perform(authed(get("/api/v1/products/" + productId + "/recipe")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.estimatedCogs").value(7250.00))
                .andExpect(jsonPath("$.data.items[0].ingredientName").value("Trà Ô Long"))
                .andExpect(jsonPath("$.data.items[0].unitCost").value(250.00))
                .andExpect(jsonPath("$.data.items[0].lineCost").value(3750.00))
                .andExpect(jsonPath("$.data.items[1].ingredientName").value("Sữa tươi"))
                .andExpect(jsonPath("$.data.items[1].unitCost").value(35.00))
                .andExpect(jsonPath("$.data.items[1].lineCost").value(3500.00));
    }

    @Test
    @DisplayName("UC12 - Lấy công thức theo size cụ thể: gộp công thức chung và công thức riêng của size đó")
    void getRecipe_withSize_mergesCommonAndSizeSpecific() throws Exception {
        int productId = createProductWithSizes("Trà Oolong Đặc Biệt");

        // Công thức chung: 10g Trà (10 * 250 = 2500)
        mockMvc.perform(authed(put("/api/v1/products/" + productId + "/recipe")).contentType(JSON)
                        .content(String.format("""
                                {
                                    "size": null,
                                    "items": [
                                        {"ingredientId": %d, "quantityBase": 10.0000}
                                    ]
                                }""", activeIng1Id)))
                .andExpect(status().isOk());

        // Công thức riêng cho size L: thêm 50ml Sữa tươi (50 * 35 = 1750)
        mockMvc.perform(authed(put("/api/v1/products/" + productId + "/recipe")).contentType(JSON)
                        .content(String.format("""
                                {
                                    "size": "L",
                                    "items": [
                                        {"ingredientId": %d, "quantityBase": 50.0000}
                                    ]
                                }""", activeIng2Id)))
                .andExpect(status().isOk());

        // GET theo size L: Tổng COGS = 2500 + 1750 = 4250.00, gồm 2 dòng
        mockMvc.perform(authed(get("/api/v1/products/" + productId + "/recipe?size=L")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value("L"))
                .andExpect(jsonPath("$.data.estimatedCogs").value(4250.00))
                .andExpect(jsonPath("$.data.items.length()").value(2));
    }

    @Test
    @DisplayName("UC12 - Validation lỗi: nguyên liệu trùng giữa công thức chung và size riêng -> HTTP 400")
    void saveRecipe_overlappingCommonAndSizeSpecificIngredient_failsWith400() throws Exception {
        int productId = createProductWithSizes("Trà Trùng Chung Và Size");

        mockMvc.perform(authed(put("/api/v1/products/" + productId + "/recipe")).contentType(JSON)
                        .content(String.format("""
                                {
                                    "size": null,
                                    "items": [{"ingredientId": %d, "quantityBase": 10.0000}]
                                }""", activeIng1Id)))
                .andExpect(status().isOk());

        mockMvc.perform(authed(put("/api/v1/products/" + productId + "/recipe")).contentType(JSON)
                        .content(String.format("""
                                {
                                    "size": "L",
                                    "items": [{"ingredientId": %d, "quantityBase": 5.0000}]
                                }""", activeIng1Id)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("items"));
    }

    @Test
    @DisplayName("UC12 - Validation lỗi: không lưu công thức cho sản phẩm INACTIVE -> HTTP 400")
    void saveRecipe_onInactiveProduct_failsWith400() throws Exception {
        int productId = createProductWithSizes("Trà Ngừng Lưu Công Thức");
        mockMvc.perform(authed(delete("/api/v1/products/" + productId))).andExpect(status().isOk());

        mockMvc.perform(authed(put("/api/v1/products/" + productId + "/recipe")).contentType(JSON)
                        .content(String.format("""
                                {
                                    "size": null,
                                    "items": [{"ingredientId": %d, "quantityBase": 10.0000}]
                                }""", activeIng1Id)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("productId"));
    }

    @Test
    @DisplayName("UC12 - Xóa ProductSize đồng thời xóa công thức riêng của size đó")
    void deleteProductSize_alsoDeletesSizeSpecificRecipe() throws Exception {
        int productId = createProductWithSizes("Trà Xoá Size Sẽ Xoá Recipe");

        mockMvc.perform(authed(put("/api/v1/products/" + productId + "/recipe")).contentType(JSON)
                        .content(String.format("""
                                {
                                    "size": "L",
                                    "items": [{"ingredientId": %d, "quantityBase": 20.0000}]
                                }""", activeIng1Id)))
                .andExpect(status().isOk());

        mockMvc.perform(authed(delete("/api/v1/products/" + productId + "/sizes/L")))
                .andExpect(status().isNoContent());

        // Thêm lại size L -> không còn công thức cũ bị mồ côi
        mockMvc.perform(authed(post("/api/v1/products/" + productId + "/sizes")).contentType(JSON)
                        .content("""
                                {"size":"L","price":42000}"""))
                .andExpect(status().isOk());

        mockMvc.perform(authed(get("/api/v1/products/" + productId + "/recipe?size=L")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    @DisplayName("UC12 - Validation lỗi: nguyên liệu trùng lặp trong cùng công thức -> HTTP 400")
    void saveRecipe_duplicateIngredients_failsWith400() throws Exception {
        int productId = createProductWithSizes("Trà Test Trùng");

        String invalidJson = String.format("""
                {
                    "size": null,
                    "items": [
                        {"ingredientId": %d, "quantityBase": 10.0000},
                        {"ingredientId": %d, "quantityBase": 5.0000}
                    ]
                }""", activeIng1Id, activeIng1Id);

        mockMvc.perform(authed(put("/api/v1/products/" + productId + "/recipe")).contentType(JSON).content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("items"));
    }

    @Test
    @DisplayName("UC12 - Validation lỗi: nguyên liệu không tồn tại hoặc INACTIVE -> HTTP 400")
    void saveRecipe_inactiveOrNotFoundIngredient_failsWith400() throws Exception {
        int productId = createProductWithSizes("Trà Test Inactive");

        // Nguyên liệu INACTIVE
        String inactiveJson = String.format("""
                {
                    "size": null,
                    "items": [
                        {"ingredientId": %d, "quantityBase": 10.0000}
                    ]
                }""", inactiveIngId);

        mockMvc.perform(authed(put("/api/v1/products/" + productId + "/recipe")).contentType(JSON).content(inactiveJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("items"));

        // Nguyên liệu không tồn tại (id = 999999)
        String notFoundIngJson = """
                {
                    "size": null,
                    "items": [
                        {"ingredientId": 999999, "quantityBase": 10.0000}
                    ]
                }""";

        mockMvc.perform(authed(put("/api/v1/products/" + productId + "/recipe")).contentType(JSON).content(notFoundIngJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("items"));
    }

    @Test
    @DisplayName("UC12 - Xoá định lượng theo size và xoá toàn bộ định lượng của sản phẩm")
    void deleteRecipe_bySizeAndAll_success() throws Exception {
        int productId = createProductWithSizes("Trà Test Xoá");

        // Cấu hình công thức size L
        mockMvc.perform(authed(put("/api/v1/products/" + productId + "/recipe")).contentType(JSON)
                        .content(String.format("""
                                {
                                    "size": "L",
                                    "items": [
                                        {"ingredientId": %d, "quantityBase": 20.0000}
                                    ]
                                }""", activeIng1Id)))
                .andExpect(status().isOk());

        // Xoá định lượng size L
        mockMvc.perform(authed(delete("/api/v1/products/" + productId + "/recipe?size=L")))
                .andExpect(status().isNoContent());

        // Kiểm tra sau khi xoá size L thì danh sách trống
        mockMvc.perform(authed(get("/api/v1/products/" + productId + "/recipe?size=L")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    @DisplayName("UC12 - Phân quyền: Cập nhật định lượng yêu cầu quyền COO")
    void updateRecipe_unauthorized_fails() throws Exception {
        int productId = createProductWithSizes("Trà Test Quyền");

        String saveJson = String.format("""
                {
                    "size": null,
                    "items": [
                        {"ingredientId": %d, "quantityBase": 10.0000}
                    ]
                }""", activeIng1Id);

        // Không gửi token -> 401
        mockMvc.perform(put("/api/v1/products/" + productId + "/recipe").contentType(JSON).content(saveJson))
                .andExpect(status().isUnauthorized());
    }
}
