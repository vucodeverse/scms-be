package edu.fpt.sba.scms.controller;

import edu.fpt.sba.scms.common.enums.BranchStatus;
import edu.fpt.sba.scms.common.enums.Roles;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
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
                                    "openingCash": 1000000,
                                    "note": "Thu ngân mở ca sáng"
                                }
                                """.formatted(testBranchId)))
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

        // 6. Thu ngân đóng Ca #1 khớp 1.320.000 đ
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
    @DisplayName("UC08: Tạo, cập nhật, kiểm tra trùng lịch và xóa lịch phân ca nhân viên")
    void testScheduleShiftFlow() throws Exception {
        String schedulePayload = """
                {
                    "branchId": %d,
                    "shiftDate": "2026-11-20",
                    "shiftType": "MORNING",
                    "plannedStart": "2026-11-20T00:00:00Z",
                    "plannedEnd": "2026-11-20T07:00:00Z",
                    "assignedEmployeeIds": [%d]
                }
                """.formatted(testBranchId, managerId);

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
        String conflictPayload = """
                {
                    "branchId": %d,
                    "shiftDate": "2026-11-20",
                    "shiftType": "EVENING",
                    "plannedStart": "2026-11-20T05:00:00Z",
                    "plannedEnd": "2026-11-20T12:00:00Z",
                    "assignedEmployeeIds": [%d]
                }
                """.formatted(testBranchId, managerId);
        mockMvc.perform(post("/api/v1/shifts/schedules")
                        .header("Authorization", "Bearer " + cooToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conflictPayload))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detailCode").value("SCHEDULE_CONFLICT"));

        // Cập nhật phân công ca: giữ managerId và gán thêm staffId (không tự xung đột với chính nó)
        String updatePayload = """
                {
                    "shiftDate": "2026-11-20",
                    "shiftType": "MORNING",
                    "plannedStart": "2026-11-20T00:30:00Z",
                    "plannedEnd": "2026-11-20T07:30:00Z",
                    "assignedEmployeeIds": [%d, %d]
                }
                """.formatted(managerId, staffId);
        mockMvc.perform(put("/api/v1/shifts/schedules/" + shiftId1)
                        .header("Authorization", "Bearer " + cooToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalEmployees").value(2));

        // Đổi sang chỉ còn staffId
        String updateOnlyStaffPayload = """
                {
                    "shiftDate": "2026-11-20",
                    "shiftType": "MORNING",
                    "plannedStart": "2026-11-20T00:30:00Z",
                    "plannedEnd": "2026-11-20T07:30:00Z",
                    "assignedEmployeeIds": [%d]
                }
                """.formatted(staffId);
        mockMvc.perform(put("/api/v1/shifts/schedules/" + shiftId1)
                        .header("Authorization", "Bearer " + cooToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateOnlyStaffPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalEmployees").value(1))
                .andExpect(jsonPath("$.data.assignedEmployeeIds[0]").value(staffId));

        // Tra cứu lịch ca trong khoảng ngày
        mockMvc.perform(get("/api/v1/shifts/schedules?branchId=" + testBranchId + "&startDate=2026-11-19&endDate=2026-11-21")
                        .header("Authorization", "Bearer " + cooToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));

        // Xóa ca SCHEDULED
        mockMvc.perform(delete("/api/v1/shifts/schedules/" + shiftId1)
                        .header("Authorization", "Bearer " + cooToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/shifts/schedules?branchId=" + testBranchId + "&startDate=2026-11-19&endDate=2026-11-21")
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
}
