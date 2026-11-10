package edu.fpt.sba.scms.controller;

import edu.fpt.sba.scms.common.enums.Roles;
import edu.fpt.sba.scms.common.enums.UserStatus;
import edu.fpt.sba.scms.common.util.DateTimeUtil;
import edu.fpt.sba.scms.model.Role;
import edu.fpt.sba.scms.model.Shift;
import edu.fpt.sba.scms.model.User;
import edu.fpt.sba.scms.repository.AttendanceRepository;
import edu.fpt.sba.scms.repository.RoleRepository;
import edu.fpt.sba.scms.repository.ShiftRepository;
import edu.fpt.sba.scms.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

    @Autowired
    private ShiftRepository shiftRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String cooToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login").contentType(JSON)
                        .content("{\"username\":\"coo\",\"password\":\"Admin@123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("accessToken")
                .asText();
    }

    private int createBranch(String token, String code) throws Exception {
        String phone = "091" + String.format("%07d", SEQ.incrementAndGet());
        String email = "branch-" + code.toLowerCase() + "@scms.vn";
        MvcResult res = mockMvc.perform(post("/api/v1/branches")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content("""
                                {"code":"%s","name":"Chi nhánh %s","address":"1 Nguyen Trai","phone":"%s",
                                 "email":"%s","openingTime":"07:00","closingTime":"22:00","tableCount":12,
                                 "status":"OPEN"}""".formatted(code, code, phone, email)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("id").asInt();
    }

    private int createStaff(String token, int branchId, String phone, String pin, String cardCode) throws Exception {
        String cardJson = cardCode != null ? ",\"cardCode\":\"" + cardCode + "\"" : "";
        MvcResult res = mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content("""
                                {"fullName":"Nhan vien %s","phone":"%s","position":"BARISTA","branchId":%d,
                                 "pin":"%s"%s,"status":"ACTIVE"}""".formatted(phone, phone, branchId, pin, cardJson)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("id").asInt();
    }

    private int scheduleShift(String token, int branchId, LocalDate shiftDate, int employeeId) throws Exception {
        Instant start = shiftDate.atTime(7, 0).atZone(DateTimeUtil.VN).toInstant();
        Instant end = shiftDate.atTime(12, 30).atZone(DateTimeUtil.VN).toInstant();
        MvcResult res = mockMvc.perform(post("/api/v1/shifts/schedules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content("""
                                {"branchId":%d,"shiftDate":"%s","shiftType":"MORNING",
                                 "plannedStart":"%s","plannedEnd":"%s",
                                 "assignedEmployeeIds":[%d]}"""
                                .formatted(branchId, shiftDate, start, end, employeeId)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("shiftId").asInt();
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
        String cardCode = "CARD" + seq;
        int employeeId = createStaff(token, branchId, phone, pin, cardCode);

        // 1. Kiểm tra danh sách Kiosk employees ban đầu -> chưa gán ca, chưa check-in
        mockMvc.perform(get("/api/v1/attendance/kiosk/employees?branchId=" + branchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].employeeId").value(employeeId))
                .andExpect(jsonPath("$.data[0].isCheckedIn").value(false))
                .andExpect(jsonPath("$.data[0].hasAssignedShift").value(false));

        // 1b. Chưa được gán ca -> bị chặn check-in / check-out / quẹt thẻ (422 SHIFT_ASSIGNMENT_REQUIRED)
        mockMvc.perform(post("/api/v1/attendance/check-in")
                        .contentType(JSON)
                        .content("{\"employeeId\":" + employeeId + ",\"pin\":\"" + pin + "\",\"branchId\":" + branchId + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detailCode").value("SHIFT_ASSIGNMENT_REQUIRED"));

        mockMvc.perform(post("/api/v1/attendance/check-out")
                        .contentType(JSON)
                        .content("{\"employeeId\":" + employeeId + ",\"pin\":\"" + pin + "\",\"branchId\":" + branchId + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detailCode").value("SHIFT_ASSIGNMENT_REQUIRED"));

        mockMvc.perform(post("/api/v1/attendance/card-swipe")
                        .contentType(JSON)
                        .content("{\"cardCode\":\"" + cardCode + "\",\"branchId\":" + branchId + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detailCode").value("SHIFT_ASSIGNMENT_REQUIRED"));

        // 1c. Phân công nhân viên vào ca làm việc hôm nay -> hasAssignedShift = true
        scheduleShift(token, branchId, DateTimeUtil.todayVN(), employeeId);

        mockMvc.perform(get("/api/v1/attendance/kiosk/employees?branchId=" + branchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].employeeId").value(employeeId))
                .andExpect(jsonPath("$.data[0].hasAssignedShift").value(true));

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

    @Test
    void shift_assignment_gate_handles_tomorrow_and_closed_shift_correctly() throws Exception {
        String token = cooToken();
        int seq = SEQ.incrementAndGet();
        int branchId = createBranch(token, "BR-" + seq);

        String pinFuture = "4321";
        int futureEmp = createStaff(token, branchId, "0987" + String.format("%06d", seq), pinFuture, "FUT" + seq);
        scheduleShift(token, branchId, DateTimeUtil.todayVN().plusDays(1), futureEmp);

        // Nhân viên chỉ được gán ca NGÀY MAI -> hôm nay không được check-in
        mockMvc.perform(post("/api/v1/attendance/check-in")
                        .contentType(JSON)
                        .content("{\"employeeId\":" + futureEmp + ",\"pin\":\"" + pinFuture + "\",\"branchId\":" + branchId + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detailCode").value("SHIFT_ASSIGNMENT_REQUIRED"));

        // Nhân viên gán ca hôm nay, đã mở ca & check-in trước khi quản lý đóng ca -> sau khi đóng ca vẫn check-out được
        String pinCurrent = "5678";
        int seq2 = SEQ.incrementAndGet();
        int currentEmp = createStaff(token, branchId, "0986" + String.format("%06d", seq2), pinCurrent, "CUR" + seq2);
        int shiftId = scheduleShift(token, branchId, DateTimeUtil.todayVN(), currentEmp);
        if (LocalTime.now(DateTimeUtil.VN).isBefore(LocalTime.of(6, 0))) {
            Shift s = shiftRepository.findById(shiftId).orElseThrow();
            s.setPlannedStart(Instant.now().minusSeconds(600));
            shiftRepository.save(s);
        }

        MvcResult openRes = mockMvc.perform(post("/api/v1/shifts/open")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content("{\"branchId\":" + branchId + ",\"openingCash\":1000000,\"scheduledShiftId\":" + shiftId + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        int openedShiftId = objectMapper.readTree(openRes.getResponse().getContentAsString()).path("data").path("id").asInt();

        mockMvc.perform(post("/api/v1/attendance/card-swipe")
                        .contentType(JSON)
                        .content("{\"cardCode\":\"CUR" + seq2 + "\",\"branchId\":" + branchId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CHECKED_IN"));

        // Quản lý đóng ca két tiền xong
        mockMvc.perform(post("/api/v1/shifts/" + openedShiftId + "/close")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content("{\"countedCash\":1000000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));

        attendanceRepository.findAllByUserIdOrderByCheckInTimeDesc(currentEmp).forEach(att -> {
            att.setCheckInTime(Instant.now().minus(Duration.ofMinutes(20)));
            attendanceRepository.save(att);
        });

        // Nhân viên đã check-in từ ca vừa đóng vẫn check-out được bình thường
        mockMvc.perform(post("/api/v1/attendance/check-out")
                        .contentType(JSON)
                        .content("{\"employeeId\":" + currentEmp + ",\"pin\":\"" + pinCurrent + "\",\"branchId\":" + branchId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        // Nhưng không được check-in mới khi tất cả ca hôm nay của nhân viên đó đã CLOSED
        mockMvc.perform(post("/api/v1/attendance/check-in")
                        .contentType(JSON)
                        .content("{\"employeeId\":" + currentEmp + ",\"pin\":\"" + pinCurrent + "\",\"branchId\":" + branchId + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detailCode").value("SHIFT_ASSIGNMENT_REQUIRED"));
    }

    @Test
    void pin_lockout_after_3_wrong_attempts_alerts_superiors_and_supports_early_unlock() throws Exception {
        String cooToken = cooToken();
        int seq1 = SEQ.incrementAndGet();
        int seq2 = SEQ.incrementAndGet();
        int branch1 = createBranch(cooToken, "BR-" + seq1);
        int branch2 = createBranch(cooToken, "BR-" + seq2);

        String bm1User = "bm" + seq1;
        String bm2User = "bm" + seq2;
        String cashierUser = "cs" + seq1;

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + cooToken)
                        .contentType(JSON)
                        .content("""
                                {"fullName":"BM Branch 1","phone":"0971%06d","position":"BRANCH_MANAGER","branchId":%d,
                                 "username":"%s","password":"Password1","status":"ACTIVE"}"""
                                .formatted(seq1, branch1, bm1User)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + cooToken)
                        .contentType(JSON)
                        .content("""
                                {"fullName":"BM Branch 2","phone":"0972%06d","position":"BRANCH_MANAGER","branchId":%d,
                                 "username":"%s","password":"Password1","status":"ACTIVE"}"""
                                .formatted(seq2, branch2, bm2User)))
                .andExpect(status().isCreated());

        Role cashierRole = roleRepository.findByCode("CASHIER").orElseThrow();
        userRepository.save(User.builder()
                .fullName("Cashier B1")
                .phone("0973" + String.format("%06d", seq1))
                .position(Roles.CASHIER)
                .roleId(cashierRole.getId())
                .branchId(branch1)
                .username(cashierUser)
                .passwordHash(passwordEncoder.encode("Password1"))
                .status(UserStatus.ACTIVE)
                .build());

        String bm1Token = loginToken(bm1User, "Password1");
        String bm2Token = loginToken(bm2User, "Password1");
        String cashierToken = loginToken(cashierUser, "Password1");

        String staffPin = "2580";
        String staffPhone = "0974" + String.format("%06d", seq1);
        int empId = createStaff(cooToken, branch1, staffPhone, staffPin, "LOCK" + seq1);
        scheduleShift(cooToken, branch1, DateTimeUtil.todayVN(), empId);

        // Sai lần 1 & 2 -> 401 INVALID_PIN
        mockMvc.perform(post("/api/v1/attendance/check-in")
                        .contentType(JSON)
                        .content("{\"employeeId\":" + empId + ",\"pin\":\"9999\",\"branchId\":" + branch1 + "}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_PIN"));

        mockMvc.perform(post("/api/v1/attendance/check-in")
                        .contentType(JSON)
                        .content("{\"employeeId\":" + empId + ",\"pin\":\"8888\",\"branchId\":" + branch1 + "}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_PIN"));

        // Sai lần 3 -> 401 PIN_LOCKED + Retry-After + tạo cảnh báo cho cấp trên
        mockMvc.perform(post("/api/v1/attendance/check-in")
                        .contentType(JSON)
                        .content("{\"employeeId\":" + empId + ",\"pin\":\"7777\",\"branchId\":" + branch1 + "}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("PIN_LOCKED"));

        // Dù nhập PIN đúng ngay sau đó vẫn bị chặn 401 PIN_LOCKED
        mockMvc.perform(post("/api/v1/attendance/check-in")
                        .contentType(JSON)
                        .content("{\"employeeId\":" + empId + ",\"pin\":\"" + staffPin + "\",\"branchId\":" + branch1 + "}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("PIN_LOCKED"));

        // Kiosk employees phản ánh trạng thái pinLocked = true
        mockMvc.perform(get("/api/v1/attendance/kiosk/employees?branchId=" + branch1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.employeeId==" + empId + ")].pinLocked", hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.employeeId==" + empId + ")].pinLockedUntil", hasItem(notNullValue())));

        // Cashier không được xem cảnh báo bảo mật -> 403
        mockMvc.perform(get("/api/v1/security-alerts")
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isForbidden());

        // BM chi nhánh khác không xem/mở khóa được cảnh báo chi nhánh 1 -> 403
        mockMvc.perform(get("/api/v1/security-alerts?branchId=" + branch1)
                        .header("Authorization", "Bearer " + bm2Token))
                .andExpect(status().isForbidden());

        // BM chi nhánh 1 và COO đều thấy cảnh báo ACTIVE + currentlyLocked=true
        MvcResult bmAlerts = mockMvc.perform(get("/api/v1/security-alerts")
                        .header("Authorization", "Bearer " + bm1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentlyLockedCount").value(1))
                .andExpect(jsonPath("$.data.items[0].targetUserId").value(empId))
                .andExpect(jsonPath("$.data.items[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.items[0].currentlyLocked").value(true))
                .andReturn();
        int alertId = objectMapper.readTree(bmAlerts.getResponse().getContentAsString())
                .path("data").path("items").get(0).path("id").asInt();

        mockMvc.perform(get("/api/v1/security-alerts?branchId=" + branch1)
                        .header("Authorization", "Bearer " + cooToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id==" + alertId + ")].currentlyLocked", hasItem(true)));

        // BM2 thử mở khóa cảnh báo chi nhánh 1 -> 403 CROSS_BRANCH_DENIED
        mockMvc.perform(post("/api/v1/security-alerts/" + alertId + "/unlock-pin")
                        .header("Authorization", "Bearer " + bm2Token)
                        .contentType(JSON)
                        .content("{\"note\":\"test\"}"))
                .andExpect(status().isForbidden());

        // BM1 bấm Mở khóa PIN sớm -> alert UNLOCKED, employee check-in lại bình thường
        mockMvc.perform(post("/api/v1/security-alerts/" + alertId + "/unlock-pin")
                        .header("Authorization", "Bearer " + bm1Token)
                        .contentType(JSON)
                        .content("{\"note\":\"Xác minh nhân viên nhập nhầm\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UNLOCKED"))
                .andExpect(jsonPath("$.data.currentlyLocked").value(false));

        mockMvc.perform(get("/api/v1/attendance/kiosk/employees?branchId=" + branch1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.employeeId==" + empId + ")].pinLocked", hasItem(false)));

        mockMvc.perform(post("/api/v1/attendance/check-in")
                        .contentType(JSON)
                        .content("{\"employeeId\":" + empId + ",\"pin\":\"" + staffPin + "\",\"branchId\":" + branch1 + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("CHECKED_IN"));
    }

    private String loginToken(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login").contentType(JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("accessToken").asText();
    }
}
