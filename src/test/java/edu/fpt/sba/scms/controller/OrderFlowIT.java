package edu.fpt.sba.scms.controller;

import edu.fpt.sba.scms.common.enums.DiscountType;
import edu.fpt.sba.scms.common.enums.OrderStatus;
import edu.fpt.sba.scms.common.enums.ProductSizeName;
import edu.fpt.sba.scms.common.enums.ProductStatus;
import edu.fpt.sba.scms.common.enums.RecordStatus;
import edu.fpt.sba.scms.common.enums.Roles;
import edu.fpt.sba.scms.common.enums.ShiftStatus;
import edu.fpt.sba.scms.common.enums.ShiftType;
import edu.fpt.sba.scms.common.enums.UserStatus;
import edu.fpt.sba.scms.common.util.DateTimeUtil;
import edu.fpt.sba.scms.domain.shift.ShiftSchedulePolicy;
import edu.fpt.sba.scms.model.Category;
import edu.fpt.sba.scms.model.Order;
import edu.fpt.sba.scms.model.Product;
import edu.fpt.sba.scms.model.ProductSize;
import edu.fpt.sba.scms.model.Role;
import edu.fpt.sba.scms.model.Shift;
import edu.fpt.sba.scms.model.ShiftAssignment;
import edu.fpt.sba.scms.model.ShiftAssignmentId;
import edu.fpt.sba.scms.model.Topping;
import edu.fpt.sba.scms.model.User;
import edu.fpt.sba.scms.model.Voucher;
import edu.fpt.sba.scms.model.VoucherProduct;
import edu.fpt.sba.scms.repository.CategoryRepository;
import edu.fpt.sba.scms.repository.OrderRepository;
import edu.fpt.sba.scms.repository.ProductRepository;
import edu.fpt.sba.scms.repository.ProductSizeRepository;
import edu.fpt.sba.scms.repository.RoleRepository;
import edu.fpt.sba.scms.repository.ShiftAssignmentRepository;
import edu.fpt.sba.scms.repository.ShiftRepository;
import edu.fpt.sba.scms.repository.ToppingRepository;
import edu.fpt.sba.scms.repository.UserRepository;
import edu.fpt.sba.scms.repository.VoucherProductRepository;
import edu.fpt.sba.scms.repository.VoucherRepository;
import edu.fpt.sba.scms.service.support.PinHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderFlowIT {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final AtomicInteger SEQ = new AtomicInteger(100);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductSizeRepository productSizeRepository;

    @Autowired
    private ToppingRepository toppingRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private VoucherRepository voucherRepository;

    @Autowired
    private VoucherProductRepository voucherProductRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShiftRepository shiftRepository;

    @Autowired
    private ShiftAssignmentRepository shiftAssignmentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PinHasher pinHasher;

    private int branchId;
    private int categoryId;
    private int productId;
    private int toppingId;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        token = cooToken();
        branchId = createBranch(token, "OB" + SEQ.incrementAndGet());

        Category category = categoryRepository.save(Category.builder()
                .code("CAT" + SEQ.incrementAndGet())
                .name("Trà sữa Test " + SEQ.get())
                .sortOrder(1)
                .status(RecordStatus.ACTIVE)
                .build());
        categoryId = category.getId();

        Product product = productRepository.save(Product.builder()
                .categoryId(categoryId)
                .name("Trà sữa Oolong Test " + SEQ.get())
                .status(ProductStatus.AVAILABLE)
                .build());
        productId = product.getId();

        productSizeRepository.save(ProductSize.builder()
                .productId(productId)
                .size(ProductSizeName.M)
                .price(new BigDecimal("35000"))
                .build());

        Topping topping = toppingRepository.save(Topping.builder()
                .name("Trân châu đen " + SEQ.get())
                .defaultPrice(new BigDecimal("10000"))
                .status(RecordStatus.ACTIVE)
                .build());
        toppingId = topping.getId();
    }

    private String cooToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login").contentType(JSON)
                        .content("{\"username\":\"coo\",\"password\":\"Admin@123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("accessToken")
                .asText();
    }

    private int createBranch(String token, String code) throws Exception {
        String phone = "093" + String.format("%07d", SEQ.incrementAndGet());
        MvcResult res = mockMvc.perform(post("/api/v1/branches")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content("""
                                {
                                  "code": "%s",
                                  "name": "Chi nhánh %s",
                                  "address": "1 Nguyen Trai",
                                  "phone": "%s",
                                  "email": "branch%s@scms.vn",
                                  "openingTime": "07:00",
                                  "closingTime": "22:00",
                                  "tableCount": 12,
                                  "status": "OPEN"
                                }
                                """.formatted(code, code, phone, code)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("id").asInt();
    }

    @Test
    void testCreateOrder_WithItemsAndToppings_Success() throws Exception {
        String payload = """
                {
                  "branchId": %d,
                  "totalAmount": 90000,
                  "finalAmount": 90000,
                  "note": "Khách dặn ít đá",
                  "lines": [
                    {
                      "productId": %d,
                      "size": "M",
                      "quantity": 2,
                      "unitPrice": 35000,
                      "sugarLevel": "70%%",
                      "iceLevel": "50%%",
                      "note": "Ít ngọt",
                      "toppingIds": [%d]
                    }
                  ]
                }
                """.formatted(branchId, productId, toppingId);

        MvcResult result = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content(payload))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.totalAmount").value(90000))
                .andExpect(jsonPath("$.data.finalAmount").value(90000))
                .andExpect(jsonPath("$.data.lines[0].productId").value(productId))
                .andExpect(jsonPath("$.data.lines[0].quantity").value(2))
                .andExpect(jsonPath("$.data.lines[0].toppings[0].toppingId").value(toppingId))
                .andReturn();

        int orderId = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asInt();
        assertThat(orderId).isPositive();
    }

    @Test
    void testCreateOrder_UsesAuthoritativeDbPriceWhenClientSendsTamperedPrice() throws Exception {
        String payload = """
                {
                  "branchId": %d,
                  "totalAmount": 1,
                  "finalAmount": 1,
                  "lines": [
                    {
                      "productId": %d,
                      "size": "M",
                      "quantity": 1,
                      "unitPrice": 1
                    }
                  ]
                }
                """.formatted(branchId, productId);

        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalAmount").value(35000))
                .andExpect(jsonPath("$.data.finalAmount").value(35000))
                .andExpect(jsonPath("$.data.lines[0].unitPrice").value(35000));
    }

    @Test
    void testApplyScopedVoucher_EnforcesProductAndCategoryScope() throws Exception {
        Category otherCat = categoryRepository.save(Category.builder()
                .code("OCAT" + SEQ.incrementAndGet())
                .name("Cà phê Test " + SEQ.get())
                .sortOrder(2)
                .status(RecordStatus.ACTIVE)
                .build());
        Product otherProduct = productRepository.save(Product.builder()
                .categoryId(otherCat.getId())
                .name("Espresso Test " + SEQ.get())
                .status(ProductStatus.AVAILABLE)
                .build());
        productSizeRepository.save(ProductSize.builder()
                .productId(otherProduct.getId())
                .size(ProductSizeName.M)
                .price(new BigDecimal("40000"))
                .build());

        String vCode = "SCOPED" + SEQ.incrementAndGet();
        Voucher scopedVoucher = voucherRepository.save(Voucher.builder()
                .code(vCode)
                .discountType(DiscountType.FIXED)
                .discountValue(new BigDecimal("10000"))
                .minOrderAmount(new BigDecimal("20000"))
                .startDate(Instant.now().minus(1, ChronoUnit.DAYS))
                .endDate(Instant.now().plus(7, ChronoUnit.DAYS))
                .usedCount(0)
                .status(RecordStatus.ACTIVE)
                .build());
        voucherProductRepository.save(VoucherProduct.builder()
                .voucherId(scopedVoucher.getId())
                .productId(productId)
                .build());

        // Đơn chỉ chứa otherProduct -> bị từ chối với VOUCHER_SCOPE_NOT_MET
        MvcResult nonMatchingOrder = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content("""
                                {
                                  "branchId": %d,
                                  "totalAmount": 40000,
                                  "finalAmount": 40000,
                                  "lines": [{"productId": %d, "size": "M", "quantity": 1, "unitPrice": 40000}]
                                }
                                """.formatted(branchId, otherProduct.getId())))
                .andExpect(status().isCreated())
                .andReturn();
        int nonMatchingOrderId = objectMapper.readTree(nonMatchingOrder.getResponse().getContentAsString())
                .path("data").path("id").asInt();

        mockMvc.perform(post("/api/v1/orders/" + nonMatchingOrderId + "/voucher")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content("{\"code\":\"%s\"}".formatted(vCode)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detailCode").value("VOUCHER_SCOPE_NOT_MET"));

        // Đơn chứa productId thuộc scope -> áp dụng thành công
        MvcResult matchingOrder = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content("""
                                {
                                  "branchId": %d,
                                  "totalAmount": 35000,
                                  "finalAmount": 35000,
                                  "lines": [{"productId": %d, "size": "M", "quantity": 1, "unitPrice": 35000}]
                                }
                                """.formatted(branchId, productId)))
                .andExpect(status().isCreated())
                .andReturn();
        int matchingOrderId = objectMapper.readTree(matchingOrder.getResponse().getContentAsString())
                .path("data").path("id").asInt();

        mockMvc.perform(post("/api/v1/orders/" + matchingOrderId + "/voucher")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content("{\"code\":\"%s\"}".formatted(vCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.discountAmount").value(10000))
                .andExpect(jsonPath("$.data.finalAmount").value(25000));
    }

    @Test
    void testHardDeleteProductOrToppingReferencedByOrder_BlockedWith409() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content("""
                                {
                                  "branchId": %d,
                                  "totalAmount": 35000,
                                  "finalAmount": 35000,
                                  "lines": [{"productId": %d, "size": "M", "quantity": 1, "unitPrice": 35000, "toppingIds": [%d]}]
                                }
                                """.formatted(branchId, productId, toppingId)))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/products/" + productId + "/permanent")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_CONFLICT"));

        mockMvc.perform(delete("/api/v1/toppings/" + toppingId + "/permanent")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_CONFLICT"));
    }

    @Test
    void testListAndGetOrder_Success() throws Exception {
        String payload = """
                {
                  "branchId": %d,
                  "totalAmount": 50000,
                  "finalAmount": 50000,
                  "note": "Đơn test tra cứu"
                }
                """.formatted(branchId);

        MvcResult createRes = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();

        int orderId = objectMapper.readTree(createRes.getResponse().getContentAsString())
                .path("data").path("id").asInt();

        // 1. Get by ID
        mockMvc.perform(get("/api/v1/orders/" + orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(orderId))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.totalAmount").value(50000));

        // 2. List orders
        mockMvc.perform(get("/api/v1/orders?branchId=" + branchId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void testApplyAndRemoveVoucher_Success() throws Exception {
        String vCode = "VOUCHER" + SEQ.incrementAndGet();
        voucherRepository.save(Voucher.builder()
                .code(vCode)
                .discountType(DiscountType.FIXED)
                .discountValue(new BigDecimal("15000"))
                .minOrderAmount(new BigDecimal("30000"))
                .startDate(Instant.now().minus(1, ChronoUnit.DAYS))
                .endDate(Instant.now().plus(7, ChronoUnit.DAYS))
                .usedCount(0)
                .status(RecordStatus.ACTIVE)
                .build());

        String payload = """
                {
                  "branchId": %d,
                  "totalAmount": 100000,
                  "finalAmount": 100000
                }
                """.formatted(branchId);

        MvcResult createRes = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();
        int orderId = objectMapper.readTree(createRes.getResponse().getContentAsString())
                .path("data").path("id").asInt();

        // 1. Áp dụng voucher
        mockMvc.perform(post("/api/v1/orders/" + orderId + "/voucher")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content("{\"code\": \"%s\"}".formatted(vCode)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.discountAmount").value(15000))
                .andExpect(jsonPath("$.data.finalAmount").value(85000))
                .andExpect(jsonPath("$.data.voucher.voucherCode").value(vCode));

        // 2. Gỡ bỏ voucher
        mockMvc.perform(delete("/api/v1/orders/" + orderId + "/voucher")
                        .header("Authorization", "Bearer " + token))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.discountAmount").value(0))
                .andExpect(jsonPath("$.data.finalAmount").value(100000));
    }

    @Test
    void testCancelOrder_Success_And_AlreadyPaid_Forbidden() throws Exception {
        String payload = """
                {
                  "branchId": %d,
                  "totalAmount": 60000,
                  "finalAmount": 60000
                }
                """.formatted(branchId);

        MvcResult createRes = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();
        int orderId = objectMapper.readTree(createRes.getResponse().getContentAsString())
                .path("data").path("id").asInt();

        // 1. Hủy đơn PENDING thành công (BR-26b)
        mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content("{\"cancelReason\": \"Khách đổi ý không mua nữa\"}"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.cancelReason").value("Khách đổi ý không mua nữa"));

        // 2. Không thể hủy lại đơn đã CANCELLED
        mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content("{\"cancelReason\": \"Hủy lại\"}"))
                .andExpect(status().isUnprocessableEntity());

        // 3. Đơn đã PAID không thể hủy trực tiếp (BR-26)
        Order paidOrder = orderRepository.save(Order.builder()
                .branchId(branchId)
                .status(OrderStatus.PAID)
                .totalAmount(new BigDecimal("50000"))
                .discountAmount(BigDecimal.ZERO)
                .finalAmount(new BigDecimal("50000"))
                .build());

        mockMvc.perform(post("/api/v1/orders/" + paidOrder.getId() + "/cancel")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content("{\"cancelReason\": \"Hủy đơn đã thanh toán\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void testCashierOnlySeesCurrentOpenShiftOrdersWhileManagerAndCooSeeAll() throws Exception {
        Role bmRole = roleRepository.findByCode(Roles.BRANCH_MANAGER.code())
                .orElseGet(() -> roleRepository.save(Role.builder().code(Roles.BRANCH_MANAGER.code()).name("Branch Manager").build()));
        Role cashierRole = roleRepository.findByCode(Roles.CASHIER.code())
                .orElseGet(() -> roleRepository.save(Role.builder().code(Roles.CASHIER.code()).name("Thu ngân").build()));

        int seq = SEQ.incrementAndGet();
        String bmUsername = "bm_ord_" + seq;
        userRepository.save(User.builder()
                .branchId(branchId)
                .roleId(bmRole.getId())
                .role(bmRole)
                .username(bmUsername)
                .fullName("Quản lý Order " + seq)
                .passwordHash(passwordEncoder.encode("Password@123"))
                .position(Roles.BRANCH_MANAGER)
                .status(UserStatus.ACTIVE)
                .build());

        String cashierPin = "2468";
        Integer cashierId = userRepository.save(User.builder()
                .branchId(branchId)
                .roleId(cashierRole.getId())
                .role(cashierRole)
                .fullName("Thu ngân Order " + seq)
                .pinHash(pinHasher.hash(cashierPin))
                .pinLookupHash(pinHasher.lookupHash(branchId, cashierPin))
                .position(Roles.CASHIER)
                .status(UserStatus.ACTIVE)
                .build()).getId();

        MvcResult bmLoginRes = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(JSON)
                        .content("{\"username\":\"%s\",\"password\":\"Password@123\"}".formatted(bmUsername)))
                .andExpect(status().isOk())
                .andReturn();
        String bmToken = objectMapper.readTree(bmLoginRes.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();

        Instant now = Instant.now();
        ShiftType inferred = ShiftSchedulePolicy.inferShiftType(LocalTime.now(DateTimeUtil.VN));
        Shift sched1 = shiftRepository.save(Shift.builder()
                .branchId(branchId)
                .shiftDate(DateTimeUtil.todayVN())
                .shiftType(inferred)
                .plannedStart(now.minusSeconds(1800))
                .plannedEnd(now.plusSeconds(14400))
                .status(ShiftStatus.SCHEDULED)
                .build());
        User cashierRef = userRepository.findById(cashierId).orElseThrow();
        shiftAssignmentRepository.save(ShiftAssignment.builder()
                .id(new ShiftAssignmentId(sched1.getId(), cashierId))
                .shift(sched1)
                .employee(cashierRef)
                .roleInShift("CASHIER")
                .build());

        MvcResult pinLoginRes = mockMvc.perform(post("/api/v1/auth/pin-login")
                        .contentType(JSON)
                        .content("{\"branchId\":%d,\"employeeId\":%d,\"pin\":\"%s\"}"
                                .formatted(branchId, cashierId, cashierPin)))
                .andExpect(status().isOk())
                .andReturn();
        String cashierToken = objectMapper.readTree(pinLoginRes.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();

        // 1. Mở Ca #1, tạo Đơn #1 không truyền shiftId -> BE tự gắn vào Ca #1
        MvcResult open1Res = mockMvc.perform(post("/api/v1/shifts/open")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(JSON)
                        .content("{\"branchId\":%d,\"openingCash\":1000000,\"scheduledShiftId\":%d}"
                                .formatted(branchId, sched1.getId())))
                .andExpect(status().isCreated())
                .andReturn();
        int shift1Id = objectMapper.readTree(open1Res.getResponse().getContentAsString()).path("data").path("id").asInt();

        MvcResult order1Res = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(JSON)
                        .content("""
                                {
                                  "branchId": %d,
                                  "totalAmount": 45000,
                                  "finalAmount": 45000,
                                  "note": "Đơn ca 1"
                                }
                                """.formatted(branchId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.shiftId").value(shift1Id))
                .andReturn();
        int order1Id = objectMapper.readTree(order1Res.getResponse().getContentAsString()).path("data").path("id").asInt();

        // 2. Đóng Ca #1 -> lúc chưa mở Ca #2, Thu ngân thấy danh sách rỗng và bị 403 khi mở chi tiết Đơn #1
        mockMvc.perform(post("/api/v1/shifts/" + shift1Id + "/close")
                        .header("Authorization", "Bearer " + bmToken)
                        .contentType(JSON)
                        .content("{\"countedCash\":1000000}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/orders?branchId=" + branchId)
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        mockMvc.perform(get("/api/v1/orders/" + order1Id)
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        // 3. Mở Ca #2, tạo Đơn #2 -> Thu ngân chỉ thấy Đơn #2; Quản lý và COO xem được cả 2 đơn
        MvcResult open2Res = mockMvc.perform(post("/api/v1/shifts/open")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(JSON)
                        .content("{\"branchId\":%d,\"openingCash\":1000000}".formatted(branchId)))
                .andExpect(status().isCreated())
                .andReturn();
        int shift2Id = objectMapper.readTree(open2Res.getResponse().getContentAsString()).path("data").path("id").asInt();

        MvcResult order2Res = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(JSON)
                        .content("""
                                {
                                  "branchId": %d,
                                  "shiftId": %d,
                                  "totalAmount": 85000,
                                  "finalAmount": 85000,
                                  "note": "Đơn ca 2"
                                }
                                """.formatted(branchId, shift2Id)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.shiftId").value(shift2Id))
                .andReturn();
        int order2Id = objectMapper.readTree(order2Res.getResponse().getContentAsString()).path("data").path("id").asInt();

        mockMvc.perform(get("/api/v1/orders?branchId=" + branchId)
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[*].id", hasItem(order2Id)))
                .andExpect(jsonPath("$.data.content[*].id", not(hasItem(order1Id))));

        mockMvc.perform(get("/api/v1/orders/" + order2Id)
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(order2Id));

        mockMvc.perform(get("/api/v1/orders/" + order1Id)
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(get("/api/v1/orders?branchId=" + branchId)
                        .header("Authorization", "Bearer " + bmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[*].id", hasItem(order1Id)))
                .andExpect(jsonPath("$.data.content[*].id", hasItem(order2Id)));

        mockMvc.perform(get("/api/v1/orders/" + order1Id)
                        .header("Authorization", "Bearer " + bmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(order1Id));

        mockMvc.perform(get("/api/v1/orders?branchId=" + branchId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[*].id", hasItem(order1Id)))
                .andExpect(jsonPath("$.data.content[*].id", hasItem(order2Id)));
    }
}
