package edu.fpt.sba.scms.controller;

import edu.fpt.sba.scms.common.enums.OrderStatus;
import edu.fpt.sba.scms.common.enums.PaymentStatus;
import edu.fpt.sba.scms.repository.PaymentConfigRepository;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentFlowIT {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final AtomicInteger SEQ = new AtomicInteger(500);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PaymentConfigRepository paymentConfigRepository;

    @BeforeEach
    void setUp() {
        paymentConfigRepository.deleteAll();
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
                                {"code":"%s","name":"Chi nhánh %s","address":"1 Nguyen Trai","phone":"092%07d",
                                 "email":"branch%s@scms.vn","openingTime":"07:00","closingTime":"22:00","tableCount":12,
                                 "status":"OPEN"}""".formatted(code, code, SEQ.incrementAndGet(), code)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("id").asInt();
    }

    private int createOrder(String token, int branchId, BigDecimal totalAmount) throws Exception {
        String payload = """
                {
                    "branchId": %d,
                    "orderCode": "ORD-%d",
                    "totalAmount": %s,
                    "discountAmount": 0,
                    "finalAmount": %s
                }
                """.formatted(branchId, SEQ.incrementAndGet(), totalAmount, totalAmount);

        MvcResult res = mockMvc.perform(post("/api/v1/payments/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("orderId").asInt();
    }

    @Test
    void testEndToEndPayment_VietQrAndSepayWebhook() throws Exception {
        String token = cooToken();
        int branchId = createBranch(token, "BR" + SEQ.incrementAndGet());
        BigDecimal amount = new BigDecimal("65000.00");

        // BƯỚC 1: Tạo đơn hàng tại quầy POS
        int orderId = createOrder(token, branchId, amount);
        assertThat(orderId).isPositive();

        // BƯỚC 2: Sinh mã VietQR động theo đơn hàng (UC43)
        String qrPayload = """
                {
                    "orderId": %d
                }
                """.formatted(orderId);

        MvcResult qrResult = mockMvc.perform(post("/api/v1/payments/vietqr")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content(qrPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(orderId))
                .andExpect(jsonPath("$.data.referenceCode").value("CAFE" + orderId))
                .andExpect(jsonPath("$.data.amount").value(65000))
                .andExpect(jsonPath("$.data.qrImageUrl").value(org.hamcrest.Matchers.startsWith("https://img.vietqr.io/image/")))
                .andReturn();

        // BƯỚC 3: Khách hàng quét mã chuyển khoản thành công, SePay gửi Webhook (UC44, UC20)
        long webhookTransId = 888000 + orderId;
        String sepayPayload = """
                {
                    "id": %d,
                    "gateway": "MBBank",
                    "transactionDate": "2026-11-06 14:00:00",
                    "accountNumber": "0987654321",
                    "code": null,
                    "content": "Thanh toan don hang CAFE%d qua VietQR",
                    "transferType": "in",
                    "transferAmount": 65000,
                    "accumulated": 5000000,
                    "referenceCode": "FT261106%d",
                    "description": "MBBank chuyen tien"
                }
                """.formatted(webhookTransId, orderId, orderId);

        // SePay webhook gửi kèm Bearer Token của SePay cấu hình
        mockMvc.perform(post("/api/v1/webhooks/sepay")
                        .header("Authorization", "Bearer sepay_sandbox_token_default_123456")
                        .contentType(JSON)
                        .content(sepayPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(orderId))
                .andExpect(jsonPath("$.data.orderStatus").value(OrderStatus.PAID.name()))
                .andExpect(jsonPath("$.data.paymentStatus").value(PaymentStatus.SUCCESS.name()))
                .andExpect(jsonPath("$.data.receivedAmount").value(65000));

        // BƯỚC 4: Màn hình Thu ngân POS polling kiểm tra trạng thái đơn hàng (UC20)
        mockMvc.perform(get("/api/v1/payments/orders/" + orderId + "/status")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(orderId))
                .andExpect(jsonPath("$.data.orderStatus").value(OrderStatus.PAID.name()))
                .andExpect(jsonPath("$.data.paymentStatus").value(PaymentStatus.SUCCESS.name()))
                .andExpect(jsonPath("$.data.sepayRef").value(String.valueOf(webhookTransId)));

        // BƯỚC 5: Kiểm tra chống xử lý trùng lặp (Anti-duplicate - UC44 AT2)
        // SePay gửi lại cùng webhookTransId
        mockMvc.perform(post("/api/v1/webhooks/sepay")
                        .header("Authorization", "Bearer sepay_sandbox_token_default_123456")
                        .contentType(JSON)
                        .content(sepayPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value(org.hamcrest.Matchers.containsString("trùng lặp")));
    }

    @Test
    void testSepayWebhook_AmountMismatch() throws Exception {
        String token = cooToken();
        int branchId = createBranch(token, "BR" + SEQ.incrementAndGet());
        BigDecimal totalAmount = new BigDecimal("100000.00");

        int orderId = createOrder(token, branchId, totalAmount);

        // Khách hàng chuyển thiếu: đơn 100,000 nhưng chuyển 80,000
        long webhookTransId = 999000 + orderId;
        String mismatchPayload = """
                {
                    "id": %d,
                    "gateway": "MBBank",
                    "transactionDate": "2026-11-06 14:15:00",
                    "accountNumber": "0987654321",
                    "code": null,
                    "content": "Thanh toan CAFE%d",
                    "transferType": "in",
                    "transferAmount": 80000,
                    "accumulated": 5080000,
                    "referenceCode": "FT261106%d",
                    "description": "MBBank chuyen tien"
                }
                """.formatted(webhookTransId, orderId, orderId);

        mockMvc.perform(post("/api/v1/webhooks/sepay")
                        .header("Authorization", "Bearer sepay_sandbox_token_default_123456")
                        .contentType(JSON)
                        .content(mismatchPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(orderId))
                .andExpect(jsonPath("$.data.orderStatus").value(OrderStatus.PENDING.name()))
                .andExpect(jsonPath("$.data.paymentStatus").value(PaymentStatus.MISMATCH.name()));

        // Kiểm tra polling POS vẫn thấy đơn PENDING và thanh toán MISMATCH
        mockMvc.perform(get("/api/v1/payments/orders/" + orderId + "/status")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value(OrderStatus.PENDING.name()))
                .andExpect(jsonPath("$.data.paymentStatus").value(PaymentStatus.MISMATCH.name()));
    }

    @Test
    void testSepayWebhook_InvalidToken_ThrowsUnauthorized() throws Exception {
        String invalidPayload = """
                {
                    "id": 112233,
                    "gateway": "MBBank",
                    "transactionDate": "2026-11-06 14:20:00",
                    "accountNumber": "0987654321",
                    "content": "CAFE999",
                    "transferType": "in",
                    "transferAmount": 50000
                }
                """;

        // Gửi token sai
        mockMvc.perform(post("/api/v1/webhooks/sepay")
                        .header("Authorization", "Bearer wrong_secret_token")
                        .contentType(JSON)
                        .content(invalidPayload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));
    }

    // =========================================================================
    // UC19: Process Cash Payment (Thanh toán tiền mặt tại quầy POS)
    // =========================================================================

    @Test
    void testCashPayment_Success() throws Exception {
        String token = cooToken();
        int branchId = createBranch(token, "BR" + SEQ.incrementAndGet());
        BigDecimal totalAmount = new BigDecimal("65000.00");
        int orderId = createOrder(token, branchId, totalAmount);

        // Khách đưa 100,000 đ cho đơn 65,000 đ -> Tiền thối 35,000 đ
        String cashPayload = """
                {
                    "orderId": %d,
                    "cashReceived": 100000,
                    "notes": "Khách trả tiền mặt 100k"
                }
                """.formatted(orderId);

        mockMvc.perform(post("/api/v1/payments/cash")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content(cashPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(orderId))
                .andExpect(jsonPath("$.data.orderStatus").value("PAID"))
                .andExpect(jsonPath("$.data.paymentStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.data.paymentMethod").value("CASH"))
                .andExpect(jsonPath("$.data.totalAmount").value(65000))
                .andExpect(jsonPath("$.data.cashReceived").value(100000))
                .andExpect(jsonPath("$.data.changeReturned").value(35000));

        // Kiểm tra tra cứu trạng thái đơn hàng (UC20) xác nhận đã PAID
        mockMvc.perform(get("/api/v1/payments/orders/" + orderId + "/status")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("PAID"))
                .andExpect(jsonPath("$.data.paymentStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.data.paymentMethod").value("CASH"));
    }

    @Test
    void testCashPayment_InsufficientCash_ThrowsBusinessRuleException() throws Exception {
        String token = cooToken();
        int branchId = createBranch(token, "BR" + SEQ.incrementAndGet());
        BigDecimal totalAmount = new BigDecimal("65000.00");
        int orderId = createOrder(token, branchId, totalAmount);

        // Khách đưa 50,000 đ nhưng đơn là 65,000 đ -> Lỗi CASH_RECEIVED_LT_TOTAL (AT1)
        String cashPayload = """
                {
                    "orderId": %d,
                    "cashReceived": 50000
                }
                """.formatted(orderId);

        mockMvc.perform(post("/api/v1/payments/cash")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content(cashPayload))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.detailCode").value("CASH_RECEIVED_LT_TOTAL"));
    }

    @Test
    void testCashPayment_OrderAlreadyPaid_ThrowsBusinessRuleException() throws Exception {
        String token = cooToken();
        int branchId = createBranch(token, "BR" + SEQ.incrementAndGet());
        BigDecimal totalAmount = new BigDecimal("50000.00");
        int orderId = createOrder(token, branchId, totalAmount);

        String cashPayload = """
                {
                    "orderId": %d,
                    "cashReceived": 50000
                }
                """.formatted(orderId);

        // Thanh toán lần 1 thành công
        mockMvc.perform(post("/api/v1/payments/cash")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content(cashPayload))
                .andExpect(status().isOk());

        // Thanh toán lần 2 trên đơn đã PAID -> ORDER_FROZEN (BR-26)
        mockMvc.perform(post("/api/v1/payments/cash")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content(cashPayload))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detailCode").value("ORDER_FROZEN"));
    }
}
