package edu.fpt.sba.scms.controller;

import edu.fpt.sba.scms.common.enums.BranchStatus;
import edu.fpt.sba.scms.common.enums.OrderStatus;
import edu.fpt.sba.scms.common.enums.Roles;
import edu.fpt.sba.scms.common.enums.UserStatus;
import edu.fpt.sba.scms.model.Branch;
import edu.fpt.sba.scms.model.Order;
import edu.fpt.sba.scms.model.Payment;
import edu.fpt.sba.scms.model.Role;
import edu.fpt.sba.scms.model.User;
import edu.fpt.sba.scms.repository.BranchRepository;
import edu.fpt.sba.scms.repository.OrderRepository;
import edu.fpt.sba.scms.repository.PaymentRepository;
import edu.fpt.sba.scms.repository.RoleRepository;
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
import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class RefundFlowIT {

    private static final AtomicInteger SEQ = new AtomicInteger(200);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PinHasher pinHasher;

    private String cooToken;
    private Integer testBranchId;
    private static final String MANAGER_PIN = "888999";

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
                .code("B_RF" + seq)
                .name("Chi nhánh Test Refund " + seq)
                .address("456 Refund Ave")
                .openingTime(LocalTime.of(7, 0))
                .closingTime(LocalTime.of(22, 0))
                .status(BranchStatus.OPEN)
                .build();
        Branch savedBranch = branchRepository.save(branch);
        this.testBranchId = savedBranch.getId();

        Role bmRole = roleRepository.findByCode(Roles.BRANCH_MANAGER.code())
                .orElseGet(() -> roleRepository.save(Role.builder().code(Roles.BRANCH_MANAGER.code()).name("Branch Manager").build()));

        String lookupHash = pinHasher.lookupHash(testBranchId, MANAGER_PIN);
        String pinHash = pinHasher.hash(MANAGER_PIN);

        User bm = User.builder()
                .branchId(testBranchId)
                .roleId(bmRole.getId())
                .role(bmRole)
                .username("bm_refund_" + seq)
                .fullName("Quản lý Refund " + seq)
                .passwordHash(passwordEncoder.encode("Password@123"))
                .pinHash(pinHash)
                .pinLookupHash(lookupHash)
                .position(Roles.BRANCH_MANAGER)
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.save(bm);

        // Mở ca làm việc tại chi nhánh trước với số dư két 1.000.000 đ (để có két tiền mà chi hoàn)
        String openPayload = """
                {
                    "branchId": %d,
                    "openingCash": 1000000,
                    "note": "Mở ca phục vụ test hoàn tiền"
                }
                """.formatted(testBranchId);
        mockMvc.perform(post("/api/v1/shifts/open")
                        .header("Authorization", "Bearer " + cooToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(openPayload))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("UC23: Luồng hoàn tiền đơn hàng đã thanh toán (PAID) và xác thực mã PIN Quản lý")
    void testRefundPaidOrderFlow() throws Exception {
        // 1. Tạo đơn hàng và thanh toán tiền mặt thành công (đơn chuyển sang PAID)
        Order order = Order.builder()
                .branchId(testBranchId)
                .totalAmount(BigDecimal.valueOf(65000))
                .discountAmount(BigDecimal.ZERO)
                .finalAmount(BigDecimal.valueOf(65000))
                .status(OrderStatus.PAID)
                .cashierId(1)
                .build();
        Order savedOrder = orderRepository.save(order);

        Payment payment = Payment.builder()
                .orderId(savedOrder.getId())
                .amount(BigDecimal.valueOf(65000))
                .receivedAmount(BigDecimal.valueOf(100000))
                .status(edu.fpt.sba.scms.common.enums.PaymentStatus.SUCCESS)
                .method(edu.fpt.sba.scms.common.enums.PaymentMethod.CASH)
                .build();
        paymentRepository.save(payment);

        // 2. Thử hoàn tiền với mã PIN sai -> Bị từ chối
        String wrongPinPayload = """
                {
                    "orderId": %d,
                    "amount": 65000,
                    "reason": "Khách phàn nàn đồ uống nguội",
                    "managerPin": "000000"
                }
                """.formatted(savedOrder.getId());

        mockMvc.perform(post("/api/v1/refunds")
                        .header("Authorization", "Bearer " + cooToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(wrongPinPayload))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detailCode").value("INVALID_MANAGER_PIN"));

        // 3. Hoàn tiền thành công với mã PIN chuẩn của Quản lý chi nhánh
        String validRefundPayload = """
                {
                    "orderId": %d,
                    "amount": 65000,
                    "reason": "Khách phàn nàn đồ uống nguội",
                    "managerPin": "%s"
                }
                """.formatted(savedOrder.getId(), MANAGER_PIN);

        mockMvc.perform(post("/api/v1/refunds")
                        .header("Authorization", "Bearer " + cooToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRefundPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DONE"))
                .andExpect(jsonPath("$.data.amount").value(65000));

        // 4. Thử hoàn tiền lại lần 2 trên cùng đơn hàng -> Bị từ chối
        mockMvc.perform(post("/api/v1/refunds")
                        .header("Authorization", "Bearer " + cooToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRefundPayload))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detailCode").value("REFUND_NOT_ALLOWED"));
    }
}
