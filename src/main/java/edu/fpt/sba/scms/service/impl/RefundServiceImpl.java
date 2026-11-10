package edu.fpt.sba.scms.service.impl;

import edu.fpt.sba.scms.common.audit.AuditLogService;
import edu.fpt.sba.scms.common.enums.AuditAction;
import edu.fpt.sba.scms.common.enums.BusinessErrorCode;
import edu.fpt.sba.scms.common.enums.CashTransactionType;
import edu.fpt.sba.scms.common.enums.OrderStatus;
import edu.fpt.sba.scms.common.enums.RefundStatus;
import edu.fpt.sba.scms.common.enums.Roles;
import edu.fpt.sba.scms.common.enums.ShiftStatus;
import edu.fpt.sba.scms.common.enums.UserStatus;
import edu.fpt.sba.scms.common.exception.BusinessRuleException;
import edu.fpt.sba.scms.common.exception.NotFoundException;
import edu.fpt.sba.scms.domain.refund.RefundPolicy;
import edu.fpt.sba.scms.domain.shift.ShiftCashCalculator;
import edu.fpt.sba.scms.dto.request.CreateRefundRequest;
import edu.fpt.sba.scms.dto.response.RefundResponse;
import edu.fpt.sba.scms.mapper.RefundMapper;
import edu.fpt.sba.scms.model.CashTransaction;
import edu.fpt.sba.scms.model.Order;
import edu.fpt.sba.scms.model.Payment;
import edu.fpt.sba.scms.model.Refund;
import edu.fpt.sba.scms.model.Shift;
import edu.fpt.sba.scms.model.User;
import edu.fpt.sba.scms.repository.CashTransactionRepository;
import edu.fpt.sba.scms.repository.OrderRepository;
import edu.fpt.sba.scms.repository.PaymentRepository;
import edu.fpt.sba.scms.repository.RefundRepository;
import edu.fpt.sba.scms.repository.ShiftRepository;
import edu.fpt.sba.scms.repository.UserRepository;
import edu.fpt.sba.scms.service.support.PinHasher;
import edu.fpt.sba.scms.security.principal.UserPrincipal;
import edu.fpt.sba.scms.service.RefundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final ShiftRepository shiftRepository;
    private final CashTransactionRepository cashTransactionRepository;
    private final UserRepository userRepository;
    private final PinHasher pinHasher;
    private final AuditLogService auditLogService;
    private final RefundMapper refundMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RefundResponse processRefund(CreateRefundRequest request, UserPrincipal principal) {
        Order order = orderRepository.findById(request.orderId())
                .orElseThrow(() -> new NotFoundException("đơn hàng"));

        // 1. Kiểm tra trạng thái đơn: Chỉ đơn PAID mới được hoàn tiền
        if (order.getStatus() != OrderStatus.PAID) {
            throw new BusinessRuleException(BusinessErrorCode.REFUND_NOT_ALLOWED,
                    "Chỉ đơn hàng đã thanh toán (PAID) mới được phép hoàn tiền. Trạng thái hiện tại: " + order.getStatus());
        }

        // 2. Kiểm tra nếu đã hoàn tiền trước đó
        if (refundRepository.existsByOrderIdAndStatus(order.getId(), RefundStatus.DONE)) {
            throw new BusinessRuleException(BusinessErrorCode.REFUND_NOT_ALLOWED,
                    "Đơn hàng này đã được hoàn tiền trước đó.");
        }

        // 3. Số tiền hoàn không vượt quá số tiền đơn hàng (BR-34) qua RefundPolicy
        if (RefundPolicy.isExceedsPaidAmount(request.amount(), order.getFinalAmount())) {
            throw new BusinessRuleException(BusinessErrorCode.REFUND_EXCEEDS_PAID,
                    "Số tiền hoàn (" + request.amount().toPlainString() + " đ) vượt quá tổng tiền thanh toán của đơn ("
                            + order.getFinalAmount().toPlainString() + " đ).");
        }

        // 4. Tìm ca mở hiện tại của chi nhánh để trừ tiền két (BR-34, BR-36)
        Shift openShift = shiftRepository.findFirstByBranchIdAndStatusOrderByOpenedAtDesc(order.getBranchId(), ShiftStatus.OPEN)
                .orElseThrow(() -> new BusinessRuleException(BusinessErrorCode.SHIFT_NOT_OPEN,
                        "Chi nhánh chưa mở ca làm việc. Bắt buộc phải có ca mở để xuất tiền hoàn từ két tiền mặt (BR-34)."));

        if (openShift.isHandoverPending()) {
            throw new BusinessRuleException(BusinessErrorCode.SHIFT_HANDOVER_PENDING,
                    "Két tiền đang tạm khóa chờ xác nhận bàn giao giữa ca #" + openShift.getId()
                            + ". Vui lòng hoàn tất kiểm két nhận ca trước khi chi hoàn tiền.");
        }
        if (openShift.isEarlyClosePending()) {
            throw new BusinessRuleException(BusinessErrorCode.SHIFT_EARLY_CLOSE_PENDING,
                    "Ca #" + openShift.getId()
                            + " đang chờ Quản lý chi nhánh xác nhận đóng ca sớm. Quầy POS tạm khóa cho đến khi được duyệt hoặc hủy yêu cầu.");
        }

        // 5. Kiểm tra tiền két có đủ để chi trả hoàn tiền không qua ShiftCashCalculator
        BigDecimal currentCash = openShift.getExpectedCash() != null ? openShift.getExpectedCash() : BigDecimal.ZERO;
        if (!ShiftCashCalculator.isSufficientCash(currentCash, request.amount())) {
            throw new BusinessRuleException(BusinessErrorCode.INSUFFICIENT_CASH,
                    "Số dư két tiền hiện tại (" + currentCash.toPlainString()
                            + " đ) không đủ để chi hoàn tiền " + request.amount().toPlainString() + " đ.");
        }

        // 6. Xác thực mã PIN Quản lý (BR-32, BR-35: > 500k yêu cầu COO phê duyệt)
        User approver = verifyApproverPin(order.getBranchId(), request.managerPin(), request.amount());

        Payment payment = paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(order.getId())
                .orElse(null);
        Integer paymentId = payment != null ? payment.getId() : 0;

        // 7. Ghi nhận giao dịch xuất tiền mặt khỏi két (REFUND_OUT)
        CashTransaction cashTx = CashTransaction.builder()
                .shiftId(openShift.getId())
                .type(CashTransactionType.REFUND_OUT)
                .amount(request.amount())
                .reason("Hoàn tiền đơn #" + order.getId() + ": " + request.reason())
                .createdBy(principal != null ? principal.userId() : 1)
                .approvedBy(approver.getId())
                .isSelfInitiated(principal != null && principal.userId().equals(approver.getId()))
                .build();
        CashTransaction savedTx = cashTransactionRepository.save(cashTx);

        // 8. Trừ số tiền kỳ vọng trong két tiền ca hiện tại qua ShiftCashCalculator (BR-39)
        openShift.setExpectedCash(ShiftCashCalculator.deductCash(currentCash, request.amount()));
        shiftRepository.save(openShift);

        // 9. Lưu bản ghi Refund
        Instant now = Instant.now();
        Refund refund = Refund.builder()
                .paymentId(paymentId)
                .orderId(order.getId())
                .cashTransactionId(savedTx.getId())
                .amount(request.amount())
                .reason(request.reason())
                .status(RefundStatus.DONE)
                .requestedBy(principal != null ? principal.userId() : 1)
                .approvedBy(approver.getId())
                .approvedAt(now)
                .build();
        Refund savedRefund = refundRepository.save(refund);

        // 10. Chuyển trạng thái đơn hàng qua RefundPolicy
        order.setStatus(RefundPolicy.determinePostRefundStatus(request.amount(), order.getFinalAmount()));
        order.setCancelReason("Hoàn tiền: " + request.reason());
        orderRepository.save(order);

        auditLogService.log(AuditAction.REFUND_APPROVE, "order", order.getId(), null,
                "Hoàn tiền đơn ID=" + order.getId() + ", số tiền=" + request.amount() + ", người duyệt=" + approver.getUsername());

        log.info("Hoàn tiền thành công cho đơn hàng ID={}: Số tiền={}, Approver={}", order.getId(), request.amount(),
                approver.getUsername());

        return refundMapper.toRefundResponse(savedRefund,
                "Hoàn tiền thành công " + request.amount().toPlainString() + " đ cho đơn hàng #" + order.getId() + ".");
    }

    private User verifyApproverPin(Integer branchId, String pin, BigDecimal amount) {
        if (!StringUtils.hasText(pin)) {
            throw new BusinessRuleException(BusinessErrorCode.MANAGER_PIN_REQUIRED,
                    "Thao tác hoàn tiền bắt buộc phải có mã PIN phê duyệt (BR-32).");
        }
        String lookupHash = pinHasher.lookupHash(branchId, pin.trim());
        User approver = userRepository.findFirstByBranchIdAndPinLookupHashAndStatus(branchId, lookupHash, UserStatus.ACTIVE)
                .orElseThrow(() -> new BusinessRuleException(BusinessErrorCode.INVALID_MANAGER_PIN,
                        "Mã PIN Quản lý phê duyệt không chính xác hoặc tài khoản không tồn tại."));

        if (!pinHasher.matches(pin.trim(), approver.getPinHash())) {
            throw new BusinessRuleException(BusinessErrorCode.INVALID_MANAGER_PIN, "Mã PIN Quản lý không chính xác.");
        }

        String role = approver.roleCode();
        // BR-35: Nếu số tiền hoàn > 500.000đ thì bắt buộc phải là COO phê duyệt qua RefundPolicy
        if (RefundPolicy.requiresCooApproval(amount)) {
            if (!Roles.COO.code().equals(role)) {
                throw new BusinessRuleException(BusinessErrorCode.REFUND_NOT_ALLOWED,
                        "Số tiền hoàn vượt quá hạn mức 500.000 đ (BR-35). Bắt buộc phải có mã PIN phê duyệt của Giám đốc vận hành (COO).");
            }
        } else {
            if (!Roles.BRANCH_MANAGER.code().equals(role) && !Roles.COO.code().equals(role)) {
                throw new BusinessRuleException(BusinessErrorCode.MANAGER_PIN_REQUIRED,
                        "Tài khoản duyệt không có quyền phê duyệt hoàn tiền (yêu cầu Branch Manager hoặc COO).");
            }
        }

        return approver;
    }
}
