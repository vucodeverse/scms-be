package edu.fpt.sba.scms.service.impl;

import edu.fpt.sba.scms.common.audit.AuditLogService;
import edu.fpt.sba.scms.common.enums.AuditAction;
import edu.fpt.sba.scms.common.enums.BusinessErrorCode;
import edu.fpt.sba.scms.common.enums.ErrorCode;
import edu.fpt.sba.scms.common.enums.OrderStatus;
import edu.fpt.sba.scms.common.enums.PaymentMethod;
import edu.fpt.sba.scms.common.enums.PaymentStatus;
import edu.fpt.sba.scms.common.enums.ShiftStatus;
import edu.fpt.sba.scms.common.enums.WebhookMatchResult;
import edu.fpt.sba.scms.common.exception.BusinessRuleException;
import edu.fpt.sba.scms.common.exception.NotFoundException;
import edu.fpt.sba.scms.common.exception.UnauthorizedException;
import edu.fpt.sba.scms.domain.order.OrderPricingCalculator;
import edu.fpt.sba.scms.domain.shift.ShiftCashCalculator;
import edu.fpt.sba.scms.dto.request.CreateOrderRequest;
import edu.fpt.sba.scms.dto.request.ProcessCashPaymentRequest;
import edu.fpt.sba.scms.dto.request.SepayWebhookRequest;
import edu.fpt.sba.scms.dto.response.CashPaymentResponse;
import edu.fpt.sba.scms.dto.response.OrderResponse;
import edu.fpt.sba.scms.dto.response.PaymentStatusResponse;
import edu.fpt.sba.scms.dto.response.VietQrResponse;
import edu.fpt.sba.scms.integration.printer.PrinterPort;
import edu.fpt.sba.scms.integration.sepay.SepayPort;
import edu.fpt.sba.scms.integration.vietqr.VietQrPort;
import edu.fpt.sba.scms.mapper.PaymentMapper;
import edu.fpt.sba.scms.model.Order;
import edu.fpt.sba.scms.model.Payment;
import edu.fpt.sba.scms.model.PaymentConfig;
import edu.fpt.sba.scms.model.PaymentWebhookLog;
import edu.fpt.sba.scms.repository.OrderRepository;
import edu.fpt.sba.scms.repository.PaymentConfigRepository;
import edu.fpt.sba.scms.repository.PaymentRepository;
import edu.fpt.sba.scms.repository.PaymentWebhookLogRepository;
import edu.fpt.sba.scms.repository.ShiftRepository;
import edu.fpt.sba.scms.security.principal.UserPrincipal;
import edu.fpt.sba.scms.service.PaymentConfigService;
import edu.fpt.sba.scms.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentWebhookLogRepository paymentWebhookLogRepository;
    private final PaymentConfigService paymentConfigService;
    private final AuditLogService auditLogService;
    private final PaymentMapper paymentMapper;
    private final VietQrPort vietQrPort;
    private final SepayPort sepayPort;
    private final PrinterPort printerPort;
    private final ShiftRepository shiftRepository;

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

        return paymentMapper.toOrderResponse(saved);
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
                    String.format("Tiền khách đưa (%s đ) nhỏ hơn tổng tiền thanh toán (%s đ).", cashReceived,
                            finalAmount));
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

        // BR-27: Tiền mặt thu ngay vào ca làm việc & cập nhật expected_cash (BR-39)
        shiftRepository.findFirstByBranchIdAndStatusOrderByOpenedAtDesc(order.getBranchId(), ShiftStatus.OPEN)
                .ifPresent(shift -> {
                    shift.setCashRevenue(shift.getCashRevenue().add(finalAmount));
                    shift.setExpectedCash(ShiftCashCalculator.addCash(shift.getExpectedCash(), finalAmount));
                    shiftRepository.save(shift);
                });

        // BR-51: Ghi log kiểm toán
        auditLogService.log(AuditAction.PAYMENT_SUCCESS, "payment", savedPayment.getId(), null,
                "CASH: " + finalAmount + " VND (Received: " + cashReceived + ", Change: " + changeReturned + ")");
        auditLogService.log(AuditAction.ORDER_PAID, "order", order.getId(), OrderStatus.PENDING.name(),
                OrderStatus.PAID.name());

        // In hóa đơn qua cổng máy in nhiệt ESC/POS (BR-31)
        printerPort.printReceipt(new PrinterPort.ReceiptCommand(
                order.getId(),
                order.getReceiptNo(),
                "Chi nhánh #" + order.getBranchId(),
                principal != null ? principal.username() : "Thu ngân",
                finalAmount,
                now
        ));

        log.info("Thanh toán tiền mặt thành công cho đơn hàng ID={}: Phải trả={}, Khách đưa={}, Thối lại={}",
                order.getId(), finalAmount, cashReceived, changeReturned);

        return paymentMapper.toCashPaymentResponse(
                order,
                savedPayment,
                cashReceived,
                changeReturned,
                now,
                "Thanh toán tiền mặt thành công. Tiền thối lại: " + changeReturned.toPlainString() + " đ.");
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
        BigDecimal amount = order.getFinalAmount();

        // 2. Tạo link VietQR động qua cổng tích hợp VietQrPort
        VietQrPort.VietQrData qrData = vietQrPort.generateQr(new VietQrPort.VietQrCommand(
                config.getBankName(),
                config.getAccountNumber(),
                config.getAccountHolder(),
                amount,
                referenceCode
        ));

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
        payment.setQrCode(qrData.qrImageUrl());
        payment.setQrExpiresAt(expiresAt);

        Payment savedPayment = paymentRepository.save(payment);
        log.info("Tạo mã VietQR cho đơn hàng ID={}, mã tham chiếu={}, số tiền={}", order.getId(), referenceCode,
                amount);

        return paymentMapper.toVietQrResponse(
                order,
                savedPayment,
                config.getBankName(),
                config.getAccountNumber(),
                config.getAccountHolder(),
                qrData.qrPayload());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentStatusResponse processSepayWebhook(SepayWebhookRequest payload, String authHeader,
            String rawPayload) {
        log.info("Nhận Webhook từ SePay: transId={}, gateway={}, amount={}, content={}",
                payload.id(), payload.gateway(), payload.transferAmount(), payload.content());

        PaymentConfig config = paymentConfigService.getInternalConfig();
        String sepayRef = payload.id() != null ? payload.id().toString() : "UNKNOWN_" + Instant.now().toEpochMilli();
        BigDecimal amount = payload.transferAmount() != null ? payload.transferAmount() : BigDecimal.ZERO;

        // BƯỚC 1: Xác thực Signature / Token từ SePay qua SepayPort (BR-29)
        boolean signatureValid = sepayPort.verifySignature(authHeader, config.getApiToken());
        if (!signatureValid) {
            log.warn("Xác thực Webhook SePay thất bại: chữ ký hoặc Token không hợp lệ. header={}", authHeader);
            saveWebhookLog(null, sepayRef, amount, false, WebhookMatchResult.INVALID_SIG, rawPayload);
            throw new UnauthorizedException(ErrorCode.AUTH_TOKEN_INVALID,
                    "Chữ ký xác thực Webhook SePay không hợp lệ.");
        }

        // BƯỚC 2: Kiểm tra chống xử lý trùng lặp (Anti-duplicate - BR-26, BR-29, AT2)
        if (paymentWebhookLogRepository.existsBySepayRef(sepayRef)) {
            log.warn("Bỏ qua Webhook trùng lặp: transId={}", sepayRef);
            saveWebhookLog(null, sepayRef, amount, true, WebhookMatchResult.DUPLICATE, rawPayload);
            Payment duplicatePayment = Payment.builder()
                    .status(PaymentStatus.SUCCESS)
                    .method(PaymentMethod.VIETQR)
                    .amount(amount)
                    .receivedAmount(amount)
                    .sepayRef(sepayRef)
                    .build();
            return paymentMapper.toPaymentStatusResponse(
                    null,
                    duplicatePayment,
                    "Giao dịch SePay đã được xử lý trước đó (trùng lặp).");
        }

        // BƯỚC 3: Trích xuất mã tham chiếu đơn hàng qua SepayPort (BR-28)
        String prefix = StringUtils.hasText(config.getPaymentPrefix()) ? config.getPaymentPrefix() : "CAFE";
        String narration = (payload.content() != null ? payload.content() : "") + " "
                + (payload.description() != null ? payload.description() : "");

        Integer orderId = sepayPort.extractOrderId(narration, prefix);
        if (orderId == null && payload.code() != null) {
            orderId = sepayPort.extractOrderId(payload.code(), prefix);
        }

        if (orderId == null) {
            log.warn("Không tìm thấy mã tham chiếu '{}' trong nội dung chuyển khoản: '{}'", prefix, narration);
            saveWebhookLog(null, sepayRef, amount, true, WebhookMatchResult.NOT_FOUND, rawPayload);
            Payment failedPayment = Payment.builder()
                    .status(PaymentStatus.FAILED)
                    .method(PaymentMethod.VIETQR)
                    .amount(amount)
                    .receivedAmount(amount)
                    .sepayRef(sepayRef)
                    .build();
            return paymentMapper.toPaymentStatusResponse(
                    null,
                    failedPayment,
                    "Không tìm thấy mã đơn hàng trong nội dung chuyển khoản.");
        }

        // BƯỚC 4: Tìm đơn hàng và Payment tương ứng
        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isEmpty()) {
            log.warn("Không tìm thấy đơn hàng ID={}", orderId);
            saveWebhookLog(null, sepayRef, amount, true, WebhookMatchResult.NOT_FOUND, rawPayload);
            Payment failedPayment = Payment.builder()
                    .orderId(orderId)
                    .status(PaymentStatus.FAILED)
                    .method(PaymentMethod.VIETQR)
                    .amount(amount)
                    .receivedAmount(amount)
                    .sepayRef(sepayRef)
                    .build();
            return paymentMapper.toPaymentStatusResponse(
                    null,
                    failedPayment,
                    "Không tìm thấy đơn hàng ID " + orderId);
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

            // Ghi nhận doanh thu chuyển khoản của ca đang mở
            shiftRepository.findFirstByBranchIdAndStatusOrderByOpenedAtDesc(order.getBranchId(), ShiftStatus.OPEN)
                    .ifPresent(shift -> {
                        shift.setTransferRevenue(shift.getTransferRevenue().add(amount));
                        shiftRepository.save(shift);
                    });

            saveWebhookLog(savedPayment.getId(), sepayRef, amount, true, WebhookMatchResult.MATCH, rawPayload);

            auditLogService.log(AuditAction.PAYMENT_SUCCESS, "payment", savedPayment.getId(), null, savedPayment);
            auditLogService.log(AuditAction.ORDER_PAID, "order", order.getId(), null, order);

            log.info("Xác nhận thanh toán SePay thành công cho đơn hàng ID={}, số tiền={}", order.getId(), amount);

            return paymentMapper.toPaymentStatusResponse(
                    order,
                    savedPayment,
                    "Thanh toán SePay thành công. Đơn hàng chuyển sang trạng thái PAID.");
        } else {
            // Lệch tiền (thiếu tiền) -> Đánh dấu MISMATCH, giữ nguyên trạng thái PENDING
            // của Order (AT4)
            payment.setStatus(PaymentStatus.MISMATCH);
            payment.setReceivedAmount(amount);
            payment.setSepayRef(sepayRef);
            Payment savedPayment = paymentRepository.save(payment);

            saveWebhookLog(savedPayment.getId(), sepayRef, amount, true, WebhookMatchResult.MISMATCH, rawPayload);
            log.warn("Thanh toán không khớp số tiền cho đơn hàng ID={}: Nhận {}, cần {}",
                    order.getId(), amount, order.getFinalAmount());

            return paymentMapper.toPaymentStatusResponse(
                    order,
                    savedPayment,
                    "Số tiền thanh toán không khớp: nhận " + amount + ", yêu cầu " + order.getFinalAmount());
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
            return paymentMapper.toPaymentStatusResponse(
                    order,
                    p,
                    order.isPaid() ? "Đơn hàng đã thanh toán thành công." : "Đang chờ thanh toán.");
        }

        Payment pendingPayment = Payment.builder()
                .orderId(order.getId())
                .status(PaymentStatus.PENDING)
                .amount(order.getFinalAmount())
                .build();
        return paymentMapper.toPaymentStatusResponse(
                order,
                pendingPayment,
                "Chưa khởi tạo giao dịch thanh toán.");
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
                    .rawPayload(rawPayload != null && rawPayload.length() > 4000 ? rawPayload.substring(0, 4000)
                            : rawPayload)
                    .receivedAt(Instant.now())
                    .build();
            paymentWebhookLogRepository.save(logEntry);
        } catch (Exception e) {
            log.error("Lỗi khi lưu payment_webhook_log cho transId {}: {}", sepayRef, e.getMessage());
        }
    }
}
