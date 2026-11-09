package edu.fpt.sba.scms.service.support;

import edu.fpt.sba.scms.common.enums.OrderStatus;
import edu.fpt.sba.scms.dto.request.SepayWebhookRequest;
import edu.fpt.sba.scms.model.Order;
import edu.fpt.sba.scms.model.PaymentConfig;
import edu.fpt.sba.scms.repository.OrderRepository;
import edu.fpt.sba.scms.service.PaymentConfigService;
import edu.fpt.sba.scms.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;

/**
 * Cơ chế chủ động thăm dò giao dịch SePay (SePay Polling Job) phục vụ:
 * 1. Môi trường local/dev: Không cần mở tunnel (ngrok) mà BE vẫn tự động nhận diện thanh toán VietQR thật.
 * 2. Môi trường production: Cơ chế đối soát dự phòng (fallback reconciliation) nếu webhook bị mạng chặn/lỡ.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.sepay.polling.enabled", havingValue = "true", matchIfMissing = true)
public class SepayPollingJob {

    private final OrderRepository orderRepository;
    private final PaymentService paymentService;
    private final PaymentConfigService paymentConfigService;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    @Scheduled(fixedDelay = 7000, initialDelay = 10000)
    public void pollTransactions() {
        try {
            List<Order> pendingOrders = orderRepository.findByStatus(OrderStatus.PENDING);
            if (pendingOrders.isEmpty()) {
                return;
            }

            PaymentConfig config = paymentConfigService.getInternalConfig();
            if (config == null || !config.isActive() || !StringUtils.hasText(config.getApiToken())) {
                return;
            }

            String token = config.getApiToken().trim();
            // Tránh gọi khi đang dùng default sandbox dummy token chưa cấu hình
            if (token.contains("default") || token.length() < 10) {
                return;
            }

            // Gọi API tra cứu giao dịch của SePay (API v1 list: my.sepay.vn/userapi/transactions/list)
            String url = "https://my.sepay.vn/userapi/transactions/list?limit=20";
            String responseBody = restClient.get()
                    .uri(url)
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/json")
                    .retrieve()
                    .body(String.class);

            if (!StringUtils.hasText(responseBody)) {
                return;
            }

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode txList = root.path("transactions");
            if (!txList.isArray() || txList.isEmpty()) {
                txList = root.path("data");
            }
            if (!txList.isArray()) {
                return;
            }

            for (JsonNode tx : txList) {
                processSingleTransaction(tx, token);
            }
        } catch (Exception e) {
            log.debug("SePay polling skip: {}", e.getMessage());
        }
    }

    private void processSingleTransaction(JsonNode tx, String token) {
        try {
            long id = tx.path("id").asLong(0);
            if (id <= 0) {
                return;
            }

            // Kiểm tra số tiền nhận (tiền vào)
            BigDecimal amount = BigDecimal.ZERO;
            if (tx.hasNonNull("amount_in")) {
                amount = new BigDecimal(tx.path("amount_in").asText("0"));
            } else if (tx.hasNonNull("transferAmount")) {
                amount = new BigDecimal(tx.path("transferAmount").asText("0"));
            }

            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                return;
            }

            String content = tx.path("transaction_content").asText(tx.path("content").asText(""));
            String code = tx.path("code").asText(null);
            String gateway = tx.path("bank_brand_name").asText(tx.path("gateway").asText("VPBank"));
            String accNo = tx.path("account_number").asText(tx.path("accountNumber").asText(""));
            String refCode = tx.path("reference_number").asText(tx.path("referenceCode").asText(""));
            String date = tx.path("transaction_date").asText(tx.path("transactionDate").asText(""));

            SepayWebhookRequest webhookPayload = new SepayWebhookRequest(
                    id,
                    gateway,
                    date,
                    accNo,
                    null,
                    code,
                    content,
                    "in",
                    content,
                    amount,
                    refCode,
                    amount
            );

            // Tái sử dụng logic xác thực & xử lý thanh toán của PaymentService
            paymentService.processSepayWebhook(webhookPayload, "Apikey " + token, tx.toString());
        } catch (Exception e) {
            log.warn("Lỗi khi xử lý giao dịch SePay từ polling: {}", e.getMessage());
        }
    }
}
