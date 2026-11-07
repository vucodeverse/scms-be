package edu.fpt.sba.scms.controller;

import edu.fpt.sba.scms.common.enums.DiscountType;
import edu.fpt.sba.scms.common.enums.OrderStatus;
import edu.fpt.sba.scms.common.enums.ProductSizeName;
import edu.fpt.sba.scms.common.enums.ProductStatus;
import edu.fpt.sba.scms.common.enums.RecordStatus;
import edu.fpt.sba.scms.model.Category;
import edu.fpt.sba.scms.model.Order;
import edu.fpt.sba.scms.model.Product;
import edu.fpt.sba.scms.model.ProductSize;
import edu.fpt.sba.scms.model.Topping;
import edu.fpt.sba.scms.model.Voucher;
import edu.fpt.sba.scms.repository.CategoryRepository;
import edu.fpt.sba.scms.repository.OrderRepository;
import edu.fpt.sba.scms.repository.ProductRepository;
import edu.fpt.sba.scms.repository.ProductSizeRepository;
import edu.fpt.sba.scms.repository.ToppingRepository;
import edu.fpt.sba.scms.repository.VoucherRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
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
    private OrderRepository orderRepository;

    private int branchId;
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

        Product product = productRepository.save(Product.builder()
                .categoryId(category.getId())
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
        MvcResult res = mockMvc.perform(post("/api/v1/branches")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content("""
                                {
                                  "code": "%s",
                                  "name": "Chi nhánh %s",
                                  "address": "1 Nguyen Trai",
                                  "phone": "0901234567",
                                  "email": "branch%s@scms.vn",
                                  "openingTime": "07:00",
                                  "closingTime": "22:00",
                                  "tableCount": 12,
                                  "status": "OPEN"
                                }
                                """.formatted(code, code, code)))
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
}
