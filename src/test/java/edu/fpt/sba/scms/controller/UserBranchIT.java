package edu.fpt.sba.scms.controller;

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

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC02 / UC05 — CRUD, view detail, phân trang và branch scope của {@code /users} và {@code /branches}
 * (IMPLEMENTATION.md §10.2, §10.2.1, §10.3).
 * <p>
 * Dùng H2 in-memory (profile test). Mỗi test tạo chi nhánh/user với mã riêng để dữ liệu dùng chung một DB.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.test.context.TestPropertySource(properties = "app.kiosk.pin-pepper=test-only-pin-pepper-0123456789abcdef")
class UserBranchIT {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final AtomicInteger SEQ = new AtomicInteger(1);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ---------------- helpers ----------------

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

    private static MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder, String token) {
        return builder.header("Authorization", "Bearer " + token);
    }

    private static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    private String uniqueSuffix() {
        return String.valueOf(SEQ.getAndIncrement());
    }

    /** Code chi nhánh [A-Z0-9-] tối đa 10 ký tự, duy nhất giữa các lần chạy test. */
    private String uniqueCode() {
        return "B" + System.nanoTime() % 100_000_000L;
    }

    /** 7 chữ số duy nhất để SĐT đúng regex ^0d{9,10}$ (không vượt quá 11 ký tự). */
    private static String uniquePhones() {
        return String.format("%07d", SEQ.getAndIncrement());
    }

    private int createBranch(String token, String code) throws Exception {
        MvcResult result = mockMvc.perform(authed(post("/api/v1/branches"), token).contentType(JSON)
                        .content(branchJson(code)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value(code))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andReturn();
        return objectMapper.readTree(body(result)).path("data").path("id").asInt();
    }

    private static String branchJson(String code) {
        return """
                {"code":"%s","name":"Chi nhánh %s","address":"1 Nguyen Trai","phone":"0901234567",
                 "email":"branch@scms.vn","openingTime":"07:00","closingTime":"22:00","tableCount":12,
                 "status":"OPEN"}""".formatted(code, code);
    }

    private static String staffJson(int branchId, String phone, String pin) {
        return """
                {"fullName":"Nhan vien %s","phone":"%s","position":"BARISTA","branchId":%d,
                 "pin":"%s","status":"ACTIVE"}""".formatted(phone, phone, branchId, pin);
    }

    private int createStaff(String token, int branchId, String phone, String pin) throws Exception {
        MvcResult result = mockMvc.perform(authed(post("/api/v1/users"), token).contentType(JSON)
                        .content(staffJson(branchId, phone, pin)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.hasPin").value(true))
                .andExpect(jsonPath("$.data.roleCode").value("BARISTA"))
                .andReturn();
        return objectMapper.readTree(body(result)).path("data").path("id").asInt();
    }

    private static String managerJson(int branchId, String username) {
        return """
                {"fullName":"Quan ly %s","phone":"0912223334","email":"%s@scms.vn","position":"BRANCH_MANAGER",
                 "branchId":%d,"username":"%s","password":"Manager@123","status":"ACTIVE"}
                """.formatted(username, username, branchId, username);
    }

    private int createManager(String token, int branchId, String username) throws Exception {
        MvcResult result = mockMvc.perform(authed(post("/api/v1/users"), token).contentType(JSON)
                        .content(managerJson(branchId, username)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.username").value(username))

                .andReturn();
        return objectMapper.readTree(body(result)).path("data").path("id").asInt();
    }

    // ---------------- branches ----------------

    @Test
    void branch_crud_detail_and_pagination_defaults() throws Exception {
        String token = cooToken();
        String code = uniqueCode();
        int id = createBranch(token, code);

        mockMvc.perform(authed(get("/api/v1/branches/{id}", id), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value(code))
                .andExpect(jsonPath("$.data.openingTime").value("07:00:00"))
                .andExpect(jsonPath("$.data.employees").isArray())
                .andExpect(jsonPath("$.data.posOnline").isEmpty());

        mockMvc.perform(authed(put("/api/v1/branches/{id}", id), token).contentType(JSON)
                        .content(branchJson(code).replace("Chi nhánh " + code, "Chi nhánh đổi tên")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Chi nhánh đổi tên"));

        mockMvc.perform(authed(get("/api/v1/branches"), token).param("keyword", code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.content[*].code", hasItem(code)));
    }

    @Test
    void branch_duplicate_code_returns_409() throws Exception {
        String token = cooToken();
        String code = uniqueCode();
        createBranch(token, code);

        mockMvc.perform(authed(post("/api/v1/branches"), token).contentType(JSON)
                        .content(branchJson(code)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("DUPLICATE_CODE"));
    }

    @Test
    void branch_closing_time_not_after_opening_returns_400() throws Exception {
        mockMvc.perform(authed(post("/api/v1/branches"), cooToken()).contentType(JSON)
                        .content(branchJson(uniqueCode())
                                .replace("\"openingTime\":\"07:00\"", "\"openingTime\":\"22:00\"")
                                .replace("\"closingTime\":\"22:00\"", "\"closingTime\":\"07:00\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("closingTime")));
    }

    @Test
    void branch_with_active_staff_cannot_be_closed_or_deleted() throws Exception {
        String token = cooToken();
        int id = createBranch(token, uniqueCode());
        createStaff(token, id, "0933" + uniquePhones(), "1234");

        mockMvc.perform(authed(patch("/api/v1/branches/{id}/status", id), token).contentType(JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detailCode").value("BRANCH_NOT_EMPTY"));

        mockMvc.perform(authed(delete("/api/v1/branches/{id}", id), token))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detailCode").value("BRANCH_NOT_EMPTY"));
    }

    @Test
    void empty_branch_can_be_soft_deleted() throws Exception {
        String token = cooToken();
        int id = createBranch(token, uniqueCode());

        mockMvc.perform(authed(delete("/api/v1/branches/{id}", id), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));
    }

    // ---------------- users ----------------

    @Test
    void user_crud_detail_and_pagination() throws Exception {
        String token = cooToken();
        int branchId = createBranch(token, uniqueCode());
        String phone = "0901" + uniquePhones();
        int id = createStaff(token, branchId, phone, "4321");

        mockMvc.perform(authed(get("/api/v1/users/{id}", id), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phone").value(phone))
                .andExpect(jsonPath("$.data.branchId").value(branchId))
                .andExpect(jsonPath("$.data.hasPin").value(true))
                .andExpect(jsonPath("$.data.pinHash").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        mockMvc.perform(authed(put("/api/v1/users/{id}", id), token).contentType(JSON)
                        .content(staffJson(branchId, phone, "4321")
                                .replace("Nhan vien " + phone, "Nhan vien doi ten")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Nhan vien doi ten"));

        mockMvc.perform(authed(get("/api/v1/users"), token).param("keyword", phone))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.content[*].phone", hasItem(phone)))
                .andExpect(jsonPath("$.data.content[*].roleCode", hasItem("BARISTA")));
    }

    @Test
    void staff_cannot_have_login_account() throws Exception {
        String token = cooToken();
        int branchId = createBranch(token, uniqueCode());

        mockMvc.perform(authed(post("/api/v1/users"), token).contentType(JSON)
                        .content("""
                                {"fullName":"Sai","phone":"0900000111","position":"BARISTA","branchId":%d,
                                 "pin":"1111","username":"barista1","status":"ACTIVE"}"""
                                .formatted(branchId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("username")));
    }

    @Test
    void duplicate_pin_in_same_branch_returns_409() throws Exception {
        String token = cooToken();
        int branchId = createBranch(token, uniqueCode());
        createStaff(token, branchId, "0900777001", "5555");

        mockMvc.perform(authed(post("/api/v1/users"), token).contentType(JSON)
                        .content(staffJson(branchId, "0900777002", "5555")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_PIN"));
    }

    @Test
    void duplicate_username_returns_409() throws Exception {
        String token = cooToken();
        int branchId = createBranch(token, uniqueCode());
        String username = "mgr" + uniqueSuffix();
        createManager(token, branchId, username);

        mockMvc.perform(authed(post("/api/v1/users"), token).contentType(JSON)
                        .content(managerJson(branchId, username)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_USERNAME"));
    }

    @Test
    void branch_manager_only_sees_and_edits_own_branch() throws Exception {
        String coo = cooToken();
        int branchA = createBranch(coo, uniqueCode());
        int branchB = createBranch(coo, uniqueCode());
        String username = "bm" + uniqueSuffix();
        createManager(coo, branchA, username);
        String bmToken = login(username, "Manager@123");

        String phoneA = "0901777001";
        String phoneB = "0901777002";
        int staffA = createStaff(coo, branchA, phoneA, "0001");
        int staffB = createStaff(coo, branchB, phoneB, "0002");

        mockMvc.perform(authed(get("/api/v1/users"), bmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].branchId", hasItem(branchA)))
                .andExpect(jsonPath("$.data.content[*].branchId", org.hamcrest.Matchers.not(hasItem(branchB))));

        mockMvc.perform(authed(get("/api/v1/users/{id}", staffB), bmToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CROSS_BRANCH_DENIED"));

        mockMvc.perform(authed(get("/api/v1/users/{id}", staffA), bmToken))
                .andExpect(status().isOk());

        // BM không được xem chi nhánh khác qua query param
        mockMvc.perform(authed(get("/api/v1/users"), bmToken).param("branchId", String.valueOf(branchB)))
                .andExpect(status().isForbidden());

        // BM không được tạo tài khoản quản lý chi nhánh
        mockMvc.perform(authed(post("/api/v1/users"), bmToken).contentType(JSON)
                        .content(managerJson(branchA, "notallowed" + uniqueSuffix())))
                .andExpect(status().isForbidden());

        // BM không truy cập được danh sách chi nhánh (COO only)
        mockMvc.perform(authed(get("/api/v1/branches"), bmToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void self_lockout_is_rejected() throws Exception {
        String coo = cooToken();
        int branchId = createBranch(coo, uniqueCode());
        String username = "self" + uniqueSuffix();
        int managerId = createManager(coo, branchId, username);
        String bmToken = login(username, "Manager@123");

        mockMvc.perform(authed(patch("/api/v1/users/{id}/status", managerId), bmToken).contentType(JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detailCode").value("SELF_ACTION_FORBIDDEN"));
    }

    @Test
    void roles_endpoint_lists_active_user_counts() throws Exception {
        mockMvc.perform(authed(get("/api/v1/users/roles"), cooToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].code", hasItem("COO")))
                .andExpect(jsonPath("$.data[*].code", hasItem("BRANCH_MANAGER")));
    }

    @Test
    void user_and_branch_endpoints_require_authentication() throws Exception {
        mockMvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/branches")).andExpect(status().isUnauthorized());
    }

    @Test
    void reset_password_revokes_sessions_and_old_password_stops_working() throws Exception {
        String coo = cooToken();
        int branchId = createBranch(coo, uniqueCode());
        String username = "pw" + uniqueSuffix();
        int managerId = createManager(coo, branchId, username);
        login(username, "Manager@123");

        mockMvc.perform(authed(put("/api/v1/users/{id}/password", managerId), coo).contentType(JSON)
                        .content("{\"password\":\"NewPass123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").exists());

        mockMvc.perform(post("/api/v1/auth/login").contentType(JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"Manager@123\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(login(username, "NewPass123")).isNotBlank();
    }
}
