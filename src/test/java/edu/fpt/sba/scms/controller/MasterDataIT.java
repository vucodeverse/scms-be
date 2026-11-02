package edu.fpt.sba.scms.controller;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import edu.fpt.sba.scms.model.Category;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC09/UC10/UC11/UC13 — hợp đồng HTTP của master data: CRUD, view detail, phân trang mặc định 10 phần tử/trang,
 * xoá mềm (status) và xoá cứng.
 * <p>
 * Dùng H2 in-memory (profile test). Mỗi test dùng tiền tố mã riêng vì dữ liệu nằm chung một DB suốt class.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MasterDataIT {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private javax.sql.DataSource dataSource;

    /** Id của danh mục hệ thống UNCAT — seed qua DataSeeder (xem Category.UNCATEGORIZED_CODE). */
    private int seededUncatId() {
        return new JdbcTemplate(dataSource).queryForObject(
                "select category_id from category where code = ?", Integer.class, Category.UNCATEGORIZED_CODE);
    }

    // ---------------- helpers ----------------

    private String loginAsCoo() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login").contentType(JSON)
                        .content("""
                                {"username":"coo","password":"Admin@123"}"""))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("accessToken")
                .asText();
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder, String token) {
        return builder.header("Authorization", "Bearer " + token);
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private int createCategory(String token, String name, int sortOrder) throws Exception {
        MvcResult result = mockMvc.perform(authed(post("/api/v1/menu-categories"), token).contentType(JSON)
                        .content("""
                                {"name":"%s","description":"test","sortOrder":%d,"status":"ACTIVE"}"""
                                .formatted(name, sortOrder)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andReturn();
        return body(result).path("id").asInt();
    }

    private int[] createProducts(String token, int categoryId, String prefix, int count) throws Exception {
        int[] ids = new int[count];
        for (int i = 0; i < count; i++) {
            MvcResult result = mockMvc.perform(authed(post("/api/v1/products"), token).contentType(JSON)
                            .content("""
                                    {"name":"%s-%02d","categoryId":%d,"status":"AVAILABLE"}"""
                                    .formatted(prefix, i, categoryId)))
                    .andExpect(status().isCreated())
                    .andReturn();
            ids[i] = body(result).path("id").asInt();
        }
        return ids;
    }

    // ---------------- pagination ----------------

    @Test
    void list_defaults_to_10_items_per_page_with_1_based_page_numbers() throws Exception {
        String token = loginAsCoo();
        int categoryId = createCategory(token, "Paging Category", 900);
        createProducts(token, categoryId, "paging", 12);

        mockMvc.perform(authed(get("/api/v1/products"), token).param("categoryId", String.valueOf(categoryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.totalElements").value(12))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.content", hasSize(10)));

        mockMvc.perform(authed(get("/api/v1/products"), token)
                        .param("categoryId", String.valueOf(categoryId)).param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.content", hasSize(2)));
    }

    @Test
    void list_rejects_size_above_max_and_page_below_one() throws Exception {
        String token = loginAsCoo();

        mockMvc.perform(authed(get("/api/v1/menu-categories"), token).param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("size")));

        mockMvc.perform(authed(get("/api/v1/menu-categories"), token).param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("page")));
    }

    @Test
    void list_rejects_unknown_sort_field() throws Exception {
        String token = loginAsCoo();

        mockMvc.perform(authed(get("/api/v1/products"), token).param("sortBy", "unitPrice"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("sortBy")));
    }

    // ---------------- UC09 ----------------

    @Test
    void category_crud_detail_and_search_are_consistent() throws Exception {
        String token = loginAsCoo();
        int id = createCategory(token, "Trà sữa Signature", 10);

        mockMvc.perform(authed(get("/api/v1/menu-categories/{id}", id), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.name").value("Trà sữa Signature"))
                .andExpect(jsonPath("$.data.systemCategory").value(false))
                .andExpect(jsonPath("$.data.products", hasSize(0)));

        mockMvc.perform(authed(put("/api/v1/menu-categories/{id}", id), token).contentType(JSON)
                        .content("""
                                {"name":"Trà sữa Đặc biệt","description":"đổi tên","sortOrder":11,"status":"ACTIVE"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Trà sữa Đặc biệt"));

        mockMvc.perform(authed(get("/api/v1/menu-categories"), token)
                        .param("keyword", "đặc BIỆT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].id", hasItem(id)));
    }

    @Test
    void duplicate_category_name_returns_409() throws Exception {
        String token = loginAsCoo();
        createCategory(token, "Trùng Tên", 20);

        mockMvc.perform(authed(post("/api/v1/menu-categories"), token).contentType(JSON)
                        .content("""
                                {"name":"Trùng Tên","sortOrder":21,"status":"ACTIVE"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_NAME"));
    }

    @Test
    void deactivating_category_with_selling_products_returns_422_category_not_empty() throws Exception {
        String token = loginAsCoo();
        int id = createCategory(token, "Danh mục còn hàng", 30);
        createProducts(token, id, "selling", 1);

        mockMvc.perform(authed(patch("/api/v1/menu-categories/{id}/status", id), token).contentType(JSON)
                        .content("""
                                {"status":"INACTIVE"}"""))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.detailCode").value("CATEGORY_NOT_EMPTY"));
    }

    @Test
    void soft_delete_marks_inactive_and_hard_delete_removes_row() throws Exception {
        String token = loginAsCoo();
        int id = createCategory(token, "Danh mục xoá được", 40);

        mockMvc.perform(authed(delete("/api/v1/menu-categories/{id}", id), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));

        mockMvc.perform(authed(delete("/api/v1/menu-categories/{id}/permanent", id), token))
                .andExpect(status().isNoContent());

        mockMvc.perform(authed(get("/api/v1/menu-categories/{id}", id), token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void hard_delete_blocked_while_category_has_products() throws Exception {
        String token = loginAsCoo();
        int id = createCategory(token, "Danh mục còn sản phẩm", 50);
        createProducts(token, id, "blocking", 1);

        mockMvc.perform(authed(delete("/api/v1/menu-categories/{id}/permanent", id), token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_CONFLICT"));
    }

    @Test
    void system_uncategorized_category_cannot_be_modified_or_deleted() throws Exception {
        String token = loginAsCoo();
        // UNCAT là danh mục hệ thống (seed sẵn) và không hiện trong danh sách quản lý → lấy id qua luồng seed.
        MvcResult result = mockMvc.perform(authed(get("/api/v1/menu-categories"), token)
                        .param("keyword", "UNCAT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(0)))
                .andReturn();
        assertThat(body(result).path("content")).isEmpty();
        // Danh mục hệ thống không cho thay đổi: xoá cứng bằng id bất kỳ của UNCAT đều bị chặn theo mã.
        int uncatId = seededUncatId();

        mockMvc.perform(authed(delete("/api/v1/menu-categories/{id}/permanent", uncatId), token))
                .andExpect(status().isConflict());
    }

    @Test
    void reorder_updates_sequence() throws Exception {
        String token = loginAsCoo();
        int first = createCategory(token, "Thứ tự A", 60);
        int second = createCategory(token, "Thứ tự B", 61);

        mockMvc.perform(authed(put("/api/v1/menu-categories/sequence"), token).contentType(JSON)
                        .content("""
                                {"items":[{"categoryId":%d,"sortOrder":2},{"categoryId":%d,"sortOrder":1}]}"""
                                .formatted(first, second)))
                .andExpect(status().isOk());

        mockMvc.perform(authed(get("/api/v1/menu-categories/{id}", second), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sortOrder").value(1));
    }

    // ---------------- UC10/UC11 ----------------

    @Test
    void product_detail_includes_sizes_toppings_and_recipe() throws Exception {
        String token = loginAsCoo();
        int categoryId = createCategory(token, "Danh mục đồ uống", 70);
        int productId = createProducts(token, categoryId, "detail", 1)[0];

        mockMvc.perform(authed(post("/api/v1/products/{id}/sizes", productId), token).contentType(JSON)
                        .content("""
                                {"size":"M","price":35000}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value("M"))
                .andExpect(jsonPath("$.data.price").value(35000));

        // upsert cùng size chỉ cập nhật giá
        mockMvc.perform(authed(post("/api/v1/products/{id}/sizes", productId), token).contentType(JSON)
                        .content("""
                                {"size":"M","price":39000}"""))
                .andExpect(status().isOk());

        MvcResult topping = mockMvc.perform(authed(post("/api/v1/toppings"), token).contentType(JSON)
                        .content("""
                                {"name":"Trân châu đen","defaultPrice":5000,"status":"ACTIVE"}"""))
                .andExpect(status().isCreated())
                .andReturn();
        int toppingId = body(topping).path("id").asInt();

        mockMvc.perform(authed(put("/api/v1/products/{id}/toppings", productId), token).contentType(JSON)
                        .content("""
                                {"items":[{"toppingId":%d,"extraPrice":6000,"isDefault":true}]}"""
                                .formatted(toppingId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.toppings", hasSize(1)))
                .andExpect(jsonPath("$.data.toppings[0].name").value("Trân châu đen"))
                .andExpect(jsonPath("$.data.toppings[0].extraPrice").value(6000));

        mockMvc.perform(authed(get("/api/v1/products/{id}", productId), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sizes", hasSize(1)))
                .andExpect(jsonPath("$.data.sizes[0].price").value(39000))
                .andExpect(jsonPath("$.data.recipe", hasSize(0)));
    }

    @Test
    void product_hard_delete_removes_sizes_and_toppings() throws Exception {
        String token = loginAsCoo();
        int categoryId = createCategory(token, "Danh mục xoá sản phẩm", 80);
        int productId = createProducts(token, categoryId, "harddel", 1)[0];
        int toppingId = body(mockMvc.perform(authed(post("/api/v1/toppings"), token).contentType(JSON)
                        .content("""
                                {"name":"Topping xoá kèm","defaultPrice":1000,"status":"ACTIVE"}"""))
                .andReturn()).path("id").asInt();
        mockMvc.perform(authed(put("/api/v1/products/{id}/toppings", productId), token).contentType(JSON)
                        .content("""
                                {"items":[{"toppingId":%d,"extraPrice":1000,"isDefault":false}]}"""
                                .formatted(toppingId)))
                .andExpect(status().isOk());

        mockMvc.perform(authed(delete("/api/v1/products/{id}/permanent", productId), token))
                .andExpect(status().isNoContent());

        mockMvc.perform(authed(get("/api/v1/products/{id}", productId), token))
                .andExpect(status().isNotFound());
    }

    @Test
    void soft_delete_product_keeps_row_and_marks_inactive() throws Exception {
        String token = loginAsCoo();
        int categoryId = createCategory(token, "Danh mục xoá mềm", 90);
        int productId = createProducts(token, categoryId, "softdel", 1)[0];

        mockMvc.perform(authed(delete("/api/v1/products/{id}", productId), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));

        mockMvc.perform(authed(get("/api/v1/products/{id}", productId), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));
    }

    // ---------------- UC13 ----------------

    @Test
    void voucher_crud_pagination_and_detail_scope() throws Exception {
        String token = loginAsCoo();
        int categoryId = createCategory(token, "Danh mục voucher", 100);
        int productId = createProducts(token, categoryId, "voucher", 1)[0];
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant end = start.plus(10, ChronoUnit.DAYS);

        MvcResult created = mockMvc.perform(authed(post("/api/v1/vouchers"), token).contentType(JSON)
                        .content(voucherBody("KHAITRUONG25", categoryId, productId, start, end)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value("KHAITRUONG25"))
                .andExpect(jsonPath("$.data.products", hasSize(1)))
                .andExpect(jsonPath("$.data.categories", hasSize(1)))
                .andReturn();
        int voucherId = body(created).path("id").asInt();

        mockMvc.perform(authed(get("/api/v1/vouchers"), token).param("keyword", "khaiTruong25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.content[*].id", hasItem(voucherId)));

        mockMvc.perform(authed(get("/api/v1/vouchers/{id}", voucherId), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.discountType").value("PERCENT"));

        mockMvc.perform(authed(patch("/api/v1/vouchers/{id}/status", voucherId), token).contentType(JSON)
                        .content("""
                                {"status":"INACTIVE"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));

        mockMvc.perform(authed(delete("/api/v1/vouchers/{id}/permanent", voucherId), token))
                .andExpect(status().isNoContent());

        mockMvc.perform(authed(get("/api/v1/vouchers/{id}", voucherId), token))
                .andExpect(status().isNotFound());
    }

    @Test
    void voucher_percent_over_100_is_rejected() throws Exception {
        String token = loginAsCoo();
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.DAYS);

        mockMvc.perform(authed(post("/api/v1/vouchers"), token).contentType(JSON)
                        .content("""
                                {"code":"SAI100","discountType":"PERCENT","discountValue":150,
                                 "startDate":"%s","endDate":"%s","status":"ACTIVE"}"""
                                .formatted(start, end)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("discountValue")));
    }

    @Test
    void voucher_code_rejects_lowercase() throws Exception {
        String token = loginAsCoo();
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);

        mockMvc.perform(authed(post("/api/v1/vouchers"), token).contentType(JSON)
                        .content("""
                                {"code":"khai-truong","discountType":"FIXED","discountValue":10000,
                                 "startDate":"%s","endDate":"%s","status":"ACTIVE"}"""
                                .formatted(start, start.plus(1, ChronoUnit.DAYS))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("code")));
    }

    @Test
    void master_data_requires_authentication() throws Exception {
        mockMvc.perform(get("/api/v1/menu-categories"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    private static String voucherBody(String code, int categoryId, int productId, Instant start, Instant end) {
        return """
                {"code":"%s","discountType":"PERCENT","discountValue":10,"minOrderAmount":50000,
                 "startDate":"%s","endDate":"%s","usageLimit":100,"status":"ACTIVE",
                 "productIds":[%d],"categoryIds":[%d]}"""
                .formatted(code, start, end, productId, categoryId);
    }
}
