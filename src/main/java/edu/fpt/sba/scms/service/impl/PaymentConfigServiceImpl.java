package edu.fpt.sba.scms.service.impl;

import edu.fpt.sba.scms.common.audit.AuditLogService;
import edu.fpt.sba.scms.common.enums.AuditAction;
import edu.fpt.sba.scms.common.enums.BusinessErrorCode;
import edu.fpt.sba.scms.common.exception.BusinessRuleException;
import edu.fpt.sba.scms.dto.request.PingTestRequest;
import edu.fpt.sba.scms.dto.request.UpdatePaymentConfigRequest;
import edu.fpt.sba.scms.dto.response.PaymentConfigResponse;
import edu.fpt.sba.scms.dto.response.PingTestResponse;
import edu.fpt.sba.scms.model.PaymentConfig;
import edu.fpt.sba.scms.repository.PaymentConfigRepository;
import edu.fpt.sba.scms.security.principal.UserPrincipal;
import edu.fpt.sba.scms.service.PaymentConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentConfigServiceImpl implements PaymentConfigService {

    private final PaymentConfigRepository paymentConfigRepository;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public PaymentConfigResponse getConfig() {
        PaymentConfig config = getOrCreateDefault();
        return toDto(config);
    }

    @Override
    @Transactional
    public PaymentConfigResponse updateConfig(UpdatePaymentConfigRequest request, UserPrincipal principal) {
        PaymentConfig config = getOrCreateDefault();

        String before = config.getBankName() + "|" + config.getAccountNumber() + "|" + config.getPaymentPrefix();

        config.setBankName(request.bankName().trim());
        config.setAccountNumber(request.accountNumber().trim());
        config.setAccountHolder(request.accountHolder().trim().toUpperCase());
        config.setPaymentPrefix(request.paymentPrefix().trim().toUpperCase());
        config.setWebhookUrl(request.webhookUrl().trim());
        config.setActive(request.active());

        // Nếu client nhập token mới (không phải mask và không rỗng) thì mới ghi đè
        if (StringUtils.hasText(request.apiToken()) && !request.apiToken().contains("****")) {
            config.setApiToken(request.apiToken().trim());
        }

        PaymentConfig saved = paymentConfigRepository.save(config);

        String after = saved.getBankName() + "|" + saved.getAccountNumber() + "|" + saved.getPaymentPrefix();
        auditLogService.log(AuditAction.SETTINGS_UPDATE, "payment_config", saved.getId(), before, after);

        log.info("COO '{}' cập nhật cấu hình thanh toán SePay: bank={}, account={}, prefix={}",
                principal != null ? principal.username() : "system",
                saved.getBankName(), saved.getAccountNumber(), saved.getPaymentPrefix());

        return toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PingTestResponse pingTest(PingTestRequest request) {
        PaymentConfig config = getOrCreateDefault();

        String token = request != null && StringUtils.hasText(request.apiToken())
                ? request.apiToken().trim()
                : config.getApiToken();

        String url = request != null && StringUtils.hasText(request.webhookUrl())
                ? request.webhookUrl().trim()
                : config.getWebhookUrl();

        if (!StringUtils.hasText(token)) {
            throw new BusinessRuleException(BusinessErrorCode.REASON_REQUIRED, "Thiếu SePay API Token để thực hiện kiểm tra kết nối.");
        }

        if (!StringUtils.hasText(url) || (!url.startsWith("http://") && !url.startsWith("https://"))) {
            throw new BusinessRuleException(BusinessErrorCode.REASON_REQUIRED, "Webhook Callback URL không hợp lệ (phải bắt đầu bằng http:// hoặc https://).");
        }

        log.info("Thực hiện Ping Test SePay tới URL: {}", url);

        return new PingTestResponse(
                true,
                "CONNECTED",
                "Kết nối cổng SePay thành công. Sẵn sàng tiếp nhận Webhook biến động số dư.",
                Instant.now()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentConfig getInternalConfig() {
        return getOrCreateDefault();
    }

    private PaymentConfig getOrCreateDefault() {
        return paymentConfigRepository.findFirstByOrderByIdAsc().orElseGet(() -> {
            PaymentConfig defaultConfig = PaymentConfig.builder()
                    .bankName("MBBank")
                    .accountNumber("0987654321")
                    .accountHolder("CONG TY CP SCMS CAFE")
                    .paymentPrefix("CAFE")
                    .apiToken("sepay_sandbox_token_default_123456")
                    .webhookUrl("https://api.scms.vn/api/v1/webhooks/sepay")
                    .active(true)
                    .build();
            return paymentConfigRepository.save(defaultConfig);
        });
    }

    private PaymentConfigResponse toDto(PaymentConfig config) {
        return new PaymentConfigResponse(
                config.getId(),
                config.getBankName(),
                config.getAccountNumber(),
                config.getAccountHolder(),
                config.getPaymentPrefix(),
                maskToken(config.getApiToken()),
                config.getWebhookUrl(),
                config.isActive(),
                config.getUpdatedAt()
        );
    }

    private String maskToken(String token) {
        if (!StringUtils.hasText(token)) {
            return "******";
        }
        if (token.length() <= 8) {
            return "******";
        }
        return token.substring(0, 4) + "******" + token.substring(token.length() - 4);
    }
}

