package edu.fpt.sba.scms.controller;

import edu.fpt.sba.scms.common.enums.BranchStatus;
import edu.fpt.sba.scms.common.enums.Roles;
import edu.fpt.sba.scms.common.enums.UserStatus;
import edu.fpt.sba.scms.model.Attendance;
import edu.fpt.sba.scms.model.Branch;
import edu.fpt.sba.scms.model.Role;
import edu.fpt.sba.scms.model.User;
import edu.fpt.sba.scms.repository.AttendanceRepository;
import edu.fpt.sba.scms.repository.BranchRepository;
import edu.fpt.sba.scms.repository.RoleRepository;
import edu.fpt.sba.scms.repository.UserRepository;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class PayrollFlowIT {

    private static final AtomicInteger SEQ = new AtomicInteger(300);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String cooToken;
    private Integer testBranchId;
    private Integer employeeId;

    @BeforeEach
    void setUp() throws Exception {
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

        int seq = SEQ.incrementAndGet();
        Branch branch = Branch.builder()
                .code("BR_PAY_" + seq)
                .name("Chi nhánh Test Payroll " + seq)
                .address("789 Payroll Road")
                .openingTime(LocalTime.of(7, 0))
                .closingTime(LocalTime.of(22, 0))
                .status(BranchStatus.OPEN)
                .build();
        Branch savedBranch = branchRepository.save(branch);
        this.testBranchId = savedBranch.getId();

        Role staffRole = roleRepository.findByCode(Roles.CASHIER.code())
                .orElseGet(() -> roleRepository.save(Role.builder().code(Roles.CASHIER.code()).name("Cashier").build()));

        User staff = User.builder()
                .branchId(testBranchId)
                .roleId(staffRole.getId())
                .role(staffRole)
                .username("staff_pay_" + seq)
                .fullName("Nhân viên Thu ngân " + seq)
                .passwordHash(passwordEncoder.encode("Password@123"))
                .position(Roles.CASHIER)
                .status(UserStatus.ACTIVE)
                .build();
        User savedStaff = userRepository.save(staff);
        this.employeeId = savedStaff.getId();

        // Tạo 1 lượt chấm công 8 tiếng cho nhân viên trong kỳ tháng 11
        Instant checkIn = LocalDate.now().atTime(8, 0).toInstant(java.time.ZoneOffset.UTC);
        Instant checkOut = checkIn.plus(8, ChronoUnit.HOURS);
        Attendance attendance = Attendance.builder()
                .userId(employeeId)
                .branchId(testBranchId)
                .checkInTime(checkIn)
                .checkOutTime(checkOut)
                .status(edu.fpt.sba.scms.common.enums.AttendanceStatus.COMPLETED)
                .build();
        attendanceRepository.save(attendance);
    }

    @Test
    @DisplayName("UC45: Vòng đời Bảng lương: Tạo kỳ lương -> Thưởng phạt -> Nộp duyệt -> COO phê duyệt")
    void testPayrollLifecycleFlow() throws Exception {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(5);
        LocalDate end = today.plusDays(5);

        // 1. Tạo kỳ lương mới (DRAFT)
        String createPayload = """
                {
                    "branchId": %d,
                    "periodName": "Kỳ lương Tháng 11 Test",
                    "startDate": "%s",
                    "endDate": "%s"
                }
                """.formatted(testBranchId, start, end);

        MvcResult createRes = mockMvc.perform(post("/api/v1/payroll/periods")
                        .header("Authorization", "Bearer " + cooToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.totalEmployees").value(1))
                .andReturn();

        int periodId = com.jayway.jsonpath.JsonPath.read(createRes.getResponse().getContentAsString(), "$.data.id");

        // 2. Xem danh sách chi tiết phiếu lương
        MvcResult itemsRes = mockMvc.perform(get("/api/v1/payroll/periods/" + periodId + "/items")
                        .header("Authorization", "Bearer " + cooToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andReturn();

        int itemId = com.jayway.jsonpath.JsonPath.read(itemsRes.getResponse().getContentAsString(), "$.data[0].id");

        // 3. Điều chỉnh thưởng / phạt cho nhân viên (Update Item)
        String updateItemPayload = """
                {
                    "bonus": 50000,
                    "deduction": 10000,
                    "note": "Thưởng phục vụ nhiệt tình"
                }
                """;
        mockMvc.perform(put("/api/v1/payroll/items/" + itemId)
                        .header("Authorization", "Bearer " + cooToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateItemPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bonus").value(50000))
                .andExpect(jsonPath("$.data.deduction").value(10000));

        // 4. Quản lý chi nhánh nộp bảng lương (Submit)
        mockMvc.perform(post("/api/v1/payroll/periods/" + periodId + "/submit")
                        .header("Authorization", "Bearer " + cooToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));

        // 5. Giám đốc vận hành (COO) phê duyệt bảng lương (Approve)
        mockMvc.perform(post("/api/v1/payroll/periods/" + periodId + "/approve")
                        .header("Authorization", "Bearer " + cooToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }
}
