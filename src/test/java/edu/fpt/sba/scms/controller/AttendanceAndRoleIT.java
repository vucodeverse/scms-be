package edu.fpt.sba.scms.controller;

import edu.fpt.sba.scms.repository.AttendanceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AttendanceAndRoleIT {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final AtomicInteger SEQ = new AtomicInteger(100);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AttendanceRepository attendanceRepository;

    private String cooToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login").contentType(JSON)
                        .content("{\"username\":\"coo\",\"password\":\"Admin@123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("accessToken")
                .asText();
    }

    private int createBranch(String token, String code) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/branches")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content("""
                                {"code":"%s","name":"Chi nhánh %s","address":"1 Nguyen Trai","phone":"0901234567",
                                 "email":"branch@scms.vn","openingTime":"07:00","closingTime":"22:00","tableCount":12,
                                 "status":"OPEN"}""".formatted(code, code)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("id").asInt();
    }

    private int createStaff(String token, int branchId, String phone, String pin) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content("""
                                {"fullName":"Nhan vien %s","phone":"%s","position":"BARISTA","branchId":%d,
                                 "pin":"%s","status":"ACTIVE"}""".formatted(phone, phone, branchId, pin)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("id").asInt();
    }

    // =========================================================================
    // UC02: Manage Roles & Permissions
    // =========================================================================

    @Test
    void uc02_list_roles_and_permissions() throws Exception {
        String token = cooToken();

        // 1. Xem danh sách vai trò
        mockMvc.perform(get("/api/v1/roles")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[*].code", hasItem("COO")))
                .andExpect(jsonPath("$.data[*].code", hasItem("CASHIER")));

        // 2. Lấy chi tiết ma trận quyền của một role
        MvcResult listRes = mockMvc.perform(get("/api/v1/roles")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(listRes.getResponse().getContentAsString()).path("data");
        int roleId = data.get(0).path("roleId").asInt();

        mockMvc.perform(get("/api/v1/roles/" + roleId + "/permissions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roleId").value(roleId))
                .andExpect(jsonPath("$.data.allAvailablePermissions").isArray());

        // 3. Cập nhật ma trận quyền cho role
        mockMvc.perform(put("/api/v1/roles/" + roleId + "/permissions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content("{\"permissions\":[\"BRANCH_MANAGE\",\"ORDER_VIEW\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assignedPermissions", hasItem("BRANCH_MANAGE")))
                .andExpect(jsonPath("$.data.assignedPermissions", hasItem("ORDER_VIEW")));
    }

    // =========================================================================
    // UC03: Check In & UC04: Check Out
    // =========================================================================

    @Test
    void uc03_uc04_checkin_checkout_flow() throws Exception {
        String token = cooToken();
        int seq = SEQ.incrementAndGet();
        String branchCode = "BR-" + seq;
        int branchId = createBranch(token, branchCode);
        String phone = "0988" + String.format("%06d", seq);
        String pin = "6789";
        int employeeId = createStaff(token, branchId, phone, pin);

        // 1. Kiểm tra danh sách Kiosk employees ban đầu -> isCheckedIn = false
        mockMvc.perform(get("/api/v1/attendance/kiosk/employees?branchId=" + branchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].employeeId").value(employeeId))
                .andExpect(jsonPath("$.data[0].isCheckedIn").value(false));

        // 2. Check-in với PIN sai -> 401 INVALID_PIN
        mockMvc.perform(post("/api/v1/attendance/check-in")
                        .contentType(JSON)
                        .content("{\"employeeId\":" + employeeId + ",\"pin\":\"0000\",\"branchId\":" + branchId + "}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_PIN"));

        // 3. Check-in với PIN đúng -> 201 CREATED
        mockMvc.perform(post("/api/v1/attendance/check-in")
                        .contentType(JSON)
                        .content("{\"employeeId\":" + employeeId + ",\"pin\":\"" + pin + "\",\"branchId\":" + branchId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.employeeId").value(employeeId))
                .andExpect(jsonPath("$.data.status").value("CHECKED_IN"));

        // 4. Check-in lần nữa khi chưa check-out -> 422 ATTENDANCE_ALREADY_CHECKED_IN
        mockMvc.perform(post("/api/v1/attendance/check-in")
                        .contentType(JSON)
                        .content("{\"employeeId\":" + employeeId + ",\"pin\":\"" + pin + "\",\"branchId\":" + branchId + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detailCode").value("ATTENDANCE_ALREADY_CHECKED_IN"));

        // 5. Kiểm tra danh sách Kiosk -> isCheckedIn = true
        mockMvc.perform(get("/api/v1/attendance/kiosk/employees?branchId=" + branchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].isCheckedIn").value(true));

        // 6. Kiểm tra trạng thái cá nhân
        mockMvc.perform(get("/api/v1/attendance/status/" + employeeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CHECKED_IN"));

        // 6b. Check-out ngay khi chưa đủ 15 phút -> 422 ATTENDANCE_MIN_INTERVAL (BR-07)
        mockMvc.perform(post("/api/v1/attendance/check-out")
                        .contentType(JSON)
                        .content("{\"employeeId\":" + employeeId + ",\"pin\":\"" + pin + "\",\"branchId\":" + branchId + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detailCode").value("ATTENDANCE_MIN_INTERVAL"));

        // Giả lập thời gian đã trôi qua 20 phút để thực hiện check-out hợp lệ
        attendanceRepository.findAllByUserIdOrderByCheckInTimeDesc(employeeId).forEach(att -> {
            att.setCheckInTime(Instant.now().minus(Duration.ofMinutes(20)));
            attendanceRepository.save(att);
        });

        // 7. Check-out với PIN đúng (UC04) -> 200 OK
        mockMvc.perform(post("/api/v1/attendance/check-out")
                        .contentType(JSON)
                        .content("{\"employeeId\":" + employeeId + ",\"pin\":\"" + pin + "\",\"branchId\":" + branchId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.employeeId").value(employeeId))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        // 8. Check-out lần 2 khi không còn ca mở -> 422 ATTENDANCE_NO_OPEN
        mockMvc.perform(post("/api/v1/attendance/check-out")
                        .contentType(JSON)
                        .content("{\"employeeId\":" + employeeId + ",\"pin\":\"" + pin + "\",\"branchId\":" + branchId + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detailCode").value("ATTENDANCE_NO_OPEN"));

        // 9. Danh sách Kiosk quay lại isCheckedIn = false
        mockMvc.perform(get("/api/v1/attendance/kiosk/employees?branchId=" + branchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].isCheckedIn").value(false));
    }
}
