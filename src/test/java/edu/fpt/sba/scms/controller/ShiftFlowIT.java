package edu.fpt.sba.scms.controller;

import edu.fpt.sba.scms.common.enums.BranchStatus;
import edu.fpt.sba.scms.common.enums.Roles;
import edu.fpt.sba.scms.common.enums.ShiftStatus;
import edu.fpt.sba.scms.common.enums.UserStatus;
import edu.fpt.sba.scms.model.Branch;
import edu.fpt.sba.scms.model.Role;
import edu.fpt.sba.scms.model.User;
import edu.fpt.sba.scms.repository.BranchRepository;
import edu.fpt.sba.scms.repository.RoleRepository;
import edu.fpt.sba.scms.repository.ShiftRepository;
import edu.fpt.sba.scms.repository.UserRepository;
import edu.fpt.sba.scms.service.support.PinHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ShiftFlowIT {

    private static final AtomicInteger SEQ = new AtomicInteger(100);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private ShiftRepository shiftRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PinHasher pinHasher;

    private String cooToken;
    private Integer testBranchId;
    private Integer managerId;
    private static final String MANAGER_PIN = "654321";

    @BeforeEach
    void setUp() throws Exception {
        // Đăng nhập lấy COO token
        String loginPayload = """
                {
                    "username": "coo",
                    "password": "Admin@123"
                }
                """;
        MvcResult res = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andReturn();
        this.cooToken = com.jayway.jsonpath.JsonPath.read(res.getResponse().getContentAsString(), "$.data.accessToken");

        // Tạo chi nhánh test
        int seq = SEQ.incrementAndGet();
        Branch branch = Branch.builder()
                .code("B_SH" + seq)
                .name("Chi nhánh Test Shift " + seq)
                .address("123 Test Street")
                .openingTime(LocalTime.of(7, 0))
                .closingTime(LocalTime.of(22, 0))
                .status(BranchStatus.OPEN)
                .build();
        Branch savedBranch = branchRepository.save(branch);
        this.testBranchId = savedBranch.getId();

        Role bmRole = roleRepository.findByCode(Roles.BRANCH_MANAGER.code())
                .orElseGet(() -> roleRepository.save(Role.builder().code(Roles.BRANCH_MANAGER.code()).name("Branch Manager").build()));

        // Tạo Quản lý chi nhánh (BM) với PIN để test xác thực 2 người duyệt (BR-32)
        String lookupHash = pinHasher.lookupHash(testBranchId, MANAGER_PIN);
        String pinHash = pinHasher.hash(MANAGER_PIN);

        User bm = User.builder()
                .branchId(testBranchId)
                .roleId(bmRole.getId())
                .role(bmRole)
                .username("bm_shift_" + seq)
                .fullName("Quản lý Shift " + seq)
                .passwordHash(passwordEncoder.encode("Password@123"))
                .pinHash(pinHash)
                .pinLookupHash(lookupHash)
                .position(Roles.BRANCH_MANAGER)
                .status(UserStatus.ACTIVE)
                .build();
        User savedBm = userRepository.save(bm);
        this.managerId = savedBm.getId();
    }

    @Test
    @DisplayName("UC24, UC25, UC26, UC27: Luồng hoàn chỉnh Vận hành Két tiền & Đóng/Mở ca làm việc POS")
    void testCashDrawerShiftFlow() throws Exception {
        // 1. Mở ca (UC24) với tiền đầu ca = 1.000.000 đ
        String openPayload = """
                {
                    "branchId": %d,
                    "openingCash": 1000000,
                    "note": "Mở ca sáng đầu ngày"
                }
                """.formatted(testBranchId);

        MvcResult openRes = mockMvc.perform(post("/api/v1/shifts/open")
                        .header("Authorization", "Bearer " + cooToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(openPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.openingCash").value(1000000))
                .andExpect(jsonPath("$.data.expectedCash").value(1000000))
                .andReturn();

        int shiftId = com.jayway.jsonpath.JsonPath.read(openRes.getResponse().getContentAsString(), "$.data.id");

        // Thử mở thêm 1 ca nữa tại cùng chi nhánh -> Bị chặn theo BR-36 (chỉ được 1 ca mở)
        mockMvc.perform(post("/api/v1/shifts/open")
                        .header("Authorization", "Bearer " + cooToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(openPayload))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detailCode").value("SHIFT_ALREADY_OPEN"));

        // 2. Nạp thêm tiền lẻ vào két (Cash In - UC25) = 200.000 đ (cần PIN BM)
        String cashInPayload = """
                {
                    "amount": 200000,
                    "reason": "Nạp thêm tiền lẻ 10k, 20k",
                    "managerPin": "%s"
                }
                """.formatted(MANAGER_PIN);

        mockMvc.perform(post("/api/v1/shifts/" + shiftId + "/cash-in")
                        .header("Authorization", "Bearer " + cooToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cashInPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("CASH_IN"))
                .andExpect(jsonPath("$.data.amount").value(200000));

        // 3. Rút bớt tiền khỏi két (Cash Out - UC26) = 150.000 đ (nộp quỹ)
        String cashOutPayload = """
                {
                    "amount": 150000,
                    "reason": "Rút nộp két sắt chi nhánh",
                    "managerPin": "%s"
                }
                """.formatted(MANAGER_PIN);

        mockMvc.perform(post("/api/v1/shifts/" + shiftId + "/cash-out")
                        .header("Authorization", "Bearer " + cooToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cashOutPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("CASH_OUT"))
                .andExpect(jsonPath("$.data.amount").value(150000));

        // 4. Xem tổng kết ca & két hiện tại
        // Expected Cash = 1.000.000 + 200.000 - 150.000 = 1.050.000 đ
        mockMvc.perform(get("/api/v1/shifts/current?branchId=" + testBranchId)
                        .header("Authorization", "Bearer " + cooToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentCashBalance").value(1050000))
                .andExpect(jsonPath("$.data.transactions", hasSize(3)));

        // 5. Đóng ca (Close Shift - UC27):
        // Giả sử đếm thực tế được 1.040.000 đ (lệch -10.000 đ) nhưng KHÔNG nhập lý do -> Bị từ chối theo BR-38
        String closeMissingReason = """
                {
                    "countedCash": 1040000,
                    "discrepancyReason": ""
                }
                """;
        mockMvc.perform(post("/api/v1/shifts/" + shiftId + "/close")
                        .header("Authorization", "Bearer " + cooToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(closeMissingReason))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detailCode").value("REASON_REQUIRED"));

        // Đóng ca thành công kèm lý do giải trình
        String closeValid = """
                {
                    "countedCash": 1040000,
                    "discrepancyReason": "Thối nhầm 10.000 đ tiền lẻ cho khách bàn 2"
                }
                """;
        mockMvc.perform(post("/api/v1/shifts/" + shiftId + "/close")
                        .header("Authorization", "Bearer " + cooToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(closeValid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.variance").value(-10000))
                .andExpect(jsonPath("$.data.countedCash").value(1040000));
    }

    @Test
    @DisplayName("UC08: Tạo lịch ca làm việc và phân công nhân viên")
    void testScheduleShiftFlow() throws Exception {
        String schedulePayload = """
                {
                    "branchId": %d,
                    "shiftDate": "2026-11-20",
                    "shiftType": "MORNING",
                    "plannedStart": "2026-11-20T07:00:00Z",
                    "plannedEnd": "2026-11-20T15:00:00Z",
                    "assignedEmployeeIds": [%d]
                }
                """.formatted(testBranchId, managerId);

        mockMvc.perform(post("/api/v1/shifts/schedules")
                        .header("Authorization", "Bearer " + cooToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(schedulePayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.shiftType").value("MORNING"))
                .andExpect(jsonPath("$.data.totalEmployees").value(1));

        // Tra cứu lịch ca trong khoảng ngày
        mockMvc.perform(get("/api/v1/shifts/schedules?branchId=" + testBranchId + "&startDate=2026-11-19&endDate=2026-11-21")
                        .header("Authorization", "Bearer " + cooToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
    }
}
