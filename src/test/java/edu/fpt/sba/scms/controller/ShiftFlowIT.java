package edu.fpt.sba.scms.controller;

import edu.fpt.sba.scms.common.enums.AttendanceStatus;
import edu.fpt.sba.scms.common.enums.BranchStatus;
import edu.fpt.sba.scms.common.enums.Roles;
import edu.fpt.sba.scms.common.enums.SecurityAlertStatus;
import edu.fpt.sba.scms.common.enums.SecurityAlertType;
import edu.fpt.sba.scms.common.enums.ShiftHandoverStatus;
import edu.fpt.sba.scms.common.enums.ShiftStatus;
import edu.fpt.sba.scms.common.enums.UserStatus;
import edu.fpt.sba.scms.common.util.DateTimeUtil;
import edu.fpt.sba.scms.model.Branch;
import edu.fpt.sba.scms.model.Role;
import edu.fpt.sba.scms.model.Shift;
import edu.fpt.sba.scms.model.ShiftAssignment;
import edu.fpt.sba.scms.model.ShiftAssignmentId;
import edu.fpt.sba.scms.model.User;
import edu.fpt.sba.scms.repository.AttendanceRepository;
import edu.fpt.sba.scms.repository.BranchRepository;
import edu.fpt.sba.scms.repository.RoleRepository;
import edu.fpt.sba.scms.repository.SecurityAlertRepository;
import edu.fpt.sba.scms.repository.ShiftAssignmentRepository;
import edu.fpt.sba.scms.repository.ShiftHandoverRepository;
import edu.fpt.sba.scms.repository.ShiftRepository;
import edu.fpt.sba.scms.repository.UserRepository;
import edu.fpt.sba.scms.service.support.PinHasher;
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

import edu.fpt.sba.scms.common.enums.ShiftType;
import edu.fpt.sba.scms.domain.shift.ShiftSchedulePolicy;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    private ShiftAssignmentRepository shiftAssignmentRepository;

    @Autowired
    private ShiftHandoverRepository shiftHandoverRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private SecurityAlertRepository securityAlertRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PinHasher pinHasher;

    private String cooToken;
    private String bmToken;
    private String bmUsername;
    private Integer testBranchId;
    private Integer managerId;
    private Integer staffId;
    private Integer cashierId;
    private static final String MANAGER_PIN = "654321";
    private static final String CASHIER_PIN = "2468";

    private int scheduleCashierForCurrentWindow(Integer branchId, Integer employeeId) {
        Instant now = Instant.now();
        ZoneId vn = DateTimeUtil.VN;
        ShiftType inferred = ShiftSchedulePolicy.inferShiftType(LocalTime.now(vn));
        Shift shift = shiftRepository.save(Shift.builder()
                .branchId(branchId)
                .shiftDate(LocalDate.now(vn))
                .shiftType(inferred)
                .plannedStart(now.minusSeconds(1800))
                .plannedEnd(now.plusSeconds(14400))
                .status(ShiftStatus.SCHEDULED)
                .build());
        User emp = userRepository.findById(employeeId).orElseThrow();
        shiftAssignmentRepository.save(ShiftAssignment.builder()
                .id(new ShiftAssignmentId(shift.getId(), employeeId))
                .shift(shift)
                .employee(emp)
                .roleInShift("CASHIER")
                .build());
        return shift.getId();
    }

    private void moveShiftIntoOnTimeCloseWindow(Integer shiftId) {
        Shift s = shiftRepository.findById(shiftId).orElseThrow();
        s.setPlannedEnd(Instant.now().plusSeconds(300));
        shiftRepository.save(s);
    }

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
        Role baristaRole = roleRepository.findByCode(Roles.BARISTA.code())
                .orElseGet(() -> roleRepository.save(Role.builder().code(Roles.BARISTA.code()).name("Barista").build()));
        Role cashierRole = roleRepository.findByCode(Roles.CASHIER.code())
                .orElseGet(() -> roleRepository.save(Role.builder().code(Roles.CASHIER.code()).name("Thu ngân").build()));

        // Tạo Quản lý chi nhánh (BM) với PIN để test xác thực 2 người duyệt (BR-32) và đăng nhập trực tiếp
        String lookupHash = pinHasher.lookupHash(testBranchId, MANAGER_PIN);
        String pinHash = pinHasher.hash(MANAGER_PIN);
        this.bmUsername = "bm_shift_" + seq;

        User bm = User.builder()
                .branchId(testBranchId)
                .roleId(bmRole.getId())
                .role(bmRole)
                .username(bmUsername)
                .fullName("Quản lý Shift " + seq)
                .passwordHash(passwordEncoder.encode("Password@123"))
                .pinHash(pinHash)
                .pinLookupHash(lookupHash)
                .position(Roles.BRANCH_MANAGER)
                .status(UserStatus.ACTIVE)
                .build();
        User savedBm = userRepository.save(bm);
        this.managerId = savedBm.getId();

        MvcResult bmRes = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"Password@123\"}".formatted(bmUsername)))
                .andExpect(status().isOk())
                .andReturn();
        this.bmToken = com.jayway.jsonpath.JsonPath.read(bmRes.getResponse().getContentAsString(), "$.data.accessToken");

        User barista = User.builder()
                .branchId(testBranchId)
                .roleId(baristaRole.getId())
                .role(baristaRole)
                .fullName("Pha chế Shift " + seq)
                .pinHash(pinHasher.hash("1112"))
                .pinLookupHash(pinHasher.lookupHash(testBranchId, "1112"))
                .position(Roles.BARISTA)
                .status(UserStatus.ACTIVE)
                .build();
        this.staffId = userRepository.save(barista).getId();

        User cashier = User.builder()
                .branchId(testBranchId)
                .roleId(cashierRole.getId())
                .role(cashierRole)
                .fullName("Thu ngân Shift " + seq)
                .pinHash(pinHasher.hash(CASHIER_PIN))
                .pinLookupHash(pinHasher.lookupHash(testBranchId, CASHIER_PIN))
                .position(Roles.CASHIER)
                .status(UserStatus.ACTIVE)
                .build();
        this.cashierId = userRepository.save(cashier).getId();
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
                .andExpect(jsonPath("$.data.totalCashIn").value(200000))
                .andExpect(jsonPath("$.data.totalCashOut").value(150000))
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

        // 6. Tra cứu danh sách lịch sử ca + chi tiết ca đã đóng
        mockMvc.perform(get("/api/v1/shifts?branchId=" + testBranchId + "&status=CLOSED&page=1&size=10")
                        .header("Authorization", "Bearer " + cooToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].id").value(shiftId))
                .andExpect(jsonPath("$.data.content[0].variance").value(-10000));

        mockMvc.perform(get("/api/v1/shifts/" + shiftId)
                        .header("Authorization", "Bearer " + cooToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shift.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.transactions", hasSize(4)));

        // Không cho mở lại ca đã CLOSED
        String reopenClosedPayload = """
                {
                    "branchId": %d,
                    "scheduledShiftId": %d,
                    "openingCash": 1000000
                }
                """.formatted(testBranchId, shiftId);
        mockMvc.perform(post("/api/v1/shifts/open")
                        .header("Authorization", "Bearer " + cooToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reopenClosedPayload))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detailCode").value("SHIFT_CLOSED"));
    }

    @Test
    @DisplayName("Kiểm két Thu ngân (PIN), đối soát kết ca cũ, phân quyền nạp/rút BM & COO và đa chi nhánh COO")
    void testCashierHandoverPermissionsAndMultiBranchCooView() throws Exception {
        int sched1Id = scheduleCashierForCurrentWindow(testBranchId, cashierId);

        // 1. Thu ngân mở khóa bằng PIN (tài khoản không có username/password)
        MvcResult pinLoginRes = mockMvc.perform(post("/api/v1/auth/pin-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"employeeId\":%d,\"pin\":\"%s\"}"
                                .formatted(testBranchId, cashierId, CASHIER_PIN)))
                .andExpect(status().isOk())
                .andReturn();
        String cashierToken = com.jayway.jsonpath.JsonPath.read(pinLoginRes.getResponse().getContentAsString(), "$.data.accessToken");

        // Chưa có ca đóng trước -> hasPreviousShift = false
        mockMvc.perform(get("/api/v1/shifts/previous-closed?branchId=" + testBranchId)
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasPreviousShift").value(false));

        // 2. Thu ngân mở Ca #1 với 1.000.000 đ
        MvcResult shift1Res = mockMvc.perform(post("/api/v1/shifts/open")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "branchId": %d,
                                    "scheduledShiftId": %d,
                                    "openingCash": 1000000,
                                    "note": "Thu ngân mở ca sáng"
                                }
                                """.formatted(testBranchId, sched1Id)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.openedBy").value(cashierId))
                .andExpect(jsonPath("$.data.branchName").exists())
                .andExpect(jsonPath("$.data.openedByName").exists())
                .andReturn();
        int shift1Id = com.jayway.jsonpath.JsonPath.read(shift1Res.getResponse().getContentAsString(), "$.data.id");

        // 3. Thu ngân KHÔNG được phép nạp tiền / rút tiền / xếp lịch / xem overview toàn chuỗi -> 403
        mockMvc.perform(post("/api/v1/shifts/" + shift1Id + "/cash-in")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":100000,"reason":"Thử nạp tiền","managerPin":"%s"}
                                """.formatted(MANAGER_PIN)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(post("/api/v1/shifts/" + shift1Id + "/cash-out")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":100000,"reason":"Thử rút tiền"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(get("/api/v1/shifts/branches-overview")
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isForbidden());

        // 4. BRANCH_MANAGER tự nạp/rút tiền KHÔNG cần PIN -> thành công; COO không cần PIN -> thành công
        mockMvc.perform(post("/api/v1/shifts/" + shift1Id + "/cash-in")
                        .header("Authorization", "Bearer " + bmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":300000,\"reason\":\"Quản lý bổ sung tiền lẻ đầu ca\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("CASH_IN"))
                .andExpect(jsonPath("$.data.isSelfInitiated").value(true))
                .andExpect(jsonPath("$.data.approvedByName").exists());

        mockMvc.perform(post("/api/v1/shifts/" + shift1Id + "/cash-out")
                        .header("Authorization", "Bearer " + cooToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100000,\"reason\":\"COO rút nộp quỹ công ty\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("CASH_OUT"))
                .andExpect(jsonPath("$.data.isSelfInitiated").value(true));

        // 5. Đơn hàng thanh toán tiền mặt có trả tiền thừa: khách đưa 200k cho đơn 120k (thối 80k)
        // Két tăng đúng 120k (expectedCash = 1.000k + 300k - 100k + 120k = 1.320k), KHÔNG sinh CASH_OUT
        MvcResult orderRes = mockMvc.perform(post("/api/v1/payments/orders")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "branchId": %d,
                                    "totalAmount": 120000,
                                    "discountAmount": 0,
                                    "finalAmount": 120000,
                                    "note": "Đơn kiểm tra thối tiền"
                                }
                                """.formatted(testBranchId)))
                .andExpect(status().isCreated())
                .andReturn();
        int orderId = com.jayway.jsonpath.JsonPath.read(orderRes.getResponse().getContentAsString(), "$.data.orderId");

        mockMvc.perform(post("/api/v1/payments/cash")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":%d,\"cashReceived\":200000}".formatted(orderId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.changeReturned").value(80000));

        mockMvc.perform(get("/api/v1/shifts/current?branchId=" + testBranchId)
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cashRevenue").value(120000))
                .andExpect(jsonPath("$.data.totalCashOut").value(100000))
                .andExpect(jsonPath("$.data.currentCashBalance").value(1320000))
                .andExpect(jsonPath("$.data.transactions", hasSize(3)));

        // 6. Thu ngân đóng Ca #1 khớp 1.320.000 đ (trong ngưỡng 10 phút cuối ca)
        moveShiftIntoOnTimeCloseWindow(shift1Id);
        mockMvc.perform(post("/api/v1/shifts/" + shift1Id + "/close")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"countedCash\":1320000,\"discrepancyReason\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.countedCash").value(1320000))
                .andExpect(jsonPath("$.data.closedByName").exists());

        // 7. Trước khi mở Ca #2: API previous-closed trả đúng 1.320.000 đ
        mockMvc.perform(get("/api/v1/shifts/previous-closed?branchId=" + testBranchId)
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasPreviousShift").value(true))
                .andExpect(jsonPath("$.data.shiftId").value(shift1Id))
                .andExpect(jsonPath("$.data.countedCash").value(1320000))
                .andExpect(jsonPath("$.data.closedByName").exists());

        // Mở Ca #2 với tiền thực đếm 1.290.000 đ (lệch -30.000 đ) nhưng KHÔNG nhập note -> 422 REASON_REQUIRED
        mockMvc.perform(post("/api/v1/shifts/open")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"openingCash\":1290000,\"note\":\"\"}".formatted(testBranchId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detailCode").value("REASON_REQUIRED"));

        // Nhập note giải trình chênh lệch đầu ca -> mở Ca #2 thành công + lưu audit bàn giao
        MvcResult shift2Res = mockMvc.perform(post("/api/v1/shifts/open")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"openingCash\":1290000,\"note\":\"Thiếu 30k tiền lẻ do két trước giữ lại đổi\"}"
                                .formatted(testBranchId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.previousShiftId").value(shift1Id))
                .andExpect(jsonPath("$.data.previousClosingCash").value(1320000))
                .andExpect(jsonPath("$.data.openingVariance").value(-30000))
                .andExpect(jsonPath("$.data.openingNote").value("Thiếu 30k tiền lẻ do két trước giữ lại đổi"))
                .andReturn();
        int shift2Id = com.jayway.jsonpath.JsonPath.read(shift2Res.getResponse().getContentAsString(), "$.data.id");

        // Đóng Ca #2 với 1.290.000 đ, mở Ca #3 khớp hoàn toàn 1.290.000 đ (không cần note)
        moveShiftIntoOnTimeCloseWindow(shift2Id);
        mockMvc.perform(post("/api/v1/shifts/" + shift2Id + "/close")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"countedCash\":1290000}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/shifts/open")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"openingCash\":1290000}".formatted(testBranchId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.openingVariance").value(0));

        // 8. Chi nhánh thứ 2 & kiểm tra quyền chéo BM + view đa chi nhánh của COO
        int seq2 = SEQ.incrementAndGet();
        Branch branch2 = branchRepository.save(Branch.builder()
                .code("B2_SH" + seq2)
                .name("Chi nhánh Phụ " + seq2)
                .openingTime(LocalTime.of(7, 0))
                .closingTime(LocalTime.of(22, 0))
                .status(BranchStatus.OPEN)
                .build());

        MvcResult branch2OpenRes = mockMvc.perform(post("/api/v1/shifts/open")
                        .header("Authorization", "Bearer " + cooToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"openingCash\":500000}".formatted(branch2.getId())))
                .andExpect(status().isCreated())
                .andReturn();
        int branch2ShiftId = com.jayway.jsonpath.JsonPath.read(branch2OpenRes.getResponse().getContentAsString(), "$.data.id");

        // BM chi nhánh 1 không thể nạp tiền vào ca của chi nhánh 2 -> 403 CROSS_BRANCH_DENIED
        mockMvc.perform(post("/api/v1/shifts/" + branch2ShiftId + "/cash-in")
                        .header("Authorization", "Bearer " + bmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100000,\"reason\":\"Test chéo chi nhánh\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CROSS_BRANCH_DENIED"));

        // BM không được gọi branches-overview -> 403
        mockMvc.perform(get("/api/v1/shifts/branches-overview")
                        .header("Authorization", "Bearer " + bmToken))
                .andExpect(status().isForbidden());

        // COO xem tất cả chi nhánh qua GET /api/v1/shifts (không truyền branchId) & GET /api/v1/shifts/branches-overview
        mockMvc.perform(get("/api/v1/shifts?page=1&size=50")
                        .header("Authorization", "Bearer " + cooToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].branchId", hasItem(testBranchId)))
                .andExpect(jsonPath("$.data.content[*].branchId", hasItem(branch2.getId())));

        mockMvc.perform(get("/api/v1/shifts/branches-overview")
                        .header("Authorization", "Bearer " + cooToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$.data[?(@.branchId==" + testBranchId + ")].currentCashBalance", hasItem(1290000.0)))
                .andExpect(jsonPath("$.data[?(@.branchId==" + branch2.getId() + ")].currentCashBalance", hasItem(500000.0)));
    }

    @Test
    @DisplayName("UC08: Tạo, cập nhật, kiểm tra trùng lịch, ràng buộc khung giờ từng ca và xóa lịch phân ca nhân viên")
    void testScheduleShiftFlow() throws Exception {
        ZoneId vn = ZoneId.of("Asia/Ho_Chi_Minh");
        LocalDate targetDate = LocalDate.now(vn).plusDays(10);
        Instant mornStart = targetDate.atTime(7, 0).atZone(vn).toInstant();
        Instant mornEnd = targetDate.atTime(12, 30).atZone(vn).toInstant();

        String schedulePayload = """
                {
                    "branchId": %d,
                    "shiftDate": "%s",
                    "shiftType": "MORNING",
                    "plannedStart": "%s",
                    "plannedEnd": "%s",
                    "assignedEmployeeIds": [%d]
                }
                """.formatted(testBranchId, targetDate, mornStart, mornEnd, managerId);

        MvcResult createRes = mockMvc.perform(post("/api/v1/shifts/schedules")
                        .header("Authorization", "Bearer " + cooToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(schedulePayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.shiftType").value("MORNING"))
                .andExpect(jsonPath("$.data.totalEmployees").value(1))
                .andReturn();
        int shiftId1 = com.jayway.jsonpath.JsonPath.read(createRes.getResponse().getContentAsString(), "$.data.shiftId");

        // Tạo ca trùng khung giờ với managerId -> Bị chặn theo BR-15 (SCHEDULE_CONFLICT)
        Instant overlapStart = targetDate.atTime(8, 0).atZone(vn).toInstant();
        Instant overlapEnd = targetDate.atTime(12, 0).atZone(vn).toInstant();
        String conflictPayload = """
                {
                    "branchId": %d,
                    "shiftDate": "%s",
                    "shiftType": "MORNING",
                    "plannedStart": "%s",
                    "plannedEnd": "%s",
                    "assignedEmployeeIds": [%d]
                }
                """.formatted(testBranchId, targetDate, overlapStart, overlapEnd, managerId);
        mockMvc.perform(post("/api/v1/shifts/schedules")
                        .header("Authorization", "Bearer " + cooToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conflictPayload))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detailCode").value("SCHEDULE_CONFLICT"));

        // Cập nhật phân công ca trong khung Ca sáng (07:30-12:00): giữ managerId và gán thêm staffId
        Instant updStart = targetDate.atTime(7, 30).atZone(vn).toInstant();
        Instant updEnd = targetDate.atTime(12, 0).atZone(vn).toInstant();
        String updatePayload = """
                {
                    "shiftDate": "%s",
                    "shiftType": "MORNING",
                    "plannedStart": "%s",
                    "plannedEnd": "%s",
                    "assignedEmployeeIds": [%d, %d]
                }
                """.formatted(targetDate, updStart, updEnd, managerId, staffId);
        mockMvc.perform(put("/api/v1/shifts/schedules/" + shiftId1)
                        .header("Authorization", "Bearer " + cooToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalEmployees").value(2));

        // Đổi sang chỉ còn staffId
        String updateOnlyStaffPayload = """
                {
                    "shiftDate": "%s",
                    "shiftType": "MORNING",
                    "plannedStart": "%s",
                    "plannedEnd": "%s",
                    "assignedEmployeeIds": [%d]
                }
                """.formatted(targetDate, updStart, updEnd, staffId);
        mockMvc.perform(put("/api/v1/shifts/schedules/" + shiftId1)
                        .header("Authorization", "Bearer " + cooToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateOnlyStaffPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalEmployees").value(1))
                .andExpect(jsonPath("$.data.assignedEmployeeIds[0]").value(staffId));

        // Chặn tạo Ca tối qua ngày hôm sau (22:00 -> 21:00+1d) & ngoài khung (17:30-22:00) -> 422 INVALID_SHIFT_TIME
        mockMvc.perform(post("/api/v1/shifts/schedules")
                        .header("Authorization", "Bearer " + cooToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "branchId": %d,
                                    "shiftDate": "%s",
                                    "shiftType": "NIGHT",
                                    "plannedStart": "%s",
                                    "plannedEnd": "%s",
                                    "assignedEmployeeIds": [%d]
                                }
                                """.formatted(
                                        testBranchId,
                                        targetDate,
                                        targetDate.atTime(22, 0).atZone(vn).toInstant(),
                                        targetDate.plusDays(1).atTime(21, 0).atZone(vn).toInstant(),
                                        cashierId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detailCode").value("INVALID_SHIFT_TIME"));

        mockMvc.perform(post("/api/v1/shifts/schedules")
                        .header("Authorization", "Bearer " + cooToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "branchId": %d,
                                    "shiftDate": "%s",
                                    "shiftType": "NIGHT",
                                    "plannedStart": "%s",
                                    "plannedEnd": "%s",
                                    "assignedEmployeeIds": [%d]
                                }
                                """.formatted(
                                        testBranchId,
                                        targetDate,
                                        targetDate.atTime(17, 30).atZone(vn).toInstant(),
                                        targetDate.atTime(22, 0).atZone(vn).toInstant(),
                                        cashierId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detailCode").value("INVALID_SHIFT_TIME"));

        // Cho phép Ca tối 20:00-22:00 (nằm gọn trong 18:00-23:00 cùng ngày)
        MvcResult nightCustomRes = mockMvc.perform(post("/api/v1/shifts/schedules")
                        .header("Authorization", "Bearer " + cooToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "branchId": %d,
                                    "shiftDate": "%s",
                                    "shiftType": "NIGHT",
                                    "plannedStart": "%s",
                                    "plannedEnd": "%s",
                                    "assignedEmployeeIds": [%d]
                                }
                                """.formatted(
                                        testBranchId,
                                        targetDate,
                                        targetDate.atTime(20, 0).atZone(vn).toInstant(),
                                        targetDate.atTime(22, 0).atZone(vn).toInstant(),
                                        cashierId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.shiftType").value("NIGHT"))
                .andReturn();
        int nightCustomId = com.jayway.jsonpath.JsonPath.read(nightCustomRes.getResponse().getContentAsString(), "$.data.shiftId");

        // Tra cứu lịch ca trong khoảng ngày
        mockMvc.perform(get("/api/v1/shifts/schedules?branchId=" + testBranchId
                        + "&startDate=" + targetDate.minusDays(1) + "&endDate=" + targetDate.plusDays(1))
                        .header("Authorization", "Bearer " + cooToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));

        // Xóa ca SCHEDULED
        mockMvc.perform(delete("/api/v1/shifts/schedules/" + nightCustomId)
                        .header("Authorization", "Bearer " + cooToken))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/shifts/schedules/" + shiftId1)
                        .header("Authorization", "Bearer " + cooToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/shifts/schedules?branchId=" + testBranchId
                        + "&startDate=" + targetDate.minusDays(1) + "&endDate=" + targetDate.plusDays(1))
                        .header("Authorization", "Bearer " + cooToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("BR-27 & BR-39: Doanh thu tiền mặt từ đơn hàng POS tự động cập nhật vào ca đang mở")
    void testCashPaymentUpdatesShiftRevenue() throws Exception {
        // 1. Mở ca
        String openPayload = """
                {
                    "branchId": %d,
                    "openingCash": 1000000,
                    "note": "Mở ca test bán hàng"
                }
                """.formatted(testBranchId);

        mockMvc.perform(post("/api/v1/shifts/open")
                        .header("Authorization", "Bearer " + cooToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(openPayload))
                .andExpect(status().isCreated());

        // 2. Tạo đơn hàng 150.000 đ
        String orderPayload = """
                {
                    "branchId": %d,
                    "totalAmount": 150000,
                    "discountAmount": 0,
                    "finalAmount": 150000,
                    "note": "Đơn test POS tiền mặt"
                }
                """.formatted(testBranchId);

        MvcResult orderRes = mockMvc.perform(post("/api/v1/payments/orders")
                        .header("Authorization", "Bearer " + cooToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload))
                .andExpect(status().isCreated())
                .andReturn();
        int orderId = com.jayway.jsonpath.JsonPath.read(orderRes.getResponse().getContentAsString(), "$.data.orderId");

        // 3. Thanh toán tiền mặt (khách đưa 200.000 đ, phải trả 150.000 đ)
        String cashPayload = """
                {
                    "orderId": %d,
                    "cashReceived": 200000
                }
                """.formatted(orderId);

        mockMvc.perform(post("/api/v1/payments/cash")
                        .header("Authorization", "Bearer " + cooToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cashPayload))
                .andExpect(status().isOk());

        // 4. Kiểm tra ca làm việc hiện tại: cashRevenue = 150.000, currentCashBalance = 1.150.000
        mockMvc.perform(get("/api/v1/shifts/current?branchId=" + testBranchId)
                        .header("Authorization", "Bearer " + cooToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cashRevenue").value(150000))
                .andExpect(jsonPath("$.data.currentCashBalance").value(1150000));
    }

    @Test
    @DisplayName("UC08 & BR-15: Khung ca chuẩn 07:00-12:30 / 12:30-18:00 / 18:00-23:00 không tự xung đột và chặn tạo trùng ca đang hoạt động")
    void testStandardShiftBoundariesAndDuplicateSlotGuard() throws Exception {
        ZoneId vn = ZoneId.of("Asia/Ho_Chi_Minh");
        LocalDate todayVN = LocalDate.now(vn);

        Instant morningStart = todayVN.atTime(7, 0).atZone(vn).toInstant();
        Instant morningEnd = todayVN.atTime(12, 30).atZone(vn).toInstant();
        Instant eveningStart = todayVN.atTime(12, 30).atZone(vn).toInstant();
        Instant eveningEnd = todayVN.atTime(18, 0).atZone(vn).toInstant();
        Instant nightStart = todayVN.atTime(18, 0).atZone(vn).toInstant();
        Instant nightEnd = todayVN.atTime(23, 0).atZone(vn).toInstant();

        // 1. Tạo đủ 3 ca chuẩn nối tiếp nhau trong ngày cho cùng 1 nhân viên (không xung đột biên)
        MvcResult morningRes = mockMvc.perform(post("/api/v1/shifts/schedules")
                        .header("Authorization", "Bearer " + cooToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "branchId": %d,
                                    "shiftDate": "%s",
                                    "shiftType": "MORNING",
                                    "plannedStart": "%s",
                                    "plannedEnd": "%s",
                                    "assignedEmployeeIds": [%d]
                                }
                                """.formatted(testBranchId, todayVN, morningStart, morningEnd, managerId)))
                .andExpect(status().isCreated())
                .andReturn();
        int morningId = com.jayway.jsonpath.JsonPath.read(morningRes.getResponse().getContentAsString(), "$.data.shiftId");

        MvcResult eveningRes = mockMvc.perform(post("/api/v1/shifts/schedules")
                        .header("Authorization", "Bearer " + cooToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "branchId": %d,
                                    "shiftDate": "%s",
                                    "shiftType": "EVENING",
                                    "plannedStart": "%s",
                                    "plannedEnd": "%s",
                                    "assignedEmployeeIds": [%d]
                                }
                                """.formatted(testBranchId, todayVN, eveningStart, eveningEnd, managerId)))
                .andExpect(status().isCreated())
                .andReturn();
        int eveningId = com.jayway.jsonpath.JsonPath.read(eveningRes.getResponse().getContentAsString(), "$.data.shiftId");

        MvcResult nightRes = mockMvc.perform(post("/api/v1/shifts/schedules")
                        .header("Authorization", "Bearer " + cooToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "branchId": %d,
                                    "shiftDate": "%s",
                                    "shiftType": "NIGHT",
                                    "plannedStart": "%s",
                                    "plannedEnd": "%s",
                                    "assignedEmployeeIds": [%d]
                                }
                                """.formatted(testBranchId, todayVN, nightStart, nightEnd, managerId)))
                .andExpect(status().isCreated())
                .andReturn();
        int nightId = com.jayway.jsonpath.JsonPath.read(nightRes.getResponse().getContentAsString(), "$.data.shiftId");

        // 2. Tạo trùng Ca sáng (khác nhân viên) trong cùng ngày -> Bị chặn SCHEDULE_CONFLICT
        mockMvc.perform(post("/api/v1/shifts/schedules")
                        .header("Authorization", "Bearer " + cooToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "branchId": %d,
                                    "shiftDate": "%s",
                                    "shiftType": "MORNING",
                                    "plannedStart": "%s",
                                    "plannedEnd": "%s",
                                    "assignedEmployeeIds": [%d]
                                }
                                """.formatted(testBranchId, todayVN, morningStart, morningEnd, staffId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detailCode").value("SCHEDULE_CONFLICT"));

        // 3. Mở ca phát sinh khi cùng khung ca hiện tại đã có ca SCHEDULED -> Bị chặn SCHEDULE_CONFLICT
        mockMvc.perform(post("/api/v1/shifts/open")
                        .header("Authorization", "Bearer " + cooToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"openingCash\":1000000}".formatted(testBranchId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detailCode").value("SCHEDULE_CONFLICT"));

        // 4. Mở & đóng đúng ca đã lên lịch theo giờ hiện tại -> sau khi CLOSED có thể lên lịch ca mới cho cùng slot
        ShiftType currentType = ShiftSchedulePolicy.inferShiftType(LocalTime.now(vn));
        int currentSlotShiftId = switch (currentType) {
            case MORNING -> morningId;
            case EVENING -> eveningId;
            case NIGHT -> nightId;
        };

        mockMvc.perform(post("/api/v1/shifts/open")
                        .header("Authorization", "Bearer " + cooToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"scheduledShiftId\":%d,\"openingCash\":1000000}"
                                .formatted(testBranchId, currentSlotShiftId)))
                .andExpect(status().isCreated());

        // Trong lúc đang OPEN vẫn chặn tạo trùng slot
        Instant slotStart = switch (currentType) {
            case MORNING -> morningStart;
            case EVENING -> eveningStart;
            case NIGHT -> nightStart;
        };
        Instant slotEnd = switch (currentType) {
            case MORNING -> morningEnd;
            case EVENING -> eveningEnd;
            case NIGHT -> nightEnd;
        };
        mockMvc.perform(post("/api/v1/shifts/schedules")
                        .header("Authorization", "Bearer " + cooToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "branchId": %d,
                                    "shiftDate": "%s",
                                    "shiftType": "%s",
                                    "plannedStart": "%s",
                                    "plannedEnd": "%s",
                                    "assignedEmployeeIds": [%d]
                                }
                                """.formatted(testBranchId, todayVN, currentType.name(), slotStart, slotEnd, staffId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detailCode").value("SCHEDULE_CONFLICT"));

        mockMvc.perform(post("/api/v1/shifts/" + currentSlotShiftId + "/close")
                        .header("Authorization", "Bearer " + cooToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"countedCash\":1000000}"))
                .andExpect(status().isOk());

        // Đã CLOSED -> được phép lên lịch mới
        mockMvc.perform(post("/api/v1/shifts/schedules")
                        .header("Authorization", "Bearer " + cooToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "branchId": %d,
                                    "shiftDate": "%s",
                                    "shiftType": "%s",
                                    "plannedStart": "%s",
                                    "plannedEnd": "%s",
                                    "assignedEmployeeIds": [%d]
                                }
                                """.formatted(testBranchId, todayVN, currentType.name(), slotStart, slotEnd, staffId)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Thu ngân kiểm két mở ca tự động Check-in và nhập PIN khi đóng ca để tự động Check-out")
    void testCashierOpenShiftAutoCheckInAndPinCloseAutoCheckOut() throws Exception {
        int schedId = scheduleCashierForCurrentWindow(testBranchId, cashierId);

        // 1. Thu ngân mở khóa bằng PIN
        MvcResult pinLoginRes = mockMvc.perform(post("/api/v1/auth/pin-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"employeeId\":%d,\"pin\":\"%s\"}"
                                .formatted(testBranchId, cashierId, CASHIER_PIN)))
                .andExpect(status().isOk())
                .andReturn();
        String cashierToken = com.jayway.jsonpath.JsonPath.read(pinLoginRes.getResponse().getContentAsString(), "$.data.accessToken");

        // Trước khi mở ca: chưa có attendance mở
        assertTrue(attendanceRepository.findFirstByUserIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(cashierId).isEmpty());

        // 2. Mở ca két -> tự gán nhân sự trực ca & tự động Check-in
        MvcResult openRes = mockMvc.perform(post("/api/v1/shifts/open")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"scheduledShiftId\":%d,\"openingCash\":1000000,\"note\":\"Thu ngân kiểm két mở ca\"}"
                                .formatted(testBranchId, schedId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andReturn();
        int shiftId = com.jayway.jsonpath.JsonPath.read(openRes.getResponse().getContentAsString(), "$.data.id");

        assertTrue(shiftAssignmentRepository.existsById(new ShiftAssignmentId(shiftId, cashierId)),
                "Thu ngân mở ca phải được gán vào nhân sự trực ca");
        var openAttendance = attendanceRepository
                .findFirstByUserIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(cashierId)
                .orElse(null);
        assertNotNull(openAttendance, "Thu ngân mở ca phải được tự động Check-in");
        assertEquals(AttendanceStatus.CHECKED_IN, openAttendance.getStatus());

        // 3. Nhập sai PIN khi đóng ca -> 401 INVALID_PIN, ca vẫn OPEN & vẫn CHECKED_IN
        moveShiftIntoOnTimeCloseWindow(shiftId);
        mockMvc.perform(post("/api/v1/shifts/" + shiftId + "/close")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"countedCash\":1000000,\"pin\":\"9999\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_PIN"));

        assertEquals(ShiftStatus.OPEN, shiftRepository.findById(shiftId).orElseThrow().getStatus());
        assertTrue(attendanceRepository.findFirstByUserIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(cashierId).isPresent());

        // 4. Nhập đúng PIN khi đóng ca -> CLOSED & tự động Check-out COMPLETED
        mockMvc.perform(post("/api/v1/shifts/" + shiftId + "/close")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"countedCash\":1000000,\"pin\":\"%s\"}".formatted(CASHIER_PIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));

        assertEquals(ShiftStatus.CLOSED, shiftRepository.findById(shiftId).orElseThrow().getStatus());
        assertTrue(attendanceRepository.findFirstByUserIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(cashierId).isEmpty(),
                "Sau khi đóng ca kèm PIN phải không còn phiên check-in mở");
        var latestAtt = attendanceRepository.findAllByUserIdOrderByCheckInTimeDesc(cashierId).get(0);
        assertEquals(AttendanceStatus.COMPLETED, latestAtt.getStatus());
        assertNotNull(latestAtt.getCheckOutTime());
        assertNotNull(latestAtt.getWorkedMinutes());
    }

    @Test
    @DisplayName("Phân quyền xem lịch & đối soát: CASHIER chỉ thấy ca đóng gần nhất và không xem được lịch phân ca; BM/COO xem toàn bộ")
    void testRoleScopeVisibilityForCashierManagerAndCoo() throws Exception {
        int sched1Id = scheduleCashierForCurrentWindow(testBranchId, cashierId);

        MvcResult pinLoginRes = mockMvc.perform(post("/api/v1/auth/pin-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"employeeId\":%d,\"pin\":\"%s\"}"
                                .formatted(testBranchId, cashierId, CASHIER_PIN)))
                .andExpect(status().isOk())
                .andReturn();
        String cashierToken = com.jayway.jsonpath.JsonPath.read(pinLoginRes.getResponse().getContentAsString(), "$.data.accessToken");

        LocalDate todayVN = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        LocalDate tomorrowVN = todayVN.plusDays(1);

        // Tạo 2 ca đã đóng (Ca #1 cũ hơn, Ca #2 gần nhất)
        MvcResult open1 = mockMvc.perform(post("/api/v1/shifts/open")
                        .header("Authorization", "Bearer " + bmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"scheduledShiftId\":%d,\"openingCash\":1000000}"
                                .formatted(testBranchId, sched1Id)))
                .andExpect(status().isCreated())
                .andReturn();
        int shift1Id = com.jayway.jsonpath.JsonPath.read(open1.getResponse().getContentAsString(), "$.data.id");
        mockMvc.perform(post("/api/v1/shifts/" + shift1Id + "/close")
                        .header("Authorization", "Bearer " + bmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"countedCash\":1000000}"))
                .andExpect(status().isOk());

        // Đảm bảo closedAt của ca #2 mới hơn ca #1 rõ rệt
        var s1 = shiftRepository.findById(shift1Id).orElseThrow();
        s1.setClosedAt(Instant.now().minusSeconds(120));
        shiftRepository.save(s1);

        MvcResult open2 = mockMvc.perform(post("/api/v1/shifts/open")
                        .header("Authorization", "Bearer " + bmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"openingCash\":1000000}".formatted(testBranchId)))
                .andExpect(status().isCreated())
                .andReturn();
        int shift2Id = com.jayway.jsonpath.JsonPath.read(open2.getResponse().getContentAsString(), "$.data.id");
        mockMvc.perform(post("/api/v1/shifts/" + shift2Id + "/close")
                        .header("Authorization", "Bearer " + bmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"countedCash\":1050000,\"discrepancyReason\":\"Thừa 50k tiền khách bo\"}"))
                .andExpect(status().isOk());

        // Tạo 2 ca SCHEDULED ngày mai: 1 ca gán cả barista + cashier, 1 ca chỉ gán barista
        Instant mornStart = tomorrowVN.atTime(7, 0).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        Instant mornEnd = tomorrowVN.atTime(12, 30).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        Instant eveStart = tomorrowVN.atTime(12, 30).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        Instant eveEnd = tomorrowVN.atTime(18, 0).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();

        MvcResult schedCashierRes = mockMvc.perform(post("/api/v1/shifts/schedules")
                        .header("Authorization", "Bearer " + bmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "branchId": %d,
                                    "shiftDate": "%s",
                                    "shiftType": "MORNING",
                                    "plannedStart": "%s",
                                    "plannedEnd": "%s",
                                    "assignedEmployeeIds": [%d, %d]
                                }
                                """.formatted(testBranchId, tomorrowVN, mornStart, mornEnd, staffId, cashierId)))
                .andExpect(status().isCreated())
                .andReturn();
        int schedWithCashierId = com.jayway.jsonpath.JsonPath.read(schedCashierRes.getResponse().getContentAsString(), "$.data.shiftId");

        MvcResult schedOtherRes = mockMvc.perform(post("/api/v1/shifts/schedules")
                        .header("Authorization", "Bearer " + bmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "branchId": %d,
                                    "shiftDate": "%s",
                                    "shiftType": "EVENING",
                                    "plannedStart": "%s",
                                    "plannedEnd": "%s",
                                    "assignedEmployeeIds": [%d]
                                }
                                """.formatted(testBranchId, tomorrowVN, eveStart, eveEnd, staffId)))
                .andExpect(status().isCreated())
                .andReturn();
        int schedOtherId = com.jayway.jsonpath.JsonPath.read(schedOtherRes.getResponse().getContentAsString(), "$.data.shiftId");

        // Mở ca #3 hiện tại
        MvcResult open3 = mockMvc.perform(post("/api/v1/shifts/open")
                        .header("Authorization", "Bearer " + bmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"openingCash\":1050000}".formatted(testBranchId)))
                .andExpect(status().isCreated())
                .andReturn();
        int shift3OpenId = com.jayway.jsonpath.JsonPath.read(open3.getResponse().getContentAsString(), "$.data.id");

        // 1. CASHIER bị chặn GET /api/v1/shifts/schedules -> 403
        mockMvc.perform(get("/api/v1/shifts/schedules?branchId=" + testBranchId + "&startDate=" + todayVN + "&endDate=" + tomorrowVN)
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        // 2. CASHIER gọi GET /api/v1/shifts/open-candidates -> 200, ẩn nhân sự khác khỏi assignedEmployeeIds
        mockMvc.perform(get("/api/v1/shifts/open-candidates?branchId=" + testBranchId)
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.shiftId==" + schedWithCashierId + ")].assignedEmployeeIds[*]", contains(cashierId)))
                .andExpect(jsonPath("$.data[?(@.shiftId==" + schedOtherId + ")].assignedEmployeeIds[0]").doesNotExist());

        // 3. CASHIER gọi GET /api/v1/shifts -> chỉ thấy DUY NHẤT 1 ca là ca đóng gần nhất (shift2Id)
        mockMvc.perform(get("/api/v1/shifts?branchId=" + testBranchId + "&page=1&size=20")
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].id").value(shift2Id));

        // 4. CASHIER xem chi tiết: ca mở (#3) & ca đóng gần nhất (#2) -> 200; ca cũ hơn (#1) & ca SCHEDULED -> 403
        mockMvc.perform(get("/api/v1/shifts/" + shift3OpenId)
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/shifts/" + shift2Id)
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shift.id").value(shift2Id));

        mockMvc.perform(get("/api/v1/shifts/" + shift1Id)
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(get("/api/v1/shifts/" + schedWithCashierId)
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        // 5. BRANCH_MANAGER và COO vẫn xem được toàn bộ danh sách, lịch phân ca & chi tiết ca cũ
        mockMvc.perform(get("/api/v1/shifts?branchId=" + testBranchId + "&page=1&size=20")
                        .header("Authorization", "Bearer " + bmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(5))
                .andExpect(jsonPath("$.data.content[*].id", hasItem(shift1Id)))
                .andExpect(jsonPath("$.data.content[*].id", hasItem(shift2Id)));

        mockMvc.perform(get("/api/v1/shifts/schedules?branchId=" + testBranchId + "&startDate=" + todayVN + "&endDate=" + tomorrowVN)
                        .header("Authorization", "Bearer " + bmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.shiftId==" + schedWithCashierId + ")].assignedEmployeeIds[*]",
                        hasItem(staffId)));

        mockMvc.perform(get("/api/v1/shifts/" + shift1Id)
                        .header("Authorization", "Bearer " + cooToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shift.id").value(shift1Id));
    }

    @Test
    @DisplayName("Bàn giao két giữa ca (Cashier A -> Cashier B): giữ nguyên ca OPEN, khóa tạm POS/hoàn tiền/nạp rút cho đến khi người mới kiểm két nhận ca")
    void testMidShiftCashDrawerHandoverCashierToCashier() throws Exception {
        // Tạo Thu ngân thứ 2 cùng chi nhánh
        String cashier2Pin = "5566";
        Role cashierRole = roleRepository.findByCode(Roles.CASHIER.code()).orElseThrow();
        User cashier2 = User.builder()
                .branchId(testBranchId)
                .roleId(cashierRole.getId())
                .role(cashierRole)
                .fullName("Thu ngân 2 Shift")
                .pinHash(pinHasher.hash(cashier2Pin))
                .pinLookupHash(pinHasher.lookupHash(testBranchId, cashier2Pin))
                .position(Roles.CASHIER)
                .status(UserStatus.ACTIVE)
                .build();
        Integer cashier2Id = userRepository.save(cashier2).getId();

        // 1. Thu ngân A mở khóa bằng PIN & mở ca (tiền đầu ca = 1.000.000)
        int currentSchedId = scheduleCashierForCurrentWindow(testBranchId, cashierId);
        MvcResult pinLoginA = mockMvc.perform(post("/api/v1/auth/pin-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"employeeId\":%d,\"pin\":\"%s\"}"
                                .formatted(testBranchId, cashierId, CASHIER_PIN)))
                .andExpect(status().isOk())
                .andReturn();
        String cashierAToken = com.jayway.jsonpath.JsonPath.read(pinLoginA.getResponse().getContentAsString(), "$.data.accessToken");

        MvcResult openRes = mockMvc.perform(post("/api/v1/shifts/open")
                        .header("Authorization", "Bearer " + cashierAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"scheduledShiftId\":%d,\"openingCash\":1000000}"
                                .formatted(testBranchId, currentSchedId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.activeCashierId").value(cashierId))
                .andExpect(jsonPath("$.data.handoverPending").value(false))
                .andReturn();
        int shiftId = com.jayway.jsonpath.JsonPath.read(openRes.getResponse().getContentAsString(), "$.data.id");

        // Tạo 1 đơn đã thanh toán tiền mặt (50.000 -> expectedCash = 1.050.000) và 1 đơn chờ thanh toán (PENDING)
        MvcResult paidOrderRes = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + cashierAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"shiftId\":%d,\"totalAmount\":50000,\"finalAmount\":50000}"
                                .formatted(testBranchId, shiftId)))
                .andExpect(status().isCreated())
                .andReturn();
        int paidOrderId = com.jayway.jsonpath.JsonPath.read(paidOrderRes.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(post("/api/v1/payments/cash")
                        .header("Authorization", "Bearer " + cashierAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":%d,\"cashReceived\":50000}".formatted(paidOrderId)))
                .andExpect(status().isOk());

        MvcResult pendingOrderRes = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + cashierAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"shiftId\":%d,\"totalAmount\":30000,\"finalAmount\":30000}"
                                .formatted(testBranchId, shiftId)))
                .andExpect(status().isCreated())
                .andReturn();
        int pendingOrderId = com.jayway.jsonpath.JsonPath.read(pendingOrderRes.getResponse().getContentAsString(), "$.data.id");

        // 2. Thu ngân A khởi tạo bàn giao két giữa ca (chiều giao)
        // Sai PIN -> 401 INVALID_PIN
        mockMvc.perform(post("/api/v1/shifts/" + shiftId + "/handover/initiate")
                        .header("Authorization", "Bearer " + cashierAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"countedCash\":1050000,\"reason\":\"Việc gia đình đột xuất\",\"expectedIncomingUserId\":%d,\"pin\":\"9999\"}"
                                .formatted(cashier2Id)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_PIN"));

        // Đếm lệch (1.045.000 so với 1.050.000) nhưng thiếu note -> 422 REASON_REQUIRED
        mockMvc.perform(post("/api/v1/shifts/" + shiftId + "/handover/initiate")
                        .header("Authorization", "Bearer " + cashierAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"countedCash\":1045000,\"reason\":\"Việc gia đình đột xuất\",\"expectedIncomingUserId\":%d,\"pin\":\"%s\"}"
                                .formatted(cashier2Id, CASHIER_PIN)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detailCode").value("REASON_REQUIRED"));

        // Bàn giao hợp lệ (lệch -5.000 kèm giải trình): ca vẫn OPEN, handoverPending=true, A tự động Check-out
        mockMvc.perform(post("/api/v1/shifts/" + shiftId + "/handover/initiate")
                        .header("Authorization", "Bearer " + cashierAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "countedCash": 1045000,
                                    "reason": "Gia đình có việc đột xuất",
                                    "note": "Thiếu 5.000 đ tiền lẻ để lại quầy",
                                    "expectedIncomingUserId": %d,
                                    "pin": "%s"
                                }
                                """.formatted(cashier2Id, CASHIER_PIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.handoverPending").value(true))
                .andExpect(jsonPath("$.data.pendingHandover.fromUserId").value(cashierId))
                .andExpect(jsonPath("$.data.pendingHandover.toUserId").value(cashier2Id))
                .andExpect(jsonPath("$.data.pendingHandover.outgoingVariance").value(-5000));

        assertTrue(attendanceRepository.findFirstByUserIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(cashierId).isEmpty(),
                "Người bàn giao phải được tự động Check-out");

        // 3. Trong lúc chờ nhận két -> POS, Payment, Refund, Nạp/Rút, Đóng ca thu ngân đều bị khóa 422 SHIFT_HANDOVER_PENDING
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + cashierAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"shiftId\":%d,\"totalAmount\":25000,\"finalAmount\":25000}"
                                .formatted(testBranchId, shiftId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detailCode").value("SHIFT_HANDOVER_PENDING"));

        mockMvc.perform(post("/api/v1/payments/orders")
                        .header("Authorization", "Bearer " + cashierAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"totalAmount\":25000,\"finalAmount\":25000}"
                                .formatted(testBranchId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detailCode").value("SHIFT_HANDOVER_PENDING"));

        mockMvc.perform(post("/api/v1/payments/cash")
                        .header("Authorization", "Bearer " + cashierAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":%d,\"cashReceived\":30000}".formatted(pendingOrderId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detailCode").value("SHIFT_HANDOVER_PENDING"));

        mockMvc.perform(post("/api/v1/payments/vietqr")
                        .header("Authorization", "Bearer " + cashierAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":%d}".formatted(pendingOrderId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detailCode").value("SHIFT_HANDOVER_PENDING"));

        mockMvc.perform(post("/api/v1/refunds")
                        .header("Authorization", "Bearer " + cashierAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":%d,\"amount\":50000,\"reason\":\"Hoàn thử khi khóa két\",\"managerPin\":\"%s\"}"
                                .formatted(paidOrderId, MANAGER_PIN)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detailCode").value("SHIFT_HANDOVER_PENDING"));

        mockMvc.perform(post("/api/v1/shifts/" + shiftId + "/cash-in")
                        .header("Authorization", "Bearer " + bmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100000,\"reason\":\"Nạp khi đang bàn giao\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detailCode").value("SHIFT_HANDOVER_PENDING"));

        mockMvc.perform(post("/api/v1/shifts/" + shiftId + "/close")
                        .header("Authorization", "Bearer " + cashierAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"countedCash\":1045000,\"pin\":\"%s\"}".formatted(CASHIER_PIN)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detailCode").value("SHIFT_HANDOVER_PENDING"));

        // Chính Thu ngân A không thể tự nhận lại két của mình -> SELF_ACTION_FORBIDDEN
        mockMvc.perform(post("/api/v1/shifts/" + shiftId + "/handover/accept")
                        .header("Authorization", "Bearer " + cashierAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"countedCash\":1045000,\"pin\":\"%s\"}".formatted(CASHIER_PIN)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detailCode").value("SELF_ACTION_FORBIDDEN"));

        // 4. Thu ngân B mở khóa bằng PIN tại máy -> kiểm két nhận ca trên CHÍNH ca đang mở này
        MvcResult pinLoginB = mockMvc.perform(post("/api/v1/auth/pin-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"employeeId\":%d,\"pin\":\"%s\"}"
                                .formatted(testBranchId, cashier2Id, cashier2Pin)))
                .andExpect(status().isOk())
                .andReturn();
        String cashierBToken = com.jayway.jsonpath.JsonPath.read(pinLoginB.getResponse().getContentAsString(), "$.data.accessToken");

        // Người nhận đếm khác số bàn giao (1.040.000 != 1.045.000) nhưng thiếu note -> 422 REASON_REQUIRED
        mockMvc.perform(post("/api/v1/shifts/" + shiftId + "/handover/accept")
                        .header("Authorization", "Bearer " + cashierBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"countedCash\":1040000,\"pin\":\"%s\"}".formatted(cashier2Pin)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detailCode").value("REASON_REQUIRED"));

        // Thu ngân B đếm khớp số bàn giao 1.045.000 + nhập PIN -> mở khóa POS & tự Check-in Thu ngân B
        mockMvc.perform(post("/api/v1/shifts/" + shiftId + "/handover/accept")
                        .header("Authorization", "Bearer " + cashierBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"countedCash\":1045000,\"note\":\"Nhận đủ 1.045.000 đ theo kiểm kê thực tế\",\"pin\":\"%s\"}"
                                .formatted(cashier2Pin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(shiftId))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.handoverPending").value(false))
                .andExpect(jsonPath("$.data.activeCashierId").value(cashier2Id));

        assertTrue(attendanceRepository.findFirstByUserIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(cashier2Id).isPresent(),
                "Người nhận két phải được tự động Check-in");
        assertTrue(shiftAssignmentRepository.existsById(new ShiftAssignmentId(shiftId, cashier2Id)),
                "Người nhận két phải được tự động gán vào ca đang chạy");

        // 5. POS mở khóa trở lại trên CÙNG CA ĐANG MỞ (#shiftId) -> Thu ngân B tạo đơn 35.000 & thu tiền mặt
        MvcResult orderBRes = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + cashierBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"shiftId\":%d,\"totalAmount\":35000,\"finalAmount\":35000}"
                                .formatted(testBranchId, shiftId)))
                .andExpect(status().isCreated())
                .andReturn();
        int orderBId = com.jayway.jsonpath.JsonPath.read(orderBRes.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(post("/api/v1/payments/cash")
                        .header("Authorization", "Bearer " + cashierBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":%d,\"cashReceived\":35000}".formatted(orderBId)))
                .andExpect(status().isOk());

        // Tổng kết cuối ca: expectedCash = 1.000.000 + 50.000 + 35.000 = 1.085.000
        moveShiftIntoOnTimeCloseWindow(shiftId);
        mockMvc.perform(post("/api/v1/shifts/" + shiftId + "/close")
                        .header("Authorization", "Bearer " + cashierBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"countedCash\":1080000,\"discrepancyReason\":\"Khớp khoản thiếu 5k lúc bàn giao giữa ca\",\"pin\":\"%s\"}"
                                .formatted(cashier2Pin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.expectedCash").value(1085000))
                .andExpect(jsonPath("$.data.variance").value(-5000));

        assertTrue(attendanceRepository.findFirstByUserIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(cashier2Id).isEmpty(),
                "Thu ngân B phải tự động Check-out khi đóng ca");

        // 6. Lịch sử bàn giao két được lưu đầy đủ trong ShiftSummaryResponse
        mockMvc.perform(get("/api/v1/shifts/" + shiftId)
                        .header("Authorization", "Bearer " + bmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.handovers", hasSize(1)))
                .andExpect(jsonPath("$.data.handovers[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.handovers[0].fromUserId").value(cashierId))
                .andExpect(jsonPath("$.data.handovers[0].toUserId").value(cashier2Id))
                .andExpect(jsonPath("$.data.handovers[0].outgoingCountedCash").value(1045000))
                .andExpect(jsonPath("$.data.handovers[0].incomingCountedCash").value(1045000))
                .andExpect(jsonPath("$.data.handovers[0].incomingVarianceFromBook").value(-5000));
    }

    @Test
    @DisplayName("Hủy bàn giao, Quản lý đứng quầy thay khi thiếu Thu ngân, đóng hẳn ca và kiêm nhiệm vai trò theo từng ca (roleInShift)")
    void testHandoverCancelManagerStandInAndPerShiftRole() throws Exception {
        // 1. Thu ngân mở khóa & mở ca
        int currentSchedId = scheduleCashierForCurrentWindow(testBranchId, cashierId);
        MvcResult pinLoginA = mockMvc.perform(post("/api/v1/auth/pin-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"employeeId\":%d,\"pin\":\"%s\"}"
                                .formatted(testBranchId, cashierId, CASHIER_PIN)))
                .andExpect(status().isOk())
                .andReturn();
        String cashierAToken = com.jayway.jsonpath.JsonPath.read(pinLoginA.getResponse().getContentAsString(), "$.data.accessToken");

        MvcResult openRes = mockMvc.perform(post("/api/v1/shifts/open")
                        .header("Authorization", "Bearer " + cashierAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"scheduledShiftId\":%d,\"openingCash\":1000000}"
                                .formatted(testBranchId, currentSchedId)))
                .andExpect(status().isCreated())
                .andReturn();
        int shiftId = com.jayway.jsonpath.JsonPath.read(openRes.getResponse().getContentAsString(), "$.data.id");

        // 2. Khởi tạo rồi hủy bàn giao ngay bởi Thu ngân A -> mở khóa POS lại và tự Check-in lại
        mockMvc.perform(post("/api/v1/shifts/" + shiftId + "/handover/initiate")
                        .header("Authorization", "Bearer " + cashierAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"countedCash\":1000000,\"reason\":\"Nhờ người đổi nhưng không cần nữa\",\"pin\":\"%s\"}"
                                .formatted(CASHIER_PIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.handoverPending").value(true));

        mockMvc.perform(post("/api/v1/shifts/" + shiftId + "/handover/cancel")
                        .header("Authorization", "Bearer " + cashierAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Thu ngân A ở lại tiếp tục đứng quầy\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.handoverPending").value(false));

        assertTrue(attendanceRepository.findFirstByUserIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(cashierId).isPresent(),
                "Thu ngân hủy bàn giao để ở lại đứng quầy phải được tự Check-in lại");

        // 3. Không có thu ngân thay thế -> Thu ngân A bàn giao cho Quản lý chi nhánh đứng quầy trực tiếp
        mockMvc.perform(post("/api/v1/shifts/" + shiftId + "/handover/initiate")
                        .header("Authorization", "Bearer " + cashierAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"countedCash\":1000000,\"reason\":\"Nghỉ khẩn cấp chưa có thu ngân thay\",\"pin\":\"%s\"}"
                                .formatted(CASHIER_PIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.handoverPending").value(true));

        mockMvc.perform(post("/api/v1/shifts/" + shiftId + "/handover/accept")
                        .header("Authorization", "Bearer " + bmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"countedCash\":1000000,\"note\":\"Quản lý trực tiếp nhận két đứng quầy thay\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.handoverPending").value(false))
                .andExpect(jsonPath("$.data.activeCashierId").value(managerId));

        assertTrue(attendanceRepository.findFirstByUserIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(managerId).isEmpty(),
                "Quản lý chi nhánh nhận két đứng quầy thay không bị tạo bản ghi chấm công PIN mở");

        // Quản lý tạo & thu tiền mặt đơn POS bình thường sau khi nhận két
        MvcResult bmOrder = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + bmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"shiftId\":%d,\"totalAmount\":45000,\"finalAmount\":45000}"
                                .formatted(testBranchId, shiftId)))
                .andExpect(status().isCreated())
                .andReturn();
        int bmOrderId = com.jayway.jsonpath.JsonPath.read(bmOrder.getResponse().getContentAsString(), "$.data.id");
        mockMvc.perform(post("/api/v1/payments/cash")
                        .header("Authorization", "Bearer " + bmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":%d,\"cashReceived\":45000}".formatted(bmOrderId)))
                .andExpect(status().isOk());

        // 4. Quản lý khởi tạo bàn giao khác rồi đóng hẳn ca -> phiếu bàn giao tự chuyển CANCELLED
        mockMvc.perform(post("/api/v1/shifts/" + shiftId + "/handover/initiate")
                        .header("Authorization", "Bearer " + bmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"countedCash\":1045000,\"reason\":\"Đổi ca nhưng quyết định đóng sớm\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.handoverPending").value(true));

        mockMvc.perform(post("/api/v1/shifts/" + shiftId + "/close")
                        .header("Authorization", "Bearer " + bmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"countedCash\":1045000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.handoverPending").value(false));

        var handovers = shiftHandoverRepository.findByShiftIdOrderByCreatedAtDescIdDesc(shiftId);
        assertEquals(3, handovers.size());
        assertEquals(ShiftHandoverStatus.CANCELLED, handovers.get(0).getStatus());
        assertEquals(ShiftHandoverStatus.COMPLETED, handovers.get(1).getStatus());
        assertEquals(ShiftHandoverStatus.CANCELLED, handovers.get(2).getStatus());

        // 5. Kiêm nhiệm vai trò theo từng ca (employeeRoles -> shift_assignment.role_in_shift):
        // Phục vụ (WAITER) được gán kiêm Pha chế (BARISTA) trong ca ngày mai, vai trò gốc vẫn giữ nguyên WAITER
        Role waiterRole = roleRepository.findByCode(Roles.WAITER.code())
                .orElseGet(() -> roleRepository.save(Role.builder().code(Roles.WAITER.code()).name("Phục vụ").build()));
        Integer waiterId = userRepository.save(User.builder()
                .branchId(testBranchId)
                .roleId(waiterRole.getId())
                .role(waiterRole)
                .fullName("Phục vụ Kiêm nhiệm")
                .pinHash(pinHasher.hash("3344"))
                .pinLookupHash(pinHasher.lookupHash(testBranchId, "3344"))
                .position(Roles.WAITER)
                .status(UserStatus.ACTIVE)
                .build()).getId();

        LocalDate tomorrowVN = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(1);
        Instant start = tomorrowVN.atTime(7, 0).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        Instant end = tomorrowVN.atTime(12, 30).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();

        MvcResult schedRes = mockMvc.perform(post("/api/v1/shifts/schedules")
                        .header("Authorization", "Bearer " + bmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "branchId": %d,
                                    "shiftDate": "%s",
                                    "shiftType": "MORNING",
                                    "plannedStart": "%s",
                                    "plannedEnd": "%s",
                                    "assignedEmployeeIds": [%d, %d],
                                    "employeeRoles": {
                                        "%d": "BARISTA"
                                    }
                                }
                                """.formatted(testBranchId, tomorrowVN, start, end, waiterId, cashierId, waiterId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.employeeRoles['" + waiterId + "']").value("BARISTA"))
                .andExpect(jsonPath("$.data.employeeRoles['" + cashierId + "']").value("CASHIER"))
                .andReturn();
        int schedId = com.jayway.jsonpath.JsonPath.read(schedRes.getResponse().getContentAsString(), "$.data.shiftId");

        var assignment = shiftAssignmentRepository.findById(new ShiftAssignmentId(schedId, waiterId)).orElseThrow();
        assertEquals("BARISTA", assignment.getRoleInShift());
        assertEquals(Roles.WAITER, userRepository.findById(waiterId).orElseThrow().getPosition(),
                "Role gốc trong app_user KHÔNG bị biến đổi vĩnh viễn khi gán kiêm nhiệm theo ca");

        // Cập nhật phân công đổi sang hỗ trợ CASHIER trong ca đó
        mockMvc.perform(put("/api/v1/shifts/schedules/" + schedId)
                        .header("Authorization", "Bearer " + bmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "shiftDate": "%s",
                                    "shiftType": "MORNING",
                                    "plannedStart": "%s",
                                    "plannedEnd": "%s",
                                    "assignedEmployeeIds": [%d, %d],
                                    "employeeRoles": {
                                        "%d": "CASHIER"
                                    }
                                }
                                """.formatted(tomorrowVN, start, end, waiterId, cashierId, waiterId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.employeeRoles['" + waiterId + "']").value("CASHIER"));

        var updatedAssignment = shiftAssignmentRepository.findById(new ShiftAssignmentId(schedId, waiterId)).orElseThrow();
        assertEquals("CASHIER", updatedAssignment.getRoleInShift());
        assertFalse(shiftRepository.findById(schedId).orElseThrow().isHandoverPending());
    }

    @Test
    @DisplayName("Hồi quy: Giữ Thu ngân gốc trong /auth/cashiers, phân biệt isReplacement bàn giao có lịch vs phát sinh, và lọc Kiosk theo ca")
    void testHandoverReplacementAndKioskFilterPreservesPermanentCashiers() throws Exception {
        Role waiterRole = roleRepository.findByCode(Roles.WAITER.code())
                .orElseGet(() -> roleRepository.save(Role.builder().code(Roles.WAITER.code()).name("Phục vụ").build()));
        String waiterPin = "7788";
        Integer waiterId = userRepository.save(User.builder()
                .branchId(testBranchId)
                .roleId(waiterRole.getId())
                .role(waiterRole)
                .fullName("Phục vụ Có Lịch")
                .pinHash(pinHasher.hash(waiterPin))
                .pinLookupHash(pinHasher.lookupHash(testBranchId, waiterPin))
                .position(Roles.WAITER)
                .status(UserStatus.ACTIVE)
                .build()).getId();

        ZoneId vn = ZoneId.of("Asia/Ho_Chi_Minh");
        LocalDate todayVN = LocalDate.now(vn);
        Instant morningStart = todayVN.atTime(7, 0).atZone(vn).toInstant();
        Instant morningEnd = todayVN.atTime(12, 30).atZone(vn).toInstant();
        MvcResult schedRes = mockMvc.perform(post("/api/v1/shifts/schedules")
                        .header("Authorization", "Bearer " + bmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "branchId": %d,
                                    "shiftDate": "%s",
                                    "shiftType": "MORNING",
                                    "plannedStart": "%s",
                                    "plannedEnd": "%s",
                                    "assignedEmployeeIds": [%d, %d]
                                }
                                """.formatted(testBranchId, todayVN, morningStart, morningEnd, cashierId, waiterId)))
                .andExpect(status().isCreated())
                .andReturn();
        int shiftId = com.jayway.jsonpath.JsonPath.read(schedRes.getResponse().getContentAsString(), "$.data.shiftId");
        Shift activeWindowShift = shiftRepository.findById(shiftId).orElseThrow();
        Instant nowWindow = Instant.now();
        activeWindowShift.setPlannedStart(nowWindow.minusSeconds(1800));
        activeWindowShift.setPlannedEnd(nowWindow.plusSeconds(14400));
        shiftRepository.save(activeWindowShift);

        // Mở ca bằng PIN Thu ngân
        MvcResult pinLoginCashier = mockMvc.perform(post("/api/v1/auth/pin-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"employeeId\":%d,\"pin\":\"%s\"}".formatted(testBranchId, cashierId, CASHIER_PIN)))
                .andExpect(status().isOk())
                .andReturn();
        String cashierToken = com.jayway.jsonpath.JsonPath.read(pinLoginCashier.getResponse().getContentAsString(), "$.data.accessToken");

        mockMvc.perform(post("/api/v1/shifts/open")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"openingCash\":1000000,\"scheduledShiftId\":%d}".formatted(testBranchId, shiftId)))
                .andExpect(status().isCreated());

        // Kiosk POS lọc theo ca + excludeCashier chỉ trả waiterId (bỏ cashierId và Barista chưa gán lịch)
        mockMvc.perform(get("/api/v1/attendance/kiosk/employees?branchId=%d&shiftId=%d&onlyAssigned=true&excludeCashier=true"
                        .formatted(testBranchId, shiftId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].employeeId").value(waiterId));

        // Bàn giao #1 cho waiterId (ĐÃ có lịch trong ca): nâng roleInShift=CASHIER nhưng giữ isReplacement=false
        mockMvc.perform(post("/api/v1/shifts/" + shiftId + "/handover/initiate")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"countedCash\":1000000,\"reason\":\"Thu ngân bận\",\"expectedIncomingUserId\":%d,\"pin\":\"%s\"}"
                                .formatted(waiterId, CASHIER_PIN)))
                .andExpect(status().isOk());

        // /auth/cashiers vừa giữ cashierId gốc vừa bổ sung người được chỉ định nhận két waiterId
        mockMvc.perform(get("/api/v1/auth/cashiers?branchId=" + testBranchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].employeeId", hasItem(cashierId)))
                .andExpect(jsonPath("$.data[*].employeeId", hasItem(waiterId)));

        mockMvc.perform(post("/api/v1/shifts/" + shiftId + "/handover/accept")
                        .header("Authorization", "Bearer " + bmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"countedCash\":1000000,\"incomingUserId\":%d,\"pin\":\"%s\"}".formatted(waiterId, waiterPin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeCashierId").value(waiterId));

        var waiterAssignment = shiftAssignmentRepository.findById(new ShiftAssignmentId(shiftId, waiterId)).orElseThrow();
        assertEquals("CASHIER", waiterAssignment.getRoleInShift());
        assertFalse(Boolean.TRUE.equals(waiterAssignment.getIsReplacement()),
                "Nhân sự đã có lịch trong ca nhận bàn giao không bị đánh dấu nhầm là nhân sự thay thế phát sinh");

        // Waiter mở khóa PIN để bàn giao KHÔNG cần chọn trước expectedIncomingUserId -> BM chỉ định staffId nhận két thay
        MvcResult pinLoginWaiter = mockMvc.perform(post("/api/v1/auth/pin-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"employeeId\":%d,\"pin\":\"%s\"}".formatted(testBranchId, waiterId, waiterPin)))
                .andExpect(status().isOk())
                .andReturn();
        String waiterToken = com.jayway.jsonpath.JsonPath.read(pinLoginWaiter.getResponse().getContentAsString(), "$.data.accessToken");

        mockMvc.perform(post("/api/v1/shifts/" + shiftId + "/handover/initiate")
                        .header("Authorization", "Bearer " + waiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"countedCash\":1000000,\"reason\":\"Gọi Pha chế đứng thay\",\"pin\":\"%s\"}"
                                .formatted(waiterPin)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/shifts/" + shiftId + "/handover/accept")
                        .header("Authorization", "Bearer " + bmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"countedCash\":1000000,\"incomingUserId\":%d,\"pin\":\"1112\"}".formatted(staffId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeCashierId").value(staffId));

        var replacementAssignment = shiftAssignmentRepository.findById(new ShiftAssignmentId(shiftId, staffId)).orElseThrow();
        assertEquals("CASHIER", replacementAssignment.getRoleInShift());
        assertTrue(Boolean.TRUE.equals(replacementAssignment.getIsReplacement()),
                "Nhân sự không có lịch ca nhận bàn giao phải được đánh dấu isReplacement=true");

        // Đóng ca khi KHÔNG còn ca OPEN hay ca SCHEDULED nào đến giờ -> /auth/cashiers rỗng & pin-login bị chặn 403
        mockMvc.perform(post("/api/v1/shifts/" + shiftId + "/close")
                        .header("Authorization", "Bearer " + bmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"countedCash\":1000000}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auth/cashiers?branchId=" + testBranchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));

        mockMvc.perform(post("/api/v1/auth/pin-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"employeeId\":%d,\"pin\":\"%s\"}".formatted(testBranchId, cashierId, CASHIER_PIN)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        // Khi ca kế tiếp được xếp lịch đến khung mở ca có gán cashierId -> cashierId hiển thị lại, vẫn ẩn staffId
        scheduleCashierForCurrentWindow(testBranchId, cashierId);
        mockMvc.perform(get("/api/v1/auth/cashiers?branchId=" + testBranchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].employeeId", hasItem(cashierId)))
                .andExpect(jsonPath("$.data[*].employeeId", org.hamcrest.Matchers.not(hasItem(staffId))));
    }

    @Test
    @DisplayName("Chặn tạo/sửa lịch ở ngày đã qua và chặn kiểm két mở ca quá hạn hoặc chưa đến giờ (trước > 60 phút)")
    void testPastScheduleBlockedAndOpenShiftTimeWindowEnforced() throws Exception {
        ZoneId vn = ZoneId.of("Asia/Ho_Chi_Minh");
        LocalDate todayVN = LocalDate.now(vn);
        LocalDate yesterdayVN = todayVN.minusDays(1);
        LocalDate tomorrowVN = todayVN.plusDays(1);

        // 1. Tạo lịch cho ngày đã qua -> 422 INVALID_SHIFT_TIME
        Instant pastStart = yesterdayVN.atTime(7, 0).atZone(vn).toInstant();
        Instant pastEnd = yesterdayVN.atTime(12, 30).atZone(vn).toInstant();
        mockMvc.perform(post("/api/v1/shifts/schedules")
                        .header("Authorization", "Bearer " + bmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "branchId": %d,
                                    "shiftDate": "%s",
                                    "shiftType": "MORNING",
                                    "plannedStart": "%s",
                                    "plannedEnd": "%s",
                                    "assignedEmployeeIds": [%d]
                                }
                                """.formatted(testBranchId, yesterdayVN, pastStart, pastEnd, staffId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detailCode").value("INVALID_SHIFT_TIME"));

        // 2. Lịch SCHEDULED quá hạn từ hôm qua lưu sẵn DB -> không được sửa và không được mở ca
        Shift expiredShift = shiftRepository.save(Shift.builder()
                .branchId(testBranchId)
                .shiftDate(yesterdayVN)
                .shiftType(ShiftType.MORNING)
                .plannedStart(pastStart)
                .plannedEnd(pastEnd)
                .status(ShiftStatus.SCHEDULED)
                .build());

        mockMvc.perform(put("/api/v1/shifts/schedules/" + expiredShift.getId())
                        .header("Authorization", "Bearer " + bmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "shiftDate": "%s",
                                    "shiftType": "MORNING",
                                    "plannedStart": "%s",
                                    "plannedEnd": "%s",
                                    "assignedEmployeeIds": [%d]
                                }
                                """.formatted(yesterdayVN, pastStart, pastEnd, staffId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detailCode").value("INVALID_SHIFT_TIME"));

        mockMvc.perform(post("/api/v1/shifts/open")
                        .header("Authorization", "Bearer " + bmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"scheduledShiftId\":%d,\"openingCash\":1000000}"
                                .formatted(testBranchId, expiredShift.getId())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detailCode").value("INVALID_SHIFT_TIME"));

        // 3. Tạo lịch ngày mai -> OK; đổi ngày sửa về hôm qua -> bị chặn; chưa đến ca -> /open bị chặn nhưng /open-candidates vẫn hiện
        Instant tomorrowStart = tomorrowVN.atTime(7, 0).atZone(vn).toInstant();
        Instant tomorrowEnd = tomorrowVN.atTime(12, 30).atZone(vn).toInstant();
        MvcResult tomorrowRes = mockMvc.perform(post("/api/v1/shifts/schedules")
                        .header("Authorization", "Bearer " + bmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "branchId": %d,
                                    "shiftDate": "%s",
                                    "shiftType": "MORNING",
                                    "plannedStart": "%s",
                                    "plannedEnd": "%s",
                                    "assignedEmployeeIds": [%d]
                                }
                                """.formatted(testBranchId, tomorrowVN, tomorrowStart, tomorrowEnd, cashierId)))
                .andExpect(status().isCreated())
                .andReturn();
        int tomorrowShiftId = com.jayway.jsonpath.JsonPath.read(tomorrowRes.getResponse().getContentAsString(), "$.data.shiftId");

        mockMvc.perform(put("/api/v1/shifts/schedules/" + tomorrowShiftId)
                        .header("Authorization", "Bearer " + bmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "shiftDate": "%s",
                                    "shiftType": "MORNING",
                                    "plannedStart": "%s",
                                    "plannedEnd": "%s",
                                    "assignedEmployeeIds": [%d]
                                }
                                """.formatted(yesterdayVN, pastStart, pastEnd, cashierId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detailCode").value("INVALID_SHIFT_TIME"));

        mockMvc.perform(get("/api/v1/shifts/open-candidates?branchId=" + testBranchId)
                        .header("Authorization", "Bearer " + bmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.shiftId==" + tomorrowShiftId + ")]").exists());

        mockMvc.perform(post("/api/v1/shifts/open")
                        .header("Authorization", "Bearer " + bmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"scheduledShiftId\":%d,\"openingCash\":1000000}"
                                .formatted(testBranchId, tomorrowShiftId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detailCode").value("INVALID_SHIFT_TIME"));

        // 4. Ca bắt đầu sau 90 phút (> 60 phút grace) bị chặn; dời lại còn 20 phút trước giờ bắt đầu thì mở được
        Instant eveningTomorrowStart = tomorrowVN.atTime(12, 30).atZone(vn).toInstant();
        Instant eveningTomorrowEnd = tomorrowVN.atTime(18, 0).atZone(vn).toInstant();
        MvcResult nearFutureRes = mockMvc.perform(post("/api/v1/shifts/schedules")
                        .header("Authorization", "Bearer " + bmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "branchId": %d,
                                    "shiftDate": "%s",
                                    "shiftType": "EVENING",
                                    "plannedStart": "%s",
                                    "plannedEnd": "%s",
                                    "assignedEmployeeIds": [%d]
                                }
                                """.formatted(testBranchId, tomorrowVN, eveningTomorrowStart, eveningTomorrowEnd, cashierId)))
                .andExpect(status().isCreated())
                .andReturn();
        int nearFutureId = com.jayway.jsonpath.JsonPath.read(nearFutureRes.getResponse().getContentAsString(), "$.data.shiftId");

        Instant now = Instant.now();
        Shift savedNear = shiftRepository.findById(nearFutureId).orElseThrow();
        savedNear.setPlannedStart(now.plusSeconds(90 * 60L));
        savedNear.setPlannedEnd(now.plusSeconds(6 * 3600L));
        savedNear = shiftRepository.save(savedNear);

        mockMvc.perform(post("/api/v1/shifts/open")
                        .header("Authorization", "Bearer " + bmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"scheduledShiftId\":%d,\"openingCash\":1000000}"
                                .formatted(testBranchId, nearFutureId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detailCode").value("INVALID_SHIFT_TIME"));

        savedNear.setPlannedStart(Instant.now().plusSeconds(20 * 60L));
        shiftRepository.save(savedNear);

        mockMvc.perform(post("/api/v1/shifts/open")
                        .header("Authorization", "Bearer " + bmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"scheduledShiftId\":%d,\"openingCash\":1000000}"
                                .formatted(testBranchId, nearFutureId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("OPEN"));
    }

    @Test
    @DisplayName("Đóng ca sớm > 10 phút: gửi yêu cầu chờ Quản lý duyệt, khóa POS, hỗ trợ hủy/từ chối và duyệt chốt ca + Check-out")
    void testCashierEarlyCloseRequiresManagerApprovalAndAutoCheckOutOnApprove() throws Exception {
        // Thêm Thu ngân B vào chi nhánh để kiểm tra chặn thế chỗ PIN khi đang chờ đóng ca sớm
        int seq = SEQ.incrementAndGet();
        Role cashierRole = roleRepository.findByCode(Roles.CASHIER.code()).orElseThrow();
        String cashier2Pin = "5678";
        User cashier2 = userRepository.save(User.builder()
                .branchId(testBranchId)
                .roleId(cashierRole.getId())
                .role(cashierRole)
                .username("cashier_ec_" + seq)
                .fullName("Thu Ngân EC " + seq)
                .passwordHash(passwordEncoder.encode("Password@123"))
                .pinHash(pinHasher.hash(cashier2Pin))
                .pinLookupHash(pinHasher.lookupHash(testBranchId, cashier2Pin))
                .position(Roles.CASHIER)
                .status(UserStatus.ACTIVE)
                .build());
        int cashier2Id = cashier2.getId();

        // Quản lý ở chi nhánh khác để kiểm tra 403 CROSS_BRANCH_DENIED
        Branch otherBranch = branchRepository.save(Branch.builder()
                .code("OEC_" + seq)
                .name("Chi nhánh Khác EC " + seq)
                .openingTime(LocalTime.of(7, 0))
                .closingTime(LocalTime.of(22, 0))
                .status(BranchStatus.OPEN)
                .build());
        Role bmRole = roleRepository.findByCode(Roles.BRANCH_MANAGER.code()).orElseThrow();
        String otherBmUsername = "other_bm_ec_" + seq;
        userRepository.save(User.builder()
                .branchId(otherBranch.getId())
                .roleId(bmRole.getId())
                .role(bmRole)
                .username(otherBmUsername)
                .fullName("QL Khác " + seq)
                .passwordHash(passwordEncoder.encode("Password@123"))
                .position(Roles.BRANCH_MANAGER)
                .status(UserStatus.ACTIVE)
                .build());
        MvcResult otherBmLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"Password@123\"}".formatted(otherBmUsername)))
                .andExpect(status().isOk())
                .andReturn();
        String otherBmToken = com.jayway.jsonpath.JsonPath.read(otherBmLogin.getResponse().getContentAsString(), "$.data.accessToken");

        // Xếp ca hiện tại với plannedEnd còn > 10 phút (mặc định +4h) và cả cashierId & cashier2Id trực
        int schedId = scheduleCashierForCurrentWindow(testBranchId, cashierId);
        Shift scheduled = shiftRepository.findById(schedId).orElseThrow();
        shiftAssignmentRepository.save(ShiftAssignment.builder()
                .id(new ShiftAssignmentId(schedId, cashier2Id))
                .shift(scheduled)
                .employee(cashier2)
                .roleInShift("CASHIER")
                .build());

        MvcResult pinLoginA = mockMvc.perform(post("/api/v1/auth/pin-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"employeeId\":%d,\"pin\":\"%s\"}"
                                .formatted(testBranchId, cashierId, CASHIER_PIN)))
                .andExpect(status().isOk())
                .andReturn();
        String cashierToken = com.jayway.jsonpath.JsonPath.read(pinLoginA.getResponse().getContentAsString(), "$.data.accessToken");

        MvcResult openRes = mockMvc.perform(post("/api/v1/shifts/open")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"scheduledShiftId\":%d,\"openingCash\":1000000}"
                                .formatted(testBranchId, schedId)))
                .andExpect(status().isCreated())
                .andReturn();
        int shiftId = com.jayway.jsonpath.JsonPath.read(openRes.getResponse().getContentAsString(), "$.data.id");

        // 1. Sai PIN khi gửi đóng ca sớm -> 401 INVALID_PIN và không bật earlyClosePending
        mockMvc.perform(post("/api/v1/shifts/" + shiftId + "/close")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"countedCash\":1000000,\"pin\":\"9999\",\"earlyCloseReason\":\"Mất điện\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_PIN"));
        assertFalse(shiftRepository.findById(shiftId).orElseThrow().isEarlyClosePending());

        // 2. Lệch tiền thiếu giải trình (BR-38) -> 422 REASON_REQUIRED
        mockMvc.perform(post("/api/v1/shifts/" + shiftId + "/close")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"countedCash\":950000,\"discrepancyReason\":\"\",\"pin\":\"%s\"}".formatted(CASHIER_PIN)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detailCode").value("REASON_REQUIRED"));

        // 3. Thu ngân gửi đóng ca sớm hợp lệ -> status vẫn OPEN, earlyClosePending=true, CHƯA Check-out
        mockMvc.perform(post("/api/v1/shifts/" + shiftId + "/close")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "countedCash": 1000000,
                                    "pin": "%s",
                                    "earlyCloseReason": "Sự cố mất điện đột xuất tại cửa hàng"
                                }
                                """.formatted(CASHIER_PIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.earlyClosePending").value(true))
                .andExpect(jsonPath("$.data.earlyCloseRequestedBy").value(cashierId))
                .andExpect(jsonPath("$.data.earlyCloseCountedCash").value(1000000))
                .andExpect(jsonPath("$.data.earlyCloseReason").value("Sự cố mất điện đột xuất tại cửa hàng"));

        assertTrue(attendanceRepository.findFirstByUserIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(cashierId).isPresent(),
                "Khi đang chờ Quản lý xác nhận đóng ca sớm thì CHƯA được tự động Check-out");

        // 4. Tạo SecurityAlert EARLY_SHIFT_CLOSE_REQUEST ACTIVE gửi Quản lý chi nhánh & COO
        var activeAlerts = securityAlertRepository.findByReferenceShiftIdAndAlertTypeAndStatusIn(
                shiftId, SecurityAlertType.EARLY_SHIFT_CLOSE_REQUEST, java.util.List.of(SecurityAlertStatus.ACTIVE));
        assertEquals(1, activeAlerts.size());

        mockMvc.perform(get("/api/v1/security-alerts?branchId=" + testBranchId + "&activeOnly=true")
                        .header("Authorization", "Bearer " + bmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeCount", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.items[?(@.alertType=='EARLY_SHIFT_CLOSE_REQUEST' && @.referenceShiftId==" + shiftId + ")]").exists());

        mockMvc.perform(get("/api/v1/shifts/branches-overview")
                        .header("Authorization", "Bearer " + cooToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.branchId==" + testBranchId + " && @.earlyClosePending==true)]").exists());

        // 5. Khóa POS, khóa gửi trùng đóng ca sớm và khóa Thu ngân khác đăng nhập PIN
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"shiftId\":%d,\"totalAmount\":35000,\"finalAmount\":35000}"
                                .formatted(testBranchId, shiftId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detailCode").value("SHIFT_EARLY_CLOSE_PENDING"));

        mockMvc.perform(post("/api/v1/shifts/" + shiftId + "/close")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"countedCash\":1000000,\"pin\":\"%s\"}".formatted(CASHIER_PIN)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detailCode").value("SHIFT_EARLY_CLOSE_PENDING"));

        mockMvc.perform(post("/api/v1/auth/pin-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"employeeId\":%d,\"pin\":\"%s\"}"
                                .formatted(testBranchId, cashier2Id, cashier2Pin)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        // 6. Thu ngân tự hủy yêu cầu đóng ca sớm -> mở lại POS & alert chuyển REJECTED
        mockMvc.perform(post("/api/v1/shifts/" + shiftId + "/early-close/reject")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Có điện trở lại, tiếp tục bán hàng\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.earlyClosePending").value(false));

        assertEquals(SecurityAlertStatus.REJECTED,
                securityAlertRepository.findById(activeAlerts.get(0).getId()).orElseThrow().getStatus());

        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"shiftId\":%d,\"totalAmount\":35000,\"finalAmount\":35000}"
                                .formatted(testBranchId, shiftId)))
                .andExpect(status().isCreated());

        // 7. Gửi lại yêu cầu đóng ca sớm -> Quản lý từ chối -> mở lại ca
        mockMvc.perform(post("/api/v1/shifts/" + shiftId + "/close")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"countedCash\":995000,\"discrepancyReason\":\"Thiếu 5k tiền lẻ\",\"pin\":\"%s\"}"
                                .formatted(CASHIER_PIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.earlyClosePending").value(true));

        mockMvc.perform(post("/api/v1/shifts/" + shiftId + "/early-close/reject")
                        .header("Authorization", "Bearer " + bmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Quán đang đông khách, chưa cho đóng ca sớm\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.earlyClosePending").value(false));

        // 8. Gửi lại yêu cầu đóng ca sớm -> Quản lý chi nhánh khác bị 403; Quản lý chi nhánh này duyệt -> CLOSED + Check-out + Alert APPROVED
        mockMvc.perform(post("/api/v1/shifts/" + shiftId + "/close")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "countedCash": 1000000,
                                    "pin": "%s",
                                    "earlyCloseReason": "Chốt két sớm cuối buổi"
                                }
                                """.formatted(CASHIER_PIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.earlyClosePending").value(true));

        mockMvc.perform(post("/api/v1/shifts/" + shiftId + "/early-close/approve")
                        .header("Authorization", "Bearer " + otherBmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/shifts/" + shiftId + "/early-close/approve")
                        .header("Authorization", "Bearer " + bmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approveNote\":\"Đã kiểm két trực tiếp và xác nhận\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.earlyClosePending").value(false))
                .andExpect(jsonPath("$.data.countedCash").value(1000000));

        assertTrue(attendanceRepository.findFirstByUserIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(cashierId).isEmpty(),
                "Sau khi Quản lý duyệt đóng ca sớm, Thu ngân phải được tự động Check-out");
        var latestAtt = attendanceRepository.findAllByUserIdOrderByCheckInTimeDesc(cashierId).get(0);
        assertEquals(AttendanceStatus.COMPLETED, latestAtt.getStatus());

        var remainingActiveAlerts = securityAlertRepository.findByReferenceShiftIdAndAlertTypeAndStatusIn(
                shiftId, SecurityAlertType.EARLY_SHIFT_CLOSE_REQUEST, java.util.List.of(SecurityAlertStatus.ACTIVE));
        assertTrue(remainingActiveAlerts.isEmpty());

        mockMvc.perform(get("/api/v1/shifts/branches-overview")
                        .header("Authorization", "Bearer " + cooToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.branchId==" + testBranchId + " && @.hasOpenShift==false && @.earlyClosePending==false)]").exists());

        // Cashier vẫn poll được chi tiết ca CLOSED gần nhất để tự đăng xuất về màn PIN
        mockMvc.perform(get("/api/v1/shifts/" + shiftId)
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shift.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.transactions[?(@.type=='CLOSE')]").exists());

        // 9. Khi ca đóng hẳn và chưa đến khung mở ca mới -> màn PIN không còn thu ngân của ca đã đóng
        mockMvc.perform(get("/api/v1/auth/cashiers?branchId=" + testBranchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));

        // 10. Kiểm tra biên <= 10 phút: nếu còn <= 10 phút trước plannedEnd, Thu ngân đóng ca trực tiếp không cần chờ duyệt
        int boundarySchedId = scheduleCashierForCurrentWindow(testBranchId, cashierId);
        MvcResult pinLoginBoundary = mockMvc.perform(post("/api/v1/auth/pin-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"employeeId\":%d,\"pin\":\"%s\"}"
                                .formatted(testBranchId, cashierId, CASHIER_PIN)))
                .andExpect(status().isOk())
                .andReturn();
        String boundaryToken = com.jayway.jsonpath.JsonPath.read(pinLoginBoundary.getResponse().getContentAsString(), "$.data.accessToken");

        MvcResult openBoundaryRes = mockMvc.perform(post("/api/v1/shifts/open")
                        .header("Authorization", "Bearer " + boundaryToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"scheduledShiftId\":%d,\"openingCash\":1000000}"
                                .formatted(testBranchId, boundarySchedId)))
                .andExpect(status().isCreated())
                .andReturn();
        int boundaryShiftId = com.jayway.jsonpath.JsonPath.read(openBoundaryRes.getResponse().getContentAsString(), "$.data.id");

        Shift boundaryShift = shiftRepository.findById(boundaryShiftId).orElseThrow();
        boundaryShift.setPlannedEnd(Instant.now().plusSeconds(480)); // còn 8 phút (< 10 phút)
        shiftRepository.save(boundaryShift);

        mockMvc.perform(post("/api/v1/shifts/" + boundaryShiftId + "/close")
                        .header("Authorization", "Bearer " + boundaryToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"countedCash\":1000000,\"pin\":\"%s\"}".formatted(CASHIER_PIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.earlyClosePending").value(false));

        assertTrue(attendanceRepository.findFirstByUserIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(cashierId).isEmpty());
    }

    @Test
    @DisplayName("Bàn giao két cuối ca cho Quản lý: Check-out ngay, khóa POS/PIN, trả lại ca, duyệt kèm rút cất két & giữ nguyên chênh lệch trên CashFlow")
    void testManagerCloseHandoverImmediateCheckOutWithdrawAndVariancePreservation() throws Exception {
        int schedId = scheduleCashierForCurrentWindow(testBranchId, cashierId);

        MvcResult pinLoginRes = mockMvc.perform(post("/api/v1/auth/pin-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"employeeId\":%d,\"pin\":\"%s\"}"
                                .formatted(testBranchId, cashierId, CASHIER_PIN)))
                .andExpect(status().isOk())
                .andReturn();
        String cashierToken = com.jayway.jsonpath.JsonPath.read(pinLoginRes.getResponse().getContentAsString(), "$.data.accessToken");

        // Mở ca với 1.000.000 đ -> auto Check-in Thu ngân
        MvcResult openRes = mockMvc.perform(post("/api/v1/shifts/open")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"scheduledShiftId\":%d,\"openingCash\":1000000}"
                                .formatted(testBranchId, schedId)))
                .andExpect(status().isCreated())
                .andReturn();
        int shiftId = com.jayway.jsonpath.JsonPath.read(openRes.getResponse().getContentAsString(), "$.data.id");
        assertTrue(attendanceRepository.findFirstByUserIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(cashierId).isPresent());

        // Tạo 1 đơn tiền mặt 4.000.000 đ (expectedCash = 5.000.000 đ) + 1 đơn PENDING để test khóa thanh toán
        MvcResult paidOrderRes = mockMvc.perform(post("/api/v1/payments/orders")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"totalAmount\":4000000,\"discountAmount\":0,\"finalAmount\":4000000,\"note\":\"Đơn test 4tr\"}"
                                .formatted(testBranchId)))
                .andExpect(status().isCreated())
                .andReturn();
        int paidOrderId = com.jayway.jsonpath.JsonPath.read(paidOrderRes.getResponse().getContentAsString(), "$.data.orderId");
        mockMvc.perform(post("/api/v1/payments/cash")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":%d,\"cashReceived\":4000000}".formatted(paidOrderId)))
                .andExpect(status().isOk());

        MvcResult pendingOrderRes = mockMvc.perform(post("/api/v1/payments/orders")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"totalAmount\":50000,\"discountAmount\":0,\"finalAmount\":50000,\"note\":\"Đơn chờ thanh toán\"}"
                                .formatted(testBranchId)))
                .andExpect(status().isCreated())
                .andReturn();
        int pendingOrderId = com.jayway.jsonpath.JsonPath.read(pendingOrderRes.getResponse().getContentAsString(), "$.data.orderId");

        // 1. Gửi handoverToManager=true nhưng thiếu PIN -> 401 INVALID_PIN
        mockMvc.perform(post("/api/v1/shifts/" + shiftId + "/close")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"countedCash\":5000000,\"handoverToManager\":true}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_PIN"));

        // BM tự gửi handoverToManager=true -> bị chặn INVALID_STATE_TRANSITION
        mockMvc.perform(post("/api/v1/shifts/" + shiftId + "/close")
                        .header("Authorization", "Bearer " + bmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"countedCash\":5000000,\"handoverToManager\":true}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detailCode").value("INVALID_STATE_TRANSITION"));

        // 2. Thu ngân đóng ca & bàn giao két cho Quản lý (5.000.000 đ) -> Check-out NGAY, OPEN + managerClosePending=true
        mockMvc.perform(post("/api/v1/shifts/" + shiftId + "/close")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "countedCash": 5000000,
                                    "pin": "%s",
                                    "handoverToManager": true,
                                    "handoverReason": "Đóng ca cuối ngày, bàn giao két nhờ Quản lý cất bớt tiền"
                                }
                                """.formatted(CASHIER_PIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.managerClosePending").value(true))
                .andExpect(jsonPath("$.data.earlyClosePending").value(false))
                .andExpect(jsonPath("$.data.pendingHandover.handoverType").value("MANAGER_CLOSE"))
                .andExpect(jsonPath("$.data.pendingHandover.status").value("PENDING_RECEIVE"))
                .andExpect(jsonPath("$.data.pendingHandover.outgoingCountedCash").value(5000000));

        assertTrue(attendanceRepository.findFirstByUserIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(cashierId).isEmpty(),
                "Thu ngân phải được tự động Check-out ngay khi gửi bàn giao két cuối ca cho Quản lý");
        assertEquals(AttendanceStatus.COMPLETED, attendanceRepository.findAllByUserIdOrderByCheckInTimeDesc(cashierId).get(0).getStatus());

        // Kiểm tra SecurityAlert MANAGER_SHIFT_CLOSE_REQUEST & overview
        var mgrAlerts = securityAlertRepository.findByReferenceShiftIdAndAlertTypeAndStatusIn(
                shiftId, SecurityAlertType.MANAGER_SHIFT_CLOSE_REQUEST, java.util.List.of(SecurityAlertStatus.ACTIVE));
        assertEquals(1, mgrAlerts.size());

        mockMvc.perform(get("/api/v1/shifts/branches-overview")
                        .header("Authorization", "Bearer " + cooToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.branchId==" + testBranchId + " && @.managerClosePending==true)]").exists());

        // 3. Kiểm tra khóa POS, thanh toán, cash-in/out, handover & PIN login
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"shiftId\":%d,\"totalAmount\":35000,\"finalAmount\":35000}".formatted(testBranchId, shiftId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detailCode").value("SHIFT_MANAGER_CLOSE_PENDING"));

        mockMvc.perform(post("/api/v1/payments/cash")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":%d,\"cashReceived\":50000}".formatted(pendingOrderId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detailCode").value("SHIFT_MANAGER_CLOSE_PENDING"));

        mockMvc.perform(post("/api/v1/shifts/" + shiftId + "/cash-in")
                        .header("Authorization", "Bearer " + bmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100000,\"reason\":\"Nạp thêm\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detailCode").value("SHIFT_MANAGER_CLOSE_PENDING"));

        mockMvc.perform(post("/api/v1/shifts/" + shiftId + "/handover/initiate")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"countedCash\":5000000,\"reason\":\"Bàn giao lại\",\"pin\":\"%s\"}".formatted(CASHIER_PIN)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detailCode").value("SHIFT_MANAGER_CLOSE_PENDING"));

        mockMvc.perform(get("/api/v1/auth/cashiers?branchId=" + testBranchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));

        mockMvc.perform(post("/api/v1/auth/pin-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"employeeId\":%d,\"pin\":\"%s\"}".formatted(testBranchId, cashierId, CASHIER_PIN)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        // 4. Quản lý từ chối / trả lại ca -> mở lại POS & handover CANCELLED & alert REJECTED
        mockMvc.perform(post("/api/v1/shifts/" + shiftId + "/manager-close/reject")
                        .header("Authorization", "Bearer " + bmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Quán đón thêm đoàn khách, tiếp tục bán\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.managerClosePending").value(false));

        assertEquals(SecurityAlertStatus.REJECTED,
                securityAlertRepository.findById(mgrAlerts.get(0).getId()).orElseThrow().getStatus());

        // POS hoạt động lại
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"shiftId\":%d,\"totalAmount\":35000,\"finalAmount\":35000}".formatted(testBranchId, shiftId)))
                .andExpect(status().isCreated());

        // 5. Thu ngân gửi lại bàn giao với số thực đếm hụt -100.000 đ (4.900.000 so với 5.000.000) + giải trình
        mockMvc.perform(post("/api/v1/shifts/" + shiftId + "/close")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "countedCash": 4900000,
                                    "discrepancyReason": "Hụt 100k tiền lẻ trong ngăn kéo",
                                    "pin": "%s",
                                    "handoverToManager": true,
                                    "handoverReason": "Đóng két cuối ngày"
                                }
                                """.formatted(CASHIER_PIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.managerClosePending").value(true));

        // Quản lý kiểm đếm khác số bàn giao mà không giải trình -> 422 REASON_REQUIRED
        mockMvc.perform(post("/api/v1/shifts/" + shiftId + "/manager-close/approve")
                        .header("Authorization", "Bearer " + bmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"countedCash\":4850000}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detailCode").value("REASON_REQUIRED"));

        // Quản lý nhập rút vượt quá thực tế kiểm đếm -> 422 INSUFFICIENT_CASH
        mockMvc.perform(post("/api/v1/shifts/" + shiftId + "/manager-close/approve")
                        .header("Authorization", "Bearer " + bmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"countedCash\":4900000,\"withdrawAmount\":4950000}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detailCode").value("INSUFFICIENT_CASH"));

        // 6. Quản lý xác nhận đúng 4.900.000 đ & rút 3.000.000 đ cất két -> lưu két 1.900.000 đ, giữ nguyên variance = -100.000 đ
        mockMvc.perform(post("/api/v1/shifts/" + shiftId + "/manager-close/approve")
                        .header("Authorization", "Bearer " + bmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "countedCash": 4900000,
                                    "withdrawAmount": 3000000,
                                    "withdrawReason": "Cất két Quản lý cuối ngày",
                                    "approveNote": "Đã kiểm 4tr9, cất 3tr, giữ két 1tr9"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.managerClosePending").value(false))
                .andExpect(jsonPath("$.data.expectedCash").value(2000000))
                .andExpect(jsonPath("$.data.countedCash").value(1900000))
                .andExpect(jsonPath("$.data.variance").value(-100000));

        // Manager KHÔNG phát sinh attendance Kiosk khi xác nhận nhận két
        assertTrue(attendanceRepository.findAllByUserIdOrderByCheckInTimeDesc(managerId).isEmpty(),
                "Quản lý xác nhận nhận két cuối ca không được phát sinh bản ghi chấm công Kiosk");

        // Kiểm tra phiếu bàn giao & CashTransaction
        mockMvc.perform(get("/api/v1/shifts/" + shiftId)
                        .header("Authorization", "Bearer " + bmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.handovers[?(@.handoverType=='MANAGER_CLOSE' && @.status=='COMPLETED')].withdrawAmount",
                        hasItem(3000000.0)))
                .andExpect(jsonPath("$.data.transactions[?(@.type=='CASH_OUT')].amount", hasItem(3000000.0)))
                .andExpect(jsonPath("$.data.transactions[?(@.type=='CLOSE')].amount", hasItem(1900000.0)));

        // previous-closed trả về đúng 1.900.000 đ
        mockMvc.perform(get("/api/v1/shifts/previous-closed?branchId=" + testBranchId)
                        .header("Authorization", "Bearer " + bmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.countedCash").value(1900000))
                .andExpect(jsonPath("$.data.variance").value(-100000));

        // Đối chiếu CashFlowReport (CashFlowCalculator) phải khớp tuyệt đối variance = -100.000 đ
        mockMvc.perform(get("/api/v1/reports/cash-flow?branchId=" + testBranchId)
                        .header("Authorization", "Bearer " + bmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shifts[?(@.shiftId==" + shiftId + ")].cashOut", hasItem(3000000.0)))
                .andExpect(jsonPath("$.data.shifts[?(@.shiftId==" + shiftId + ")].expectedCash", hasItem(2000000.0)))
                .andExpect(jsonPath("$.data.shifts[?(@.shiftId==" + shiftId + ")].countedCash", hasItem(1900000.0)))
                .andExpect(jsonPath("$.data.shifts[?(@.shiftId==" + shiftId + ")].variance", hasItem(-100000.0)));
    }

    @Test
    @DisplayName("Giữa hai ca: Nạp tiền đầu ngày (kể cả trước ca đầu tiên, hỗ trợ hủy) & Rút tiền sau kết ca giữ nguyên chênh lệch")
    void testBetweenShiftPreOpenCashInAndPostCloseCashOut() throws Exception {
        int sched1Id = scheduleCashierForCurrentWindow(testBranchId, cashierId);
        MvcResult pinLoginRes = mockMvc.perform(post("/api/v1/auth/pin-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"employeeId\":%d,\"pin\":\"%s\"}"
                                .formatted(testBranchId, cashierId, CASHIER_PIN)))
                .andExpect(status().isOk())
                .andReturn();
        String cashierToken = com.jayway.jsonpath.JsonPath.read(pinLoginRes.getResponse().getContentAsString(), "$.data.accessToken");

        // 1. Thu ngân không được nạp tiền đầu ngày -> 403
        mockMvc.perform(post("/api/v1/shifts/pre-open-cash-in")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"amount\":500000,\"reason\":\"Thử nạp\"}".formatted(testBranchId)))
                .andExpect(status().isForbidden());

        // 2. Quản lý nạp 2 khoản trước khi có bất kỳ ca nào mở (500k + 300k), sau đó hủy khoản 300k
        mockMvc.perform(post("/api/v1/shifts/pre-open-cash-in")
                        .header("Authorization", "Bearer " + bmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"amount\":500000,\"reason\":\"Nạp tiền lẻ đầu ngày\"}".formatted(testBranchId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.amount").value(500000));

        MvcResult adj2Res = mockMvc.perform(post("/api/v1/shifts/pre-open-cash-in")
                        .header("Authorization", "Bearer " + bmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"amount\":300000,\"reason\":\"Nạp nhầm khoản thứ 2\"}".formatted(testBranchId)))
                .andExpect(status().isCreated())
                .andReturn();
        int adj2Id = com.jayway.jsonpath.JsonPath.read(adj2Res.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(delete("/api/v1/shifts/pre-open-cash-in/" + adj2Id + "?reason=Huy%20nhap%20nham")
                        .header("Authorization", "Bearer " + bmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cancelledAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/shifts/previous-closed?branchId=" + testBranchId)
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasPreviousShift").value(false))
                .andExpect(jsonPath("$.data.preOpenCashIn").value(500000))
                .andExpect(jsonPath("$.data.expectedOpeningCash").value(500000))
                .andExpect(jsonPath("$.data.preOpenAdjustments", hasSize(1)));

        // 3. Mở ca < tiền Quản lý đã nạp -> 400
        mockMvc.perform(post("/api/v1/shifts/open")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"scheduledShiftId\":%d,\"openingCash\":400000}"
                                .formatted(testBranchId, sched1Id)))
                .andExpect(status().isBadRequest());

        // Mở Ca #1 khớp 500.000 -> baseOpeningCash = 0, preOpenCashIn = 500.000, expectedCash = 500.000
        MvcResult s1Res = mockMvc.perform(post("/api/v1/shifts/open")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"scheduledShiftId\":%d,\"openingCash\":500000}"
                                .formatted(testBranchId, sched1Id)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.openingCash").value(0))
                .andExpect(jsonPath("$.data.preOpenCashIn").value(500000))
                .andExpect(jsonPath("$.data.expectedCash").value(500000))
                .andExpect(jsonPath("$.data.openingVariance").value(0))
                .andReturn();
        int shift1Id = com.jayway.jsonpath.JsonPath.read(s1Res.getResponse().getContentAsString(), "$.data.id");

        // Khi đang có ca mở, không cho dùng pre-open-cash-in hoặc post-close cash-out
        mockMvc.perform(post("/api/v1/shifts/pre-open-cash-in")
                        .header("Authorization", "Bearer " + bmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"amount\":100000,\"reason\":\"Đang mở ca\"}".formatted(testBranchId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detailCode").value("SHIFT_ALREADY_OPEN"));

        mockMvc.perform(post("/api/v1/shifts/previous-closed/cash-out")
                        .header("Authorization", "Bearer " + bmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"amount\":100000,\"reason\":\"Đang mở ca\"}".formatted(testBranchId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detailCode").value("SHIFT_ALREADY_OPEN"));

        // Đóng Ca #1 với doanh thu thêm +1.500k (nạp tiền trong ca) và thiếu 100k -> expected=2.000k, counted=1.900k, variance=-100k
        mockMvc.perform(post("/api/v1/shifts/" + shift1Id + "/cash-in")
                        .header("Authorization", "Bearer " + bmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1500000,\"reason\":\"Nạp dự phòng\"}"))
                .andExpect(status().isOk());

        moveShiftIntoOnTimeCloseWindow(shift1Id);
        mockMvc.perform(post("/api/v1/shifts/" + shift1Id + "/close")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"countedCash\":1900000,\"discrepancyReason\":\"Hụt 100k cuối ca 1\"}"))
                .andExpect(status().isOk());

        // 4. Sau kết ca: Rút quá số dư -> INSUFFICIENT_CASH; Thu ngân rút -> 403; BM rút 500k -> còn 1.400k, giữ nguyên variance=-100k
        mockMvc.perform(post("/api/v1/shifts/previous-closed/cash-out")
                        .header("Authorization", "Bearer " + bmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"amount\":1950000,\"reason\":\"Vượt số dư\"}".formatted(testBranchId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detailCode").value("INSUFFICIENT_CASH"));

        mockMvc.perform(post("/api/v1/shifts/previous-closed/cash-out")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"amount\":100000,\"reason\":\"Thu ngân rút\"}".formatted(testBranchId)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/shifts/previous-closed/cash-out")
                        .header("Authorization", "Bearer " + bmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"amount\":500000,\"reason\":\"Rút bớt tiền cất két sau đóng cửa\"}".formatted(testBranchId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("CASH_OUT"))
                .andExpect(jsonPath("$.data.amount").value(500000));

        // 5. Quản lý nạp thêm 600.000 đ đầu ngày mới -> Dự kiến mở Ca #2 = 1.400k + 600k = 2.000k
        mockMvc.perform(post("/api/v1/shifts/pre-open-cash-in")
                        .header("Authorization", "Bearer " + bmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"amount\":600000,\"reason\":\"Bổ sung tiền thối đầu ngày mới\"}".formatted(testBranchId)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/shifts/previous-closed?branchId=" + testBranchId)
                        .header("Authorization", "Bearer " + bmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasPreviousShift").value(true))
                .andExpect(jsonPath("$.data.countedCash").value(1400000))
                .andExpect(jsonPath("$.data.variance").value(-100000))
                .andExpect(jsonPath("$.data.preOpenCashIn").value(600000))
                .andExpect(jsonPath("$.data.expectedOpeningCash").value(2000000));

        // Mở Ca #2 lệch dự kiến (1.950k) không giải trình -> REASON_REQUIRED
        mockMvc.perform(post("/api/v1/shifts/open")
                        .header("Authorization", "Bearer " + bmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"openingCash\":1950000}".formatted(testBranchId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detailCode").value("REASON_REQUIRED"));

        // Mở Ca #2 đúng 2.000.000 đ -> baseOpeningCash=1.400.000, preOpenCashIn=600.000, expectedCash=2.000.000, openingVariance=0
        MvcResult s2Res = mockMvc.perform(post("/api/v1/shifts/open")
                        .header("Authorization", "Bearer " + bmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":%d,\"openingCash\":2000000}".formatted(testBranchId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.previousShiftId").value(shift1Id))
                .andExpect(jsonPath("$.data.previousClosingCash").value(1400000))
                .andExpect(jsonPath("$.data.openingCash").value(1400000))
                .andExpect(jsonPath("$.data.preOpenCashIn").value(600000))
                .andExpect(jsonPath("$.data.expectedCash").value(2000000))
                .andExpect(jsonPath("$.data.openingVariance").value(0))
                .andReturn();
        int shift2Id = com.jayway.jsonpath.JsonPath.read(s2Res.getResponse().getContentAsString(), "$.data.id");

        // Khoản nạp đầu ngày đã được tiêu thụ sạch trên previous-closed
        mockMvc.perform(get("/api/v1/shifts/previous-closed?branchId=" + testBranchId)
                        .header("Authorization", "Bearer " + bmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.preOpenCashIn").value(0))
                .andExpect(jsonPath("$.data.preOpenAdjustments", hasSize(0)));

        // Kiểm tra CashFlowReport không bị tính trùng cho cả 2 ca
        mockMvc.perform(get("/api/v1/reports/cash-flow?branchId=" + testBranchId)
                        .header("Authorization", "Bearer " + bmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shifts[?(@.shiftId==" + shift1Id + ")].cashIn", hasItem(2000000.0)))
                .andExpect(jsonPath("$.data.shifts[?(@.shiftId==" + shift1Id + ")].cashOut", hasItem(500000.0)))
                .andExpect(jsonPath("$.data.shifts[?(@.shiftId==" + shift1Id + ")].expectedCash", hasItem(1500000.0)))
                .andExpect(jsonPath("$.data.shifts[?(@.shiftId==" + shift1Id + ")].countedCash", hasItem(1400000.0)))
                .andExpect(jsonPath("$.data.shifts[?(@.shiftId==" + shift1Id + ")].variance", hasItem(-100000.0)))
                .andExpect(jsonPath("$.data.shifts[?(@.shiftId==" + shift2Id + ")].openingCash", hasItem(1400000.0)))
                .andExpect(jsonPath("$.data.shifts[?(@.shiftId==" + shift2Id + ")].cashIn", hasItem(600000.0)))
                .andExpect(jsonPath("$.data.shifts[?(@.shiftId==" + shift2Id + ")].expectedCash", hasItem(2000000.0)));
    }
}
