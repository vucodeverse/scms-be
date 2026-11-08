package edu.fpt.sba.scms.controller;

import edu.fpt.sba.scms.common.enums.BranchStatus;
import edu.fpt.sba.scms.common.enums.ProductSizeName;
import edu.fpt.sba.scms.common.enums.ProductStatus;
import edu.fpt.sba.scms.common.enums.RecordStatus;
import edu.fpt.sba.scms.common.enums.Roles;
import edu.fpt.sba.scms.common.enums.ShiftStatus;
import edu.fpt.sba.scms.common.enums.UserStatus;
import edu.fpt.sba.scms.model.Branch;
import edu.fpt.sba.scms.model.Category;
import edu.fpt.sba.scms.model.Product;
import edu.fpt.sba.scms.model.ProductSize;
import edu.fpt.sba.scms.model.Role;
import edu.fpt.sba.scms.model.Topping;
import edu.fpt.sba.scms.model.User;
import edu.fpt.sba.scms.repository.BranchRepository;
import edu.fpt.sba.scms.repository.CategoryRepository;
import edu.fpt.sba.scms.repository.OrderRepository;
import edu.fpt.sba.scms.repository.ProductRepository;
import edu.fpt.sba.scms.repository.ProductSizeRepository;
import edu.fpt.sba.scms.repository.RoleRepository;
import edu.fpt.sba.scms.repository.ShiftAssignmentRepository;
import edu.fpt.sba.scms.repository.ShiftRepository;
import edu.fpt.sba.scms.repository.ToppingRepository;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class EndToEndPosShiftOrderFlowIT {

    private static final AtomicInteger SEQ = new AtomicInteger(500);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductSizeRepository productSizeRepository;

    @Autowired
    private ToppingRepository toppingRepository;

    @Autowired
    private ShiftRepository shiftRepository;

    @Autowired
    private ShiftAssignmentRepository shiftAssignmentRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PinHasher pinHasher;

    private String cooToken;
    private String cashierToken;
    private Integer testBranchId;
    private Integer cashierId;
    private Integer testProductId;
    private Integer testToppingId;
    private static final String CASHIER_PASSWORD = "Password@123";
    private static final String CASHIER_PIN = "123456";

    @BeforeEach
    void setUp() throws Exception {
        int seq = SEQ.incrementAndGet();

        // 1. Đăng nhập lấy Token Quản trị viên (COO)
        String loginPayload = """
                {
                    "username": "coo",
                    "password": "Admin@123"
                }
                """;
        MvcResult cooLoginRes = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andReturn();
        this.cooToken = com.jayway.jsonpath.JsonPath.read(cooLoginRes.getResponse().getContentAsString(), "$.data.accessToken");

        // 2. Khởi tạo Chi nhánh hoạt động
        Branch branch = Branch.builder()
                .code("B" + seq)
                .name("Chi nhánh E2E POS #" + seq)
                .address("456 Duong E2E, Quan 1")
                .openingTime(LocalTime.of(7, 0))
                .closingTime(LocalTime.of(23, 0))
                .status(BranchStatus.OPEN)
                .build();
        Branch savedBranch = branchRepository.save(branch);
        this.testBranchId = savedBranch.getId();

        // 3. Khởi tạo Role CASHIER nếu chưa có
        Role cashierRole = roleRepository.findByCode(Roles.CASHIER.code())
                .orElseGet(() -> roleRepository.save(Role.builder().code(Roles.CASHIER.code()).name("Thu Ngân").build()));

        // 4. Tạo Tài khoản Nhân viên Thu ngân (Cashier) gắn với Chi nhánh
        String cashierUsername = "cashier_e2e_" + seq;
        String pinLookupHash = pinHasher.lookupHash(testBranchId, CASHIER_PIN);
        String pinHash = pinHasher.hash(CASHIER_PIN);

        User cashier = User.builder()
                .branchId(testBranchId)
                .roleId(cashierRole.getId())
                .role(cashierRole)
                .username(cashierUsername)
                .fullName("Thu Ngân E2E " + seq)
                .passwordHash(passwordEncoder.encode(CASHIER_PASSWORD))
                .pinHash(pinHash)
                .pinLookupHash(pinLookupHash)
                .position(Roles.CASHIER)
                .status(UserStatus.ACTIVE)
                .build();
        User savedCashier = userRepository.save(cashier);
        this.cashierId = savedCashier.getId();

        // Đăng nhập bằng tài khoản Thu ngân để lấy Cashier Token
        String cashierLoginPayload = """
                {
                    "username": "%s",
                    "password": "%s"
                }
                """.formatted(cashierUsername, CASHIER_PASSWORD);
        MvcResult cashierLoginRes = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cashierLoginPayload))
                .andExpect(status().isOk())
                .andReturn();
        this.cashierToken = com.jayway.jsonpath.JsonPath.read(cashierLoginRes.getResponse().getContentAsString(), "$.data.accessToken");

        // 5. Chuẩn bị Menu (Danh mục + Món + Topping)
        Category category = categoryRepository.save(Category.builder()
                .code("C" + seq)
                .name("Trà Sữa Tuyển Chọn " + seq)
                .sortOrder(1)
                .status(RecordStatus.ACTIVE)
                .build());

        Product product = productRepository.save(Product.builder()
                .categoryId(category.getId())
                .name("Trà Sữa Oolong E2E " + seq)
                .status(ProductStatus.AVAILABLE)
                .build());
        this.testProductId = product.getId();

        productSizeRepository.save(ProductSize.builder()
                .productId(testProductId)
                .size(ProductSizeName.M)
                .price(new BigDecimal("35000"))
                .build());

        Topping topping = toppingRepository.save(Topping.builder()
                .name("Trân Châu Hoàng Kim " + seq)
                .defaultPrice(new BigDecimal("10000"))
                .status(RecordStatus.ACTIVE)
                .build());
        this.testToppingId = topping.getId();
    }

    @Test
    @DisplayName("E2E Test: Luồng khép kín từ Đăng nhập tài khoản -> Lên lịch & Gán ca -> Mở ca két -> Gọi món POS -> Thanh toán tiền mặt -> Đối soát két")
    void testFullFlow_Account_Schedule_OpenShift_Order_CashPayment_Close() throws Exception {
        // =========================================================================
        // BƯỚC 1: LÊN LỊCH CA LÀM VIỆC VÀ PHÂN CÔNG NHÂN VIÊN (UC08, BR-15)
        // =========================================================================
        LocalDate today = LocalDate.now();
        Instant plannedStart = Instant.now().truncatedTo(ChronoUnit.HOURS);
        Instant plannedEnd = plannedStart.plus(8, ChronoUnit.HOURS);

        String schedulePayload = """
                {
                    "branchId": %d,
                    "shiftDate": "%s",
                    "shiftType": "MORNING",
                    "plannedStart": "%s",
                    "plannedEnd": "%s",
                    "assignedEmployeeIds": [%d]
                }
                """.formatted(testBranchId, today, plannedStart, plannedEnd, cashierId);

        MvcResult scheduleRes = mockMvc.perform(post("/api/v1/shifts/schedules")
                        .header("Authorization", "Bearer " + cooToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(schedulePayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.shiftType").value("MORNING"))
                .andExpect(jsonPath("$.data.totalEmployees").value(1))
                .andExpect(jsonPath("$.data.assignedEmployeeIds[0]").value(cashierId))
                .andReturn();

        int scheduledShiftId = com.jayway.jsonpath.JsonPath.read(scheduleRes.getResponse().getContentAsString(), "$.data.shiftId");
        assertTrue(scheduledShiftId > 0, "Ca làm việc lên lịch phải có ID > 0");

        // Kiểm tra trong database: bảng shift_assignment đã lưu đúng phân công
        var assignments = shiftAssignmentRepository.findByIdShiftId(scheduledShiftId);
        assertEquals(1, assignments.size(), "Phải có đúng 1 phân công nhân viên");
        assertEquals(cashierId, assignments.get(0).getId().getEmployeeId());

        // =========================================================================
        // BƯỚC 2: THU NGÂN VÀO CA & MỞ CA LÀM VIỆC / KHAI BÁO TIỀN ĐẦU KÉT (UC24, BR-36)
        // Thu ngân dùng chính token của mình để mở ca đã được phân công
        // =========================================================================
        BigDecimal openingCash = new BigDecimal("1000000"); // 1.000.000 VNĐ tiền lẻ đầu ca
        String openShiftPayload = """
                {
                    "branchId": %d,
                    "scheduledShiftId": %d,
                    "openingCash": 1000000,
                    "note": "Thu ngân bắt đầu ca sáng"
                }
                """.formatted(testBranchId, scheduledShiftId);

        MvcResult openRes = mockMvc.perform(post("/api/v1/shifts/open")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(openShiftPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(scheduledShiftId))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.openingCash").value(1000000))
                .andExpect(jsonPath("$.data.expectedCash").value(1000000))
                .andExpect(jsonPath("$.data.cashRevenue").value(0))
                .andExpect(jsonPath("$.data.openedBy").value(cashierId))
                .andReturn();

        // =========================================================================
        // BƯỚC 3: KHÁCH ĐẾN GỌI MÓN TẠI QUẦY POS (UC16 - CREATE ORDER)
        // Món: 2 ly Trà Sữa Oolong size M (đơn giá 35.000 đ) + Topping Trân Châu (10.000 đ)
        // Thành tiền mỗi ly = 35.000 + 10.000 = 45.000 đ
        // Tổng tiền 2 ly = 90.000 đ
        // Đơn hàng gắn với shiftId của ca đang mở và cashierId của thu ngân
        // =========================================================================
        String createOrderPayload = """
                {
                    "branchId": %d,
                    "shiftId": %d,
                    "totalAmount": 90000,
                    "discountAmount": 0,
                    "finalAmount": 90000,
                    "note": "Khách bàn 5: 70%% đường, 50%% đá",
                    "lines": [
                        {
                            "productId": %d,
                            "size": "M",
                            "quantity": 2,
                            "unitPrice": 35000,
                            "sugarLevel": "70%%",
                            "iceLevel": "50%%",
                            "note": "Nhiều trân châu",
                            "toppingIds": [%d]
                        }
                    ]
                }
                """.formatted(testBranchId, scheduledShiftId, testProductId, testToppingId);

        MvcResult createOrderRes = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createOrderPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.branchId").value(testBranchId))
                .andExpect(jsonPath("$.data.totalAmount").value(90000))
                .andExpect(jsonPath("$.data.finalAmount").value(90000))
                .andExpect(jsonPath("$.data.lines", hasSize(1)))
                .andExpect(jsonPath("$.data.lines[0].quantity").value(2))
                .andExpect(jsonPath("$.data.lines[0].lineTotal").value(90000))
                .andExpect(jsonPath("$.data.lines[0].toppings", hasSize(1)))
                .andReturn();

        int orderId = com.jayway.jsonpath.JsonPath.read(createOrderRes.getResponse().getContentAsString(), "$.data.id");
        assertTrue(orderId > 0, "Order ID phải được sinh ra tự động > 0");

        // Kiểm tra chi tiết đơn hàng qua API GET /api/v1/orders/{id}
        mockMvc.perform(get("/api/v1/orders/" + orderId)
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(orderId))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.cashierId").value(cashierId));

        // =========================================================================
        // BƯỚC 4: THU NGÂN THU TIỀN MẶT CỦA KHÁCH (UC19 - CASH PAYMENT)
        // Khách thanh toán đơn hàng 90.000 đ bằng tiền mặt, đưa 100.000 đ, thối lại 10.000 đ
        // Hệ thống tự động kích hoạt BR-27: Doanh thu tiền mặt cộng ngay vào ca đang mở!
        // =========================================================================
        String cashPaymentPayload = """
                {
                    "orderId": %d,
                    "cashReceived": 100000
                }
                """.formatted(orderId);

        mockMvc.perform(post("/api/v1/payments/cash")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cashPaymentPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.data.totalAmount").value(90000))
                .andExpect(jsonPath("$.data.cashReceived").value(100000))
                .andExpect(jsonPath("$.data.changeReturned").value(10000));

        // =========================================================================
        // BƯỚC 5: KIỂM TRA SỐ DƯ KÉT TIỀN & DOANH THU CA ĐANG MỞ (BR-27, BR-39)
        // Tiền đầu ca = 1.000.000 đ
        // Doanh thu tiền mặt từ đơn = 90.000 đ
        // Số dư két lý thuyết hiện tại = 1.000.000 + 90.000 = 1.090.000 đ
        // =========================================================================
        mockMvc.perform(get("/api/v1/shifts/current?branchId=" + testBranchId)
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.openingCash").value(1000000))
                .andExpect(jsonPath("$.data.cashRevenue").value(90000))
                .andExpect(jsonPath("$.data.currentCashBalance").value(1090000));

        // =========================================================================
        // BƯỚC 6: THU NGÂN KIỂM ĐẾM TIỀN & ĐÓNG CA (UC27, BR-38)
        // Đếm thực tế trong két = 1.090.000 đ (khớp 100% với số dư kỳ vọng)
        // =========================================================================
        String closeShiftPayload = """
                {
                    "countedCash": 1090000,
                    "discrepancyReason": ""
                }
                """;

        mockMvc.perform(post("/api/v1/shifts/" + scheduledShiftId + "/close")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(closeShiftPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.countedCash").value(1090000))
                .andExpect(jsonPath("$.data.variance").value(0))
                .andExpect(jsonPath("$.data.closedBy").value(cashierId));

        // Kiểm tra lại ca đã đóng trong database
        var closedShift = shiftRepository.findById(scheduledShiftId).orElseThrow();
        assertEquals(ShiftStatus.CLOSED, closedShift.getStatus());
        assertEquals(new BigDecimal("1090000.00"), closedShift.getCountedCash());
        assertEquals(new BigDecimal("0.00"), closedShift.getVariance());
        assertEquals(new BigDecimal("90000.00"), closedShift.getCashRevenue());
    }
}
