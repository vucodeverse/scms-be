package edu.fpt.sba.scms.service.impl;

import edu.fpt.sba.scms.common.audit.AuditLogService;
import edu.fpt.sba.scms.common.enums.AuditAction;
import edu.fpt.sba.scms.common.enums.BusinessErrorCode;
import edu.fpt.sba.scms.common.enums.ErrorCode;
import edu.fpt.sba.scms.common.enums.OrderStatus;
import edu.fpt.sba.scms.common.enums.PaymentMethod;
import edu.fpt.sba.scms.common.enums.PaymentStatus;
import edu.fpt.sba.scms.common.enums.WebhookMatchResult;
import edu.fpt.sba.scms.common.exception.BusinessRuleException;
import edu.fpt.sba.scms.common.exception.NotFoundException;
import edu.fpt.sba.scms.common.exception.UnauthorizedException;
import edu.fpt.sba.scms.domain.order.OrderPricingCalculator;
import edu.fpt.sba.scms.dto.request.CreateOrderRequest;
import edu.fpt.sba.scms.dto.request.ProcessCashPaymentRequest;
import edu.fpt.sba.scms.dto.request.SepayWebhookRequest;
import edu.fpt.sba.scms.dto.response.CashPaymentResponse;
import edu.fpt.sba.scms.dto.response.OrderResponse;
import edu.fpt.sba.scms.dto.response.PaymentStatusResponse;
import edu.fpt.sba.scms.dto.response.VietQrResponse;
import edu.fpt.sba.scms.model.Order;
import edu.fpt.sba.scms.model.Payment;
import edu.fpt.sba.scms.model.PaymentConfig;
import edu.fpt.sba.scms.model.PaymentWebhookLog;
import edu.fpt.sba.scms.repository.OrderRepository;
import edu.fpt.sba.scms.repository.PaymentConfigRepository;
import edu.fpt.sba.scms.repository.PaymentRepository;
import edu.fpt.sba.scms.repository.PaymentWebhookLogRepository;
import edu.fpt.sba.scms.security.principal.UserPrincipal;
import edu.fpt.sba.scms.service.PaymentConfigService;
import edu.fpt.sba.scms.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentWebhookLogRepository paymentWebhookLogRepository;
    private final PaymentConfigService paymentConfigService;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResponse createOrder(CreateOrderRequest request, UserPrincipal principal) {
        Order order = Order.builder()
                .branchId(request.branchId())
                .cashierId(principal != null ? principal.userId() : null)
                .status(OrderStatus.PENDING)
                .totalAmount(request.totalAmount())
                .discountAmount(request.discountAmount() != null ? request.discountAmount() : BigDecimal.ZERO)
                .finalAmount(request.finalAmount())
                .note(request.note())
                .build();

        Order saved = orderRepository.save(order);
        log.info("Tạo đơn hàng mới ID={} tại chi nhánh {}, tổng thanh toán={}",
                saved.getId(), saved.getBranchId(), saved.getFinalAmount());

        return toOrderResponse(saved);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CashPaymentResponse processCashPayment(ProcessCashPaymentRequest request, UserPrincipal principal) {
        Order order = orderRepository.findById(request.orderId())
                .orElseThrow(() -> new NotFoundException("đơn hàng"));

        // BR-26: Đơn hàng đã thanh toán bị đóng băng
        if (order.isPaid()) {
            throw new BusinessRuleException(BusinessErrorCode.ORDER_FROZEN, "Đơn hàng đã được thanh toán trước đó.");
        }

        BigDecimal finalAmount = order.getFinalAmount() != null ? order.getFinalAmount() : BigDecimal.ZERO;
        BigDecimal cashReceived = request.cashReceived();

        // AT1: Tiền khách đưa nhỏ hơn tổng tiền phải trả
        if (!OrderPricingCalculator.isSufficientCash(cashReceived, finalAmount)) {
            throw new BusinessRuleException(BusinessErrorCode.CASH_RECEIVED_LT_TOTAL,
                    String.format("Tiền khách đưa (%s đ) nhỏ hơn tổng tiền thanh toán (%s đ).", cashReceived, finalAmount));
        }

        BigDecimal changeReturned = OrderPricingCalculator.calculateChange(cashReceived, finalAmount);
        Instant now = Instant.now();

        // Cập nhật trạng thái đơn hàng sang PAID
        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(now);
        orderRepository.save(order);

        // Tạo bản ghi Payment với phương thức CASH
        Payment payment = Payment.builder()
                .orderId(order.getId())
                .method(PaymentMethod.CASH)
                .amount(finalAmount)
                .receivedAmount(cashReceived)
                .status(PaymentStatus.SUCCESS)
                .build();
        Payment savedPayment = paymentRepository.save(payment);

        // BR-51: Ghi log kiểm toán
        auditLogService.log(AuditAction.PAYMENT_SUCCESS, "payment", savedPayment.getId(), null,
                "CASH: " + finalAmount + " VND (Received: " + cashReceived + ", Change: " + changeReturned + ")");
        auditLogService.log(AuditAction.ORDER_PAID, "order", order.getId(), OrderStatus.PENDING.name(), OrderStatus.PAID.name());

        log.info("Thanh toán tiền mặt thành công cho đơn hàng ID={}: Phải trả={}, Khách đưa={}, Thối lại={}",
                order.getId(), finalAmount, cashReceived, changeReturned);

        return new CashPaymentResponse(
                order.getId(),
                order.getStatus(),
                savedPayment.getStatus(),
                savedPayment.getMethod(),
                finalAmount,
                cashReceived,
                changeReturned,
                now,
                "Thanh toán tiền mặt thành công. Tiền thối lại: " + changeReturned.toPlainString() + " đ."
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VietQrResponse generateVietQr(Integer orderId, UserPrincipal principal) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("đơn hàng"));

        if (order.isPaid()) {
            throw new BusinessRuleException(BusinessErrorCode.ORDER_FROZEN, "Đơn hàng đã thanh toán thành công.");
        }

        PaymentConfig config = paymentConfigService.getInternalConfig();

        // 1. Tạo mã tham chiếu nhận diện chuyển khoản (BR-24, BR-28): CAFE{orderId}
        String prefix = StringUtils.hasText(config.getPaymentPrefix()) ? config.getPaymentPrefix() : "CAFE";
        String referenceCode = prefix + order.getId();

        // 2. Tạo đường link VietQR động
        String encodedHolder = URLEncoder.encode(config.getAccountHolder(), StandardCharsets.UTF_8);
        String bank = config.getBankName().trim();
        String accountNo = config.getAccountNumber().trim();
        BigDecimal amount = order.getFinalAmount();

        // URL chuẩn hiển thị QR dạng ảnh (quick link VietQR)
        String qrImageUrl = String.format(
                "https://img.vietqr.io/image/%s-%s-compact2.png?amount=%s&addInfo=%s&accountName=%s",
                bank, accountNo, amount.toPlainString(), referenceCode, encodedHolder
        );

        // Payload định dạng chuẩn
        String qrPayload = String.format("vietqr://transfer?bank=%s&acc=%s&amount=%s&memo=%s",
                bank, accountNo, amount.toPlainString(), referenceCode);

        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(15));

        // 3. Lưu hoặc cập nhật bản ghi Payment với trạng thái PENDING
        Payment payment = paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(order.getId())
                .orElseGet(() -> Payment.builder()
                        .orderId(order.getId())
                        .build());

        payment.setMethod(PaymentMethod.VIETQR);
        payment.setAmount(amount);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setReferenceCode(referenceCode);
        payment.setQrCode(qrImageUrl);
        payment.setQrExpiresAt(expiresAt);

        paymentRepository.save(payment);
        log.info("Tạo mã VietQR cho đơn hàng ID={}, mã tham chiếu={}, số tiền={}", order.getId(), referenceCode, amount);

        return new VietQrResponse(
                order.getId(),
                referenceCode,
                amount,
                config.getBankName(),
                config.getAccountNumber(),
                config.getAccountHolder(),
                qrImageUrl,
                qrPayload,
                expiresAt,
                PaymentStatus.PENDING
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentStatusResponse processSepayWebhook(SepayWebhookRequest payload, String authHeader, String rawPayload) {
        log.info("Nhận Webhook từ SePay: transId={}, gateway={}, amount={}, content={}",
                payload.id(), payload.gateway(), payload.transferAmount(), payload.content());

        PaymentConfig config = paymentConfigService.getInternalConfig();
        String sepayRef = payload.id() != null ? payload.id().toString() : "UNKNOWN_" + Instant.now().toEpochMilli();
        BigDecimal amount = payload.transferAmount() != null ? payload.transferAmount() : BigDecimal.ZERO;

        // BƯỚC 1: Xác thực Signature / Token từ SePay (BR-29)
        boolean signatureValid = verifySignature(authHeader, config.getApiToken());
        if (!signatureValid) {
            log.warn("Xác thực Webhook SePay thất bại: chữ ký hoặc Token không hợp lệ. header={}", authHeader);
            saveWebhookLog(null, sepayRef, amount, false, WebhookMatchResult.INVALID_SIG, rawPayload);
            throw new UnauthorizedException(ErrorCode.AUTH_TOKEN_INVALID, "Chữ ký xác thực Webhook SePay không hợp lệ.");
        }

        // BƯỚC 2: Kiểm tra chống xử lý trùng lặp (Anti-duplicate - BR-26, BR-29, AT2)
        if (paymentWebhookLogRepository.existsBySepayRef(sepayRef)) {
            log.warn("Bỏ qua Webhook trùng lặp: transId={}", sepayRef);
            saveWebhookLog(null, sepayRef, amount, true, WebhookMatchResult.DUPLICATE, rawPayload);
            return new PaymentStatusResponse(
                    null, null, PaymentStatus.SUCCESS, PaymentMethod.VIETQR,
                    amount, amount, null, sepayRef, Instant.now(),
                    "Giao dịch SePay đã được xử lý trước đó (trùng lặp)."
            );
        }

        // BƯỚC 3: Trích xuất mã tham chiếu đơn hàng (Order Reference) từ nội dung chuyển khoản (BR-28)
        String prefix = StringUtils.hasText(config.getPaymentPrefix()) ? config.getPaymentPrefix() : "CAFE";
        String narration = (payload.content() != null ? payload.content() : "") + " "
                + (payload.description() != null ? payload.description() : "");

        Integer orderId = extractOrderId(narration, prefix);
        if (orderId == null && payload.code() != null) {
            orderId = extractOrderId(payload.code(), prefix);
        }

        if (orderId == null) {
            log.warn("Không tìm thấy mã tham chiếu '{}' trong nội dung chuyển khoản: '{}'", prefix, narration);
            saveWebhookLog(null, sepayRef, amount, true, WebhookMatchResult.NOT_FOUND, rawPayload);
            return new PaymentStatusResponse(
                    null, null, PaymentStatus.FAILED, PaymentMethod.VIETQR,
                    amount, amount, null, sepayRef, null,
                    "Không tìm thấy mã đơn hàng trong nội dung chuyển khoản."
            );
        }

        // BƯỚC 4: Tìm đơn hàng và Payment tương ứng
        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isEmpty()) {
            log.warn("Không tìm thấy đơn hàng ID={}", orderId);
            saveWebhookLog(null, sepayRef, amount, true, WebhookMatchResult.NOT_FOUND, rawPayload);
            return new PaymentStatusResponse(
                    orderId, null, PaymentStatus.FAILED, PaymentMethod.VIETQR,
                    amount, amount, null, sepayRef, null,
                    "Không tìm thấy đơn hàng ID " + orderId
            );
        }

        Order order = orderOpt.get();
        Payment payment = paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(order.getId())
                .orElseGet(() -> Payment.builder()
                        .orderId(order.getId())
                        .method(PaymentMethod.VIETQR)
                        .amount(order.getFinalAmount())
                        .build());

        // BƯỚC 5: Đối soát số tiền thanh toán (BR-29, AT4)
        boolean amountMatch = amount.compareTo(order.getFinalAmount()) >= 0;

        if (amountMatch) {
            // Khớp tiền -> Xác nhận thành công và đóng băng đơn hàng (BR-26, BR-29, BR-31)
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setReceivedAmount(amount);
            payment.setSepayRef(sepayRef);
            Payment savedPayment = paymentRepository.save(payment);

            order.setStatus(OrderStatus.PAID);
            order.setPaidAt(Instant.now());
            orderRepository.save(order);

            saveWebhookLog(savedPayment.getId(), sepayRef, amount, true, WebhookMatchResult.MATCH, rawPayload);

            auditLogService.log(AuditAction.PAYMENT_SUCCESS, "payment", savedPayment.getId(), null, savedPayment);
            auditLogService.log(AuditAction.ORDER_PAID, "order", order.getId(), null, order);

            log.info("Xác nhận thanh toán SePay thành công cho đơn hàng ID={}, số tiền={}", order.getId(), amount);

            return new PaymentStatusResponse(
                    order.getId(),
                    order.getStatus(),
                    savedPayment.getStatus(),
                    savedPayment.getMethod(),
                    savedPayment.getAmount(),
                    amount,
                    savedPayment.getReferenceCode(),
                    sepayRef,
                    order.getPaidAt(),
                    "Thanh toán SePay thành công. Đơn hàng chuyển sang trạng thái PAID."
            );
        } else {
            // Lệch tiền (thiếu tiền) -> Đánh dấu MISMATCH, giữ nguyên trạng thái PENDING của Order (AT4)
            payment.setStatus(PaymentStatus.MISMATCH);
            payment.setReceivedAmount(amount);
            payment.setSepayRef(sepayRef);
            Payment savedPayment = paymentRepository.save(payment);

            saveWebhookLog(savedPayment.getId(), sepayRef, amount, true, WebhookMatchResult.MISMATCH, rawPayload);
            log.warn("Thanh toán không khớp số tiền cho đơn hàng ID={}: Nhận {}, cần {}",
                    order.getId(), amount, order.getFinalAmount());

            return new PaymentStatusResponse(
                    order.getId(),
                    order.getStatus(),
                    PaymentStatus.MISMATCH,
                    savedPayment.getMethod(),
                    savedPayment.getAmount(),
                    amount,
                    savedPayment.getReferenceCode(),
                    sepayRef,
                    null,
                    "Số tiền thanh toán không khớp: nhận " + amount + ", yêu cầu " + order.getFinalAmount()
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentStatusResponse getPaymentStatus(Integer orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("đơn hàng"));

        Optional<Payment> paymentOpt = paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(orderId);

        if (paymentOpt.isPresent()) {
            Payment p = paymentOpt.get();
            return new PaymentStatusResponse(
                    order.getId(),
                    order.getStatus(),
                    p.getStatus(),
                    p.getMethod(),
                    p.getAmount(),
                    p.getReceivedAmount(),
                    p.getReferenceCode(),
                    p.getSepayRef(),
                    order.getPaidAt(),
                    order.isPaid() ? "Đơn hàng đã thanh toán thành công." : "Đang chờ thanh toán."
            );
        }

        return new PaymentStatusResponse(
                order.getId(),
                order.getStatus(),
                PaymentStatus.PENDING,
                null,
                order.getFinalAmount(),
                null,
                null,
                null,
                order.getPaidAt(),
                "Chưa khởi tạo giao dịch thanh toán."
        );
    }

    private boolean verifySignature(String authHeader, String configuredToken) {
        if (!StringUtils.hasText(configuredToken)) {
            // Chưa cấu hình token bí mật -> chấp nhận trong môi trường test/sandbox
            return true;
        }
        if (!StringUtils.hasText(authHeader)) {
            return false;
        }
        // Hỗ trợ cả định dạng "Apikey {token}", "Bearer {token}" hoặc "{token}"
        String cleaned = authHeader.replace("Apikey ", "").replace("Bearer ", "").trim();
        return configuredToken.trim().equals(cleaned);
    }

    private Integer extractOrderId(String text, String prefix) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        Pattern pattern = Pattern.compile(Pattern.quote(prefix) + "\\s*([0-9]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private void saveWebhookLog(Integer paymentId, String sepayRef, BigDecimal amount,
                                boolean signatureValid, WebhookMatchResult matchResult, String rawPayload) {
        try {
            PaymentWebhookLog logEntry = PaymentWebhookLog.builder()
                    .paymentId(paymentId)
                    .sepayRef(sepayRef)
                    .amount(amount)
                    .signatureValid(signatureValid)
                    .matchResult(matchResult)
                    .rawPayload(rawPayload != null && rawPayload.length() > 4000 ? rawPayload.substring(0, 4000) : rawPayload)
                    .receivedAt(Instant.now())
                    .build();
            paymentWebhookLogRepository.save(logEntry);
        } catch (Exception e) {
            log.error("Lỗi khi lưu payment_webhook_log cho transId {}: {}", sepayRef, e.getMessage());
        }
    }

    private OrderResponse toOrderResponse(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getBranchId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getDiscountAmount(),
                order.getFinalAmount(),
                order.getNote(),
                order.getCreatedAt(),
                order.getPaidAt()
        );
    }
}
