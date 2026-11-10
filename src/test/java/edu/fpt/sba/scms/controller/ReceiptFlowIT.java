package edu.fpt.sba.scms.controller;

import edu.fpt.sba.scms.common.enums.BranchStatus;
import edu.fpt.sba.scms.common.enums.OrderStatus;
import edu.fpt.sba.scms.common.enums.PaymentMethod;
import edu.fpt.sba.scms.common.enums.PaymentStatus;
import edu.fpt.sba.scms.common.enums.ProductStatus;
import edu.fpt.sba.scms.common.enums.RecordStatus;
import edu.fpt.sba.scms.common.enums.Roles;
import edu.fpt.sba.scms.common.enums.ShiftStatus;
import edu.fpt.sba.scms.common.enums.ShiftType;
import edu.fpt.sba.scms.common.enums.UserStatus;
import edu.fpt.sba.scms.model.Branch;
import edu.fpt.sba.scms.model.Category;
import edu.fpt.sba.scms.model.Order;
import edu.fpt.sba.scms.model.OrderLine;
import edu.fpt.sba.scms.model.Payment;
import edu.fpt.sba.scms.model.Product;
import edu.fpt.sba.scms.model.Role;
import edu.fpt.sba.scms.model.Shift;
import edu.fpt.sba.scms.model.User;
import edu.fpt.sba.scms.repository.BranchRepository;
import edu.fpt.sba.scms.repository.CategoryRepository;
import edu.fpt.sba.scms.repository.OrderLineRepository;
import edu.fpt.sba.scms.repository.OrderRepository;
import edu.fpt.sba.scms.repository.PaymentRepository;
import edu.fpt.sba.scms.repository.ProductRepository;
import edu.fpt.sba.scms.repository.RoleRepository;
import edu.fpt.sba.scms.repository.ShiftRepository;
import edu.fpt.sba.scms.repository.UserRepository;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReceiptFlowIT {

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
    private ShiftRepository shiftRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderLineRepository orderLineRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Integer branchId;
    private Integer cashierId;
    private String cashierToken;
    private Product testProduct;
    private Shift testShift;

    @BeforeEach
    void setUp() throws Exception {
        int seq = SEQ.incrementAndGet();

        Role cashierRole = roleRepository.findByCode(Roles.CASHIER.code())
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .code(Roles.CASHIER.code())
                        .name("Thu ngân")
                        .build()));

        Branch branch = branchRepository.save(Branch.builder()
                .code("BR" + seq)
                .name("Chi nhánh Quận 10 - Sư Vạn Hạnh")
                .address("355 Sư Vạn Hạnh, P.12, Q.10")
                .phone("0901269955")
                .openingTime(LocalTime.of(7, 0))
                .closingTime(LocalTime.of(22, 0))
                .status(BranchStatus.OPEN)
                .build());
        branchId = branch.getId();

        String rawPass = "Pass123!";
        User cashier = userRepository.save(User.builder()
                .username("cashier" + seq)
                .fullName("Nguyễn Văn A")
                .passwordHash(passwordEncoder.encode(rawPass))
                .roleId(cashierRole.getId())
                .role(cashierRole)
                .position(Roles.CASHIER)
                .branchId(branchId)
                .branch(branch)
                .status(UserStatus.ACTIVE)
                .build());
        cashierId = cashier.getId();

        MvcResult loginRes = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + cashier.getUsername() + "\",\"password\":\"" + rawPass + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        cashierToken = com.jayway.jsonpath.JsonPath.read(loginRes.getResponse().getContentAsString(), "$.data.accessToken");

        Instant now = Instant.now();
        testShift = shiftRepository.save(Shift.builder()
                .branchId(branchId)
                .shiftDate(LocalDate.now())
                .shiftType(ShiftType.MORNING)
                .plannedStart(now.minusSeconds(1800))
                .plannedEnd(now.plusSeconds(14400))
                .status(ShiftStatus.OPEN)
                .openingCash(new BigDecimal("1000000"))
                .openedAt(now)
                .build());

        Category category = categoryRepository.save(Category.builder()
                .code("CAT" + seq)
                .name("Cà phê Test " + seq)
                .sortOrder(1)
                .status(RecordStatus.ACTIVE)
                .build());

        testProduct = productRepository.save(Product.builder()
                .categoryId(category.getId())
                .name("Cà Phê Sữa")
                .status(ProductStatus.AVAILABLE)
                .build());
    }

    @Test
    @DisplayName("UC22 & BR-31: Đơn hàng chưa thanh toán (PENDING) không được phép xem/xuất biên lai")
    void getReceipt_whenPending_throwsOrderNotPaid() throws Exception {
        Order pendingOrder = orderRepository.save(Order.builder()
                .branchId(branchId)
                .shiftId(testShift.getId())
                .cashierId(cashierId)
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("35000"))
                .discountAmount(BigDecimal.ZERO)
                .finalAmount(new BigDecimal("35000"))
                .receiptNo("ORD-" + System.currentTimeMillis())
                .build());

        mockMvc.perform(get("/api/v1/orders/" + pendingOrder.getId() + "/receipt")
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.detailCode").value("ORDER_NOT_PAID"));

        mockMvc.perform(get("/api/v1/orders/" + pendingOrder.getId() + "/receipt/export")
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.detailCode").value("ORDER_NOT_PAID"));
    }

    @Test
    @DisplayName("UC22: Đơn hàng PAID lấy biên lai thành công với đầy đủ thông tin chi nhánh, mã đơn hàng và tiền bằng chữ")
    void getReceipt_whenPaid_returnsReceiptResponse() throws Exception {
        Order paidOrder = orderRepository.save(Order.builder()
                .branchId(branchId)
                .shiftId(testShift.getId())
                .cashierId(cashierId)
                .status(OrderStatus.PAID)
                .totalAmount(new BigDecimal("70000"))
                .discountAmount(BigDecimal.ZERO)
                .finalAmount(new BigDecimal("70000"))
                .receiptNo("ORD-" + System.currentTimeMillis())
                .paidAt(Instant.now())
                .build());

        orderLineRepository.save(OrderLine.builder()
                .orderId(paidOrder.getId())
                .productId(testProduct.getId())
                .quantity(2)
                .size("L")
                .unitPrice(new BigDecimal("35000"))
                .toppingTotal(BigDecimal.ZERO)
                .lineTotal(new BigDecimal("70000"))
                .sugarLevel("50")
                .iceLevel("70")
                .build());

        paymentRepository.save(Payment.builder()
                .orderId(paidOrder.getId())
                .method(PaymentMethod.CASH)
                .amount(new BigDecimal("70000"))
                .receivedAmount(new BigDecimal("100000"))
                .status(PaymentStatus.SUCCESS)
                .build());

        mockMvc.perform(get("/api/v1/orders/" + paidOrder.getId() + "/receipt")
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(paidOrder.getId()))
                .andExpect(jsonPath("$.data.branchName").value("Chi nhánh Quận 10 - Sư Vạn Hạnh"))
                .andExpect(jsonPath("$.data.branchAddress").value("355 Sư Vạn Hạnh, P.12, Q.10"))
                .andExpect(jsonPath("$.data.branchPhone").value("0901269955"))
                .andExpect(jsonPath("$.data.cashierName").value("Nguyễn Văn A"))
                .andExpect(jsonPath("$.data.totalQuantity").value(2))
                .andExpect(jsonPath("$.data.totalAmount").value(70000))
                .andExpect(jsonPath("$.data.finalAmount").value(70000))
                .andExpect(jsonPath("$.data.paymentMethod").value("TIỀN MẶT"))
                .andExpect(jsonPath("$.data.receivedAmount").value(100000))
                .andExpect(jsonPath("$.data.changeReturned").value(30000))
                .andExpect(jsonPath("$.data.isCash").value(true))
                .andExpect(jsonPath("$.data.amountInWords").value("Bảy mươi ngàn đồng"))
                .andExpect(jsonPath("$.data.items[0].productName").value("Cà Phê Sữa"))
                .andExpect(jsonPath("$.data.items[0].size").value("L"))
                .andExpect(jsonPath("$.data.items[0].quantity").value(2));
    }

    @Test
    @DisplayName("UC22: Xuất PDF biên lai K80 tải về thành công và cập nhật receiptPrintedAt")
    void exportReceiptPdf_whenPaid_returnsPdfFile() throws Exception {
        Order paidOrder = orderRepository.save(Order.builder()
                .branchId(branchId)
                .shiftId(testShift.getId())
                .cashierId(cashierId)
                .status(OrderStatus.PAID)
                .totalAmount(new BigDecimal("35000"))
                .discountAmount(BigDecimal.ZERO)
                .finalAmount(new BigDecimal("35000"))
                .receiptNo("ORD-" + System.currentTimeMillis())
                .paidAt(Instant.now())
                .build());

        orderLineRepository.save(OrderLine.builder()
                .orderId(paidOrder.getId())
                .productId(testProduct.getId())
                .quantity(1)
                .unitPrice(new BigDecimal("35000"))
                .toppingTotal(BigDecimal.ZERO)
                .lineTotal(new BigDecimal("35000"))
                .build());

        paymentRepository.save(Payment.builder()
                .orderId(paidOrder.getId())
                .method(PaymentMethod.VIETQR)
                .amount(new BigDecimal("35000"))
                .receivedAmount(new BigDecimal("35000"))
                .status(PaymentStatus.SUCCESS)
                .build());

        MvcResult res = mockMvc.perform(get("/api/v1/orders/" + paidOrder.getId() + "/receipt/export")
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("hoa-don-#" + paidOrder.getId() + ".pdf")))
                .andReturn();

        byte[] pdfBytes = res.getResponse().getContentAsByteArray();
        assertThat(pdfBytes).isNotEmpty();
        assertThat(new String(pdfBytes, 0, 5)).isEqualTo("%PDF-");

        Order updatedOrder = orderRepository.findById(paidOrder.getId()).orElseThrow();
        assertThat(updatedOrder.getReceiptPrintedAt()).isNotNull();
    }

    @Test
    @DisplayName("UC22: Gửi lệnh in (hoặc in lại) biên lai qua máy in nhiệt cập nhật thời điểm in")
    void printReceipt_whenPaid_updatesReceiptPrintedAt() throws Exception {
        Order paidOrder = orderRepository.save(Order.builder()
                .branchId(branchId)
                .shiftId(testShift.getId())
                .cashierId(cashierId)
                .status(OrderStatus.PAID)
                .totalAmount(new BigDecimal("35000"))
                .discountAmount(BigDecimal.ZERO)
                .finalAmount(new BigDecimal("35000"))
                .receiptNo("ORD-" + System.currentTimeMillis())
                .paidAt(Instant.now())
                .build());

        mockMvc.perform(post("/api/v1/orders/" + paidOrder.getId() + "/receipt/print")
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(paidOrder.getId()))
                .andExpect(jsonPath("$.data.printedAt").isNotEmpty());

        Order updatedOrder = orderRepository.findById(paidOrder.getId()).orElseThrow();
        assertThat(updatedOrder.getReceiptPrintedAt()).isNotNull();
    }
}
