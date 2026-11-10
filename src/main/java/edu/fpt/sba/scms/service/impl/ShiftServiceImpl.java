package edu.fpt.sba.scms.service.impl;

import edu.fpt.sba.scms.common.audit.AuditLogService;
import edu.fpt.sba.scms.common.enums.AttendanceStatus;
import edu.fpt.sba.scms.common.enums.AuditAction;
import edu.fpt.sba.scms.common.enums.BusinessErrorCode;
import edu.fpt.sba.scms.common.enums.CashTransactionType;
import edu.fpt.sba.scms.common.enums.ErrorCode;
import edu.fpt.sba.scms.common.enums.Roles;
import edu.fpt.sba.scms.common.enums.SecurityAlertSeverity;
import edu.fpt.sba.scms.common.enums.SecurityAlertStatus;
import edu.fpt.sba.scms.common.enums.SecurityAlertType;
import edu.fpt.sba.scms.common.enums.ShiftHandoverStatus;
import edu.fpt.sba.scms.common.enums.ShiftHandoverType;
import edu.fpt.sba.scms.common.enums.ShiftStatus;
import edu.fpt.sba.scms.common.enums.ShiftType;
import edu.fpt.sba.scms.common.enums.SortDirection;
import edu.fpt.sba.scms.common.enums.UserStatus;
import edu.fpt.sba.scms.common.exception.BusinessRuleException;
import edu.fpt.sba.scms.common.exception.ForbiddenException;
import edu.fpt.sba.scms.common.exception.InvalidFieldValueException;
import edu.fpt.sba.scms.common.exception.InvalidPinException;
import edu.fpt.sba.scms.common.exception.NotFoundException;
import edu.fpt.sba.scms.common.exception.PinLockedException;
import edu.fpt.sba.scms.common.exception.UnauthorizedException;
import edu.fpt.sba.scms.common.util.DateTimeUtil;
import edu.fpt.sba.scms.domain.attendance.AttendanceCalculator;
import edu.fpt.sba.scms.domain.shift.ShiftCashCalculator;
import edu.fpt.sba.scms.domain.shift.ShiftSchedulePolicy;
import edu.fpt.sba.scms.dto.PageQueryDefaults;
import edu.fpt.sba.scms.dto.request.AcceptShiftHandoverRequest;
import edu.fpt.sba.scms.dto.request.ApproveEarlyCloseRequest;
import edu.fpt.sba.scms.dto.request.ApproveManagerCloseRequest;
import edu.fpt.sba.scms.dto.request.CancelShiftHandoverRequest;
import edu.fpt.sba.scms.dto.request.CashAdjustmentRequest;
import edu.fpt.sba.scms.dto.request.CloseShiftRequest;
import edu.fpt.sba.scms.dto.request.CreateScheduleShiftRequest;
import edu.fpt.sba.scms.dto.request.InitiateShiftHandoverRequest;
import edu.fpt.sba.scms.dto.request.OpenShiftRequest;
import edu.fpt.sba.scms.dto.request.PostCloseCashOutRequest;
import edu.fpt.sba.scms.dto.request.PreOpenCashInRequest;
import edu.fpt.sba.scms.dto.request.RejectEarlyCloseRequest;
import edu.fpt.sba.scms.dto.request.RejectManagerCloseRequest;
import edu.fpt.sba.scms.dto.request.UpdateScheduleShiftRequest;
import edu.fpt.sba.scms.dto.request.query.ShiftListQuery;
import edu.fpt.sba.scms.dto.response.BranchShiftOverviewResponse;
import edu.fpt.sba.scms.dto.response.CashTransactionResponse;
import edu.fpt.sba.scms.dto.response.PreOpenCashAdjustmentResponse;
import edu.fpt.sba.scms.dto.response.PreviousShiftSummaryResponse;
import edu.fpt.sba.scms.dto.response.ShiftHandoverResponse;
import edu.fpt.sba.scms.dto.response.ShiftResponse;
import edu.fpt.sba.scms.dto.response.ShiftScheduleResponse;
import edu.fpt.sba.scms.dto.response.ShiftSummaryResponse;
import edu.fpt.sba.scms.mapper.ShiftMapper;
import edu.fpt.sba.scms.model.Attendance;
import edu.fpt.sba.scms.model.Branch;
import edu.fpt.sba.scms.model.CashTransaction;
import edu.fpt.sba.scms.model.PreOpenCashAdjustment;
import edu.fpt.sba.scms.model.SecurityAlert;
import edu.fpt.sba.scms.model.Shift;
import edu.fpt.sba.scms.model.ShiftAssignment;
import edu.fpt.sba.scms.model.ShiftAssignmentId;
import edu.fpt.sba.scms.model.ShiftHandover;
import edu.fpt.sba.scms.model.User;
import edu.fpt.sba.scms.repository.AttendanceRepository;
import edu.fpt.sba.scms.repository.BranchRepository;
import edu.fpt.sba.scms.repository.CashTransactionRepository;
import edu.fpt.sba.scms.repository.PreOpenCashAdjustmentRepository;
import edu.fpt.sba.scms.repository.SecurityAlertRepository;
import edu.fpt.sba.scms.repository.ShiftAssignmentRepository;
import edu.fpt.sba.scms.repository.ShiftHandoverRepository;
import edu.fpt.sba.scms.repository.ShiftRepository;
import edu.fpt.sba.scms.repository.ShiftSpecs;
import edu.fpt.sba.scms.repository.UserRepository;
import edu.fpt.sba.scms.security.BranchContext;
import edu.fpt.sba.scms.security.principal.UserPrincipal;
import edu.fpt.sba.scms.service.ShiftService;
import edu.fpt.sba.scms.service.support.PinHasher;
import edu.fpt.sba.scms.service.support.PinLockoutService;
import edu.fpt.sba.scms.service.support.UserDirectory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShiftServiceImpl implements ShiftService {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int MAX_REASON_LEN = 500;
    private static final Set<String> SHIFT_STAFF_ROLES = Set.of(
            Roles.CASHIER.code(),
            Roles.BARISTA.code(),
            Roles.WAITER.code(),
            Roles.BRANCH_MANAGER.code()
    );

    private final ShiftRepository shiftRepository;
    private final CashTransactionRepository cashTransactionRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final ShiftHandoverRepository shiftHandoverRepository;
    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final SecurityAlertRepository securityAlertRepository;
    private final PreOpenCashAdjustmentRepository preOpenCashAdjustmentRepository;
    private final PinHasher pinHasher;
    private final PinLockoutService pinLockoutService;
    private final AuditLogService auditLogService;
    private final ShiftMapper shiftMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShiftResponse openShift(OpenShiftRequest request, UserPrincipal principal) {
        Integer branchId = BranchContext.resolveBranchScope(request.branchId());
        validateNoOpenShiftInBranch(branchId);

        Instant now = Instant.now();
        Shift shift = resolveShift(request, branchId, now);

        Shift previousClosed = shiftRepository
                .findFirstByBranchIdAndStatusOrderByClosedAtDescIdDesc(branchId, ShiftStatus.CLOSED)
                .orElse(null);

        List<PreOpenCashAdjustment> pendingPreOpen = preOpenCashAdjustmentRepository.findPendingByBranchIdForUpdate(branchId);
        BigDecimal preOpenTotal = pendingPreOpen.stream()
                .map(PreOpenCashAdjustment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal countedAtOpen = request.openingCash();
        if (preOpenTotal.compareTo(BigDecimal.ZERO) > 0 && countedAtOpen.compareTo(preOpenTotal) < 0) {
            throw new InvalidFieldValueException("openingCash",
                    "Tổng tiền thực đếm đầu ca (" + countedAtOpen.toPlainString()
                            + " đ) không thể nhỏ hơn tổng tiền Quản lý đã nạp đầu ngày ("
                            + preOpenTotal.toPlainString() + " đ).");
        }

        Integer previousShiftId = null;
        BigDecimal previousClosingCash = null;
        BigDecimal openingVariance = null;
        String trimmedNote = StringUtils.hasText(request.note()) ? request.note().trim() : null;

        if (previousClosed != null) {
            previousShiftId = previousClosed.getId();
            previousClosingCash = resolvePreviousClosingCash(previousClosed);
        }

        if (previousClosed != null || preOpenTotal.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal basePrev = previousClosingCash != null ? previousClosingCash : BigDecimal.ZERO;
            BigDecimal expectedOpeningTotal = basePrev.add(preOpenTotal);
            openingVariance = ShiftCashCalculator.calculateVariance(countedAtOpen, expectedOpeningTotal);
            if (openingVariance.compareTo(BigDecimal.ZERO) != 0 && !StringUtils.hasText(trimmedNote)) {
                String expectedDetail = preOpenTotal.compareTo(BigDecimal.ZERO) > 0
                        ? "dự kiến đầu ca (" + expectedOpeningTotal.toPlainString()
                                + " đ = Kết ca trước " + basePrev.toPlainString()
                                + " đ + Nạp đầu ngày " + preOpenTotal.toPlainString() + " đ)"
                        : "kết ca trước (" + basePrev.toPlainString() + " đ)";
                throw new BusinessRuleException(BusinessErrorCode.REASON_REQUIRED,
                        "Tiền đầu ca (" + countedAtOpen.toPlainString()
                                + " đ) lệch " + openingVariance.toPlainString()
                                + " đ so với " + expectedDetail
                                + ". Vui lòng nhập ghi chú bàn giao / giải trình chênh lệch đầu ca.");
            }
        }

        BigDecimal baseOpeningCash = countedAtOpen.subtract(preOpenTotal);
        Integer actorId = resolveActorId(principal);
        populateOpeningFields(shift, baseOpeningCash, countedAtOpen, preOpenTotal, actorId, now,
                previousShiftId, previousClosingCash, openingVariance, trimmedNote);

        Shift savedShift = shiftRepository.save(shift);
        recordOpeningCashTransaction(
                savedShift.getId(), baseOpeningCash, preOpenTotal, trimmedNote,
                previousClosingCash, openingVariance, actorId);

        if (!pendingPreOpen.isEmpty()) {
            List<CashTransaction> preOpenTxs = new ArrayList<>(pendingPreOpen.size());
            for (PreOpenCashAdjustment adj : pendingPreOpen) {
                Integer creator = adj.getCreatedBy() != null ? adj.getCreatedBy() : actorId;
                Integer approver = adj.getApprovedBy() != null ? adj.getApprovedBy() : creator;
                preOpenTxs.add(CashTransaction.builder()
                        .shiftId(savedShift.getId())
                        .type(CashTransactionType.CASH_IN)
                        .amount(adj.getAmount())
                        .reason(truncate("[Nạp đầu ngày] " + adj.getReason(), MAX_REASON_LEN))
                        .createdBy(creator)
                        .approvedBy(approver)
                        .isSelfInitiated(Objects.equals(creator, approver))
                        .createdAt(adj.getCreatedAt())
                        .build());
                adj.setAppliedShiftId(savedShift.getId());
                adj.setAppliedAt(now);
            }
            cashTransactionRepository.saveAll(preOpenTxs);
            preOpenCashAdjustmentRepository.saveAll(pendingPreOpen);
        }

        boolean autoCheckedIn = false;
        if (principal != null && Roles.CASHIER.code().equals(principal.role())) {
            autoCheckedIn = autoAssignAndCheckInCashier(
                    savedShift, principal.userId(), now, "Tự động check-in khi mở ca #" + savedShift.getId(), false);
        }

        String auditDetail = "Mở ca ID=" + savedShift.getId()
                + ", tiền đầu ca=" + baseOpeningCash
                + (preOpenTotal.compareTo(BigDecimal.ZERO) > 0 ? ", nạp đầu ngày=" + preOpenTotal + ", tổng thực két=" + countedAtOpen : "");
        if (previousClosingCash != null || preOpenTotal.compareTo(BigDecimal.ZERO) > 0) {
            auditDetail += ", kết ca trước=" + previousClosingCash + ", chênh lệch đầu ca=" + openingVariance;
        }
        if (autoCheckedIn) {
            auditDetail += ", autoCheckIn=true";
        }
        auditLogService.log(AuditAction.SHIFT_OPEN, "shift", savedShift.getId(), null, auditDetail);

        log.info("Mở ca thành công: shiftId={}, branchId={}, baseOpeningCash={}, preOpenCashIn={}, expectedCash={}, openingVariance={}, autoCheckedIn={}",
                savedShift.getId(), branchId, baseOpeningCash, preOpenTotal, countedAtOpen, openingVariance, autoCheckedIn);
        return enrichShiftResponse(savedShift);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CashTransactionResponse cashIn(Integer shiftId, CashAdjustmentRequest request, UserPrincipal principal) {
        ensureCanAdjustCash(principal);
        Shift shift = getOpenShiftForUpdateOrThrow(shiftId);
        if (shift.isHandoverPending()) {
            throw new BusinessRuleException(BusinessErrorCode.SHIFT_HANDOVER_PENDING,
                    "Két tiền đang tạm khóa chờ bàn giao giữa ca. Vui lòng hoàn tất hoặc hủy bàn giao trước khi nạp tiền.");
        }
        if (shift.isManagerClosePending()) {
            throw new BusinessRuleException(BusinessErrorCode.SHIFT_MANAGER_CLOSE_PENDING,
                    "Ca làm việc đã chốt kiểm két cuối ca và đang chờ Quản lý chi nhánh xác nhận nhận tiền / đóng ca.");
        }
        if (shift.isEarlyClosePending()) {
            throw new BusinessRuleException(BusinessErrorCode.SHIFT_EARLY_CLOSE_PENDING,
                    "Ca làm việc đang chờ Quản lý chi nhánh xác nhận đóng ca sớm. Vui lòng chờ duyệt hoặc hủy yêu cầu trước khi nạp tiền.");
        }

        User manager = resolveCashAdjustmentApprover(shift.getBranchId(), request.managerPin(), principal);
        boolean isSelf = principal != null && principal.userId().equals(manager.getId());
        Integer actorId = resolveActorId(principal);

        CashTransaction tx = CashTransaction.builder()
                .shiftId(shift.getId())
                .type(CashTransactionType.CASH_IN)
                .amount(request.amount())
                .reason(request.reason())
                .createdBy(actorId)
                .approvedBy(manager.getId())
                .isSelfInitiated(isSelf)
                .build();
        CashTransaction savedTx = cashTransactionRepository.save(tx);

        shift.setExpectedCash(ShiftCashCalculator.addCash(shift.getExpectedCash(), request.amount()));
        shiftRepository.save(shift);

        auditLogService.log(AuditAction.CASH_IN, "cash_transaction", savedTx.getId(), null,
                "Nạp tiền két shiftId=" + shift.getId() + ", số tiền=" + request.amount() + ", lý do=" + request.reason());

        log.info("Nạp tiền két thành công: shiftId={}, amount={}, managerId={}", shift.getId(), request.amount(),
                manager.getId());
        return enrichCashTransactionResponse(savedTx);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CashTransactionResponse cashOut(Integer shiftId, CashAdjustmentRequest request, UserPrincipal principal) {
        ensureCanAdjustCash(principal);
        Shift shift = getOpenShiftForUpdateOrThrow(shiftId);
        if (shift.isHandoverPending()) {
            throw new BusinessRuleException(BusinessErrorCode.SHIFT_HANDOVER_PENDING,
                    "Két tiền đang tạm khóa chờ bàn giao giữa ca. Vui lòng hoàn tất hoặc hủy bàn giao trước khi rút tiền.");
        }
        if (shift.isManagerClosePending()) {
            throw new BusinessRuleException(BusinessErrorCode.SHIFT_MANAGER_CLOSE_PENDING,
                    "Ca làm việc đã chốt kiểm két cuối ca và đang chờ Quản lý chi nhánh xác nhận nhận tiền / đóng ca.");
        }
        if (shift.isEarlyClosePending()) {
            throw new BusinessRuleException(BusinessErrorCode.SHIFT_EARLY_CLOSE_PENDING,
                    "Ca làm việc đang chờ Quản lý chi nhánh xác nhận đóng ca sớm. Vui lòng chờ duyệt hoặc hủy yêu cầu trước khi rút tiền.");
        }

        User manager = resolveCashAdjustmentApprover(shift.getBranchId(), request.managerPin(), principal);
        boolean isSelf = principal != null && principal.userId().equals(manager.getId());
        Integer actorId = resolveActorId(principal);

        if (!ShiftCashCalculator.isSufficientCash(shift.getExpectedCash(), request.amount())) {
            throw new BusinessRuleException(BusinessErrorCode.INSUFFICIENT_CASH,
                    "Số dư két tiền hiện tại (" + (shift.getExpectedCash() != null ? shift.getExpectedCash().toPlainString() : "0")
                            + " đ) không đủ để rút số tiền " + request.amount().toPlainString() + " đ.");
        }

        CashTransaction tx = CashTransaction.builder()
                .shiftId(shift.getId())
                .type(CashTransactionType.CASH_OUT)
                .amount(request.amount())
                .reason(request.reason())
                .createdBy(actorId)
                .approvedBy(manager.getId())
                .isSelfInitiated(isSelf)
                .build();
        CashTransaction savedTx = cashTransactionRepository.save(tx);

        shift.setExpectedCash(ShiftCashCalculator.deductCash(shift.getExpectedCash(), request.amount()));
        shiftRepository.save(shift);

        auditLogService.log(AuditAction.CASH_OUT, "cash_transaction", savedTx.getId(), null,
                "Rút tiền két shiftId=" + shift.getId() + ", số tiền=" + request.amount() + ", lý do=" + request.reason());

        log.info("Rút tiền két thành công: shiftId={}, amount={}, managerId={}", shift.getId(), request.amount(),
                manager.getId());
        return enrichCashTransactionResponse(savedTx);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShiftResponse initiateHandover(Integer shiftId, InitiateShiftHandoverRequest request, UserPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException(ErrorCode.AUTH_REQUIRED);
        }
        Shift shift = getOpenShiftForUpdateOrThrow(shiftId);
        if (shift.isHandoverPending()) {
            throw new BusinessRuleException(BusinessErrorCode.SHIFT_HANDOVER_PENDING,
                    "Ca làm việc đang có yêu cầu bàn giao két chờ xác nhận. Vui lòng hoàn tất hoặc hủy yêu cầu hiện tại trước.");
        }
        if (shift.isManagerClosePending()) {
            throw new BusinessRuleException(BusinessErrorCode.SHIFT_MANAGER_CLOSE_PENDING,
                    "Ca làm việc đã chốt kiểm két cuối ca và đang chờ Quản lý chi nhánh xác nhận nhận tiền / đóng ca.");
        }
        if (shift.isEarlyClosePending()) {
            throw new BusinessRuleException(BusinessErrorCode.SHIFT_EARLY_CLOSE_PENDING,
                    "Ca làm việc đang chờ Quản lý chi nhánh xác nhận đóng ca sớm. Vui lòng chờ duyệt hoặc hủy yêu cầu trước khi bàn giao két.");
        }

        Integer actorId = resolveActorId(principal);
        Integer fromUserId;
        if (isCashier(principal)) {
            if (!StringUtils.hasText(request.pin())) {
                throw new InvalidPinException("Thu ngân bắt buộc nhập mã PIN cá nhân (4–6 số) để xác nhận bàn giao két giữa ca.");
            }
            User verifiedCashier = verifyActorPinForUser(principal.userId(), shift.getBranchId(), request.pin(), "SHIFT_HANDOVER_OUT");
            fromUserId = verifiedCashier.getId();
        } else {
            if (StringUtils.hasText(request.pin())) {
                verifyActorPinForUser(principal.userId(), shift.getBranchId(), request.pin(), "SHIFT_HANDOVER_OUT");
            }
            fromUserId = shift.getActiveCashierId() != null
                    ? shift.getActiveCashierId()
                    : (shift.getOpenedBy() != null ? shift.getOpenedBy() : actorId);
        }

        if (request.expectedIncomingUserId() != null) {
            if (request.expectedIncomingUserId().equals(fromUserId)) {
                throw new BusinessRuleException(BusinessErrorCode.SELF_ACTION_FORBIDDEN,
                        "Người dự kiến nhận két phải khác người đang bàn giao.");
            }
            User expectedTarget = userRepository.findWithRoleAndBranchById(request.expectedIncomingUserId())
                    .orElseThrow(() -> new NotFoundException("nhân viên nhận két"));
            if (!expectedTarget.isActive()) {
                throw new InvalidFieldValueException("expectedIncomingUserId", "Tài khoản nhân viên nhận két đang ngừng hoạt động.");
            }
            if (!Roles.COO.code().equals(expectedTarget.roleCode()) && !shift.getBranchId().equals(expectedTarget.getBranchId())) {
                throw new ForbiddenException(ErrorCode.CROSS_BRANCH_DENIED);
            }
        }

        BigDecimal expectedAtOut = shift.getExpectedCash() != null ? shift.getExpectedCash() : shift.getOpeningCash();
        BigDecimal counted = request.countedCash();
        BigDecimal outgoingVariance = ShiftCashCalculator.calculateVariance(counted, expectedAtOut);
        String trimmedNote = StringUtils.hasText(request.note()) ? request.note().trim() : null;

        if (ShiftCashCalculator.hasDiscrepancy(counted, expectedAtOut) && !StringUtils.hasText(trimmedNote)) {
            throw new BusinessRuleException(BusinessErrorCode.REASON_REQUIRED,
                    "Số tiền thực đếm bàn giao (" + counted.toPlainString()
                            + " đ) lệch " + outgoingVariance.toPlainString()
                            + " đ so với sổ sách (" + expectedAtOut.toPlainString()
                            + " đ). Vui lòng nhập ghi chú giải trình.");
        }

        Instant now = Instant.now();
        ShiftHandover handover = ShiftHandover.builder()
                .shiftId(shift.getId())
                .branchId(shift.getBranchId())
                .fromUserId(fromUserId)
                .toUserId(request.expectedIncomingUserId())
                .status(ShiftHandoverStatus.PENDING_RECEIVE)
                .reason(request.reason().trim())
                .expectedCashAtOut(expectedAtOut)
                .outgoingCountedCash(counted)
                .outgoingVariance(outgoingVariance)
                .outgoingNote(trimmedNote)
                .outgoingAt(now)
                .outgoingConfirmedBy(actorId)
                .build();
        ShiftHandover savedHandover = shiftHandoverRepository.save(handover);

        shift.setHandoverPending(Boolean.TRUE);
        Shift savedShift = shiftRepository.save(shift);

        Long autoOutMinutes = fromUserId != null
                ? autoCheckOutForUserId(fromUserId, savedShift.getId(), now,
                        "Tự động check-out khi bàn giao két giữa ca #" + savedShift.getId())
                : null;

        auditLogService.log(AuditAction.SHIFT_CLOSE, "shift", savedShift.getId(), null,
                "Bàn giao két giữa ca (Chiều giao) shiftId=" + savedShift.getId()
                        + ", handoverId=" + savedHandover.getId()
                        + ", fromUserId=" + fromUserId
                        + ", countedOut=" + counted
                        + ", expectedAtOut=" + expectedAtOut
                        + ", varianceOut=" + outgoingVariance
                        + (autoOutMinutes != null ? ", autoCheckOutMinutes=" + autoOutMinutes : ""));

        log.info("Khởi tạo bàn giao két giữa ca: shiftId={}, handoverId={}, fromUserId={}, countedOut={}, varianceOut={}",
                savedShift.getId(), savedHandover.getId(), fromUserId, counted, outgoingVariance);
        return enrichShiftResponse(savedShift);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShiftResponse acceptHandover(Integer shiftId, AcceptShiftHandoverRequest request, UserPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException(ErrorCode.AUTH_REQUIRED);
        }
        Shift shift = getOpenShiftForUpdateOrThrow(shiftId);
        if (!shift.isHandoverPending()) {
            throw new BusinessRuleException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Ca làm việc hiện không ở trạng thái chờ bàn giao két giữa ca.");
        }

        ShiftHandover handover = shiftHandoverRepository
                .findFirstByShiftIdAndStatusOrderByIdDesc(shift.getId(), ShiftHandoverStatus.PENDING_RECEIVE)
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.INVALID_STATE_TRANSITION,
                        "Không tìm thấy phiếu bàn giao két đang chờ xác nhận."));

        Integer actorId = resolveActorId(principal);
        User incomingUser;
        if (isCashier(principal)) {
            if (request.incomingUserId() != null && !request.incomingUserId().equals(actorId)) {
                throw new ForbiddenException(ErrorCode.ACCESS_DENIED,
                        "Thu ngân chỉ được xác nhận nhận két cho chính tài khoản đang mở khóa.");
            }
            if (!StringUtils.hasText(request.pin())) {
                throw new InvalidPinException("Thu ngân bắt buộc nhập mã PIN cá nhân (4–6 số) để xác nhận nhận két.");
            }
            incomingUser = verifyActorPinForUser(actorId, shift.getBranchId(), request.pin(), "SHIFT_HANDOVER_IN");
        } else {
            Integer targetId = request.incomingUserId() != null ? request.incomingUserId() : actorId;
            User target = userRepository.findWithRoleAndBranchById(targetId)
                    .orElseThrow(() -> new NotFoundException("nhân viên nhận két"));
            if (!target.isActive()) {
                throw new UnauthorizedException(ErrorCode.ACCOUNT_INACTIVE);
            }
            if (!Roles.COO.code().equals(target.roleCode()) && !shift.getBranchId().equals(target.getBranchId())) {
                throw new ForbiddenException(ErrorCode.CROSS_BRANCH_DENIED);
            }
            if (!targetId.equals(actorId)) {
                if (!StringUtils.hasText(request.pin())) {
                    throw new InvalidPinException(
                            "Vui lòng để người nhận ca (" + target.getFullName() + ") nhập mã PIN cá nhân xác nhận nhận két.");
                }
                incomingUser = verifyActorPinForUser(targetId, shift.getBranchId(), request.pin(), "SHIFT_HANDOVER_IN");
            } else {
                if (StringUtils.hasText(request.pin())) {
                    verifyActorPinForUser(actorId, shift.getBranchId(), request.pin(), "SHIFT_HANDOVER_IN");
                }
                incomingUser = target;
            }
        }

        if (Objects.equals(handover.getFromUserId(), incomingUser.getId())) {
            throw new BusinessRuleException(BusinessErrorCode.SELF_ACTION_FORBIDDEN,
                    "Người nhận két phải khác người vừa bàn giao (" + incomingUser.getFullName()
                            + "). Nếu bạn tiếp tục đứng quầy, hãy bấm Hủy bàn giao thay vì tự nhận lại.");
        }

        BigDecimal counted = request.countedCash();
        BigDecimal varFromOut = ShiftCashCalculator.calculateVariance(counted, handover.getOutgoingCountedCash());
        BigDecimal varFromBook = ShiftCashCalculator.calculateVariance(counted, handover.getExpectedCashAtOut());
        String trimmedNote = StringUtils.hasText(request.note()) ? request.note().trim() : null;

        if (ShiftCashCalculator.hasDiscrepancy(counted, handover.getOutgoingCountedCash()) && !StringUtils.hasText(trimmedNote)) {
            throw new BusinessRuleException(BusinessErrorCode.REASON_REQUIRED,
                    "Số tiền người nhận kiểm đếm (" + counted.toPlainString()
                            + " đ) lệch " + varFromOut.toPlainString()
                            + " đ so với số bàn giao (" + handover.getOutgoingCountedCash().toPlainString()
                            + " đ). Vui lòng nhập ghi chú giải trình.");
        }

        Instant now = Instant.now();
        handover.setStatus(ShiftHandoverStatus.COMPLETED);
        handover.setToUserId(incomingUser.getId());
        handover.setIncomingCountedCash(counted);
        handover.setIncomingVarianceFromOut(varFromOut);
        handover.setIncomingVarianceFromBook(varFromBook);
        handover.setIncomingNote(trimmedNote);
        handover.setIncomingAt(now);
        handover.setIncomingConfirmedBy(actorId);
        shiftHandoverRepository.save(handover);

        shift.setHandoverPending(Boolean.FALSE);
        shift.setActiveCashierId(incomingUser.getId());
        Shift savedShift = shiftRepository.save(shift);

        boolean autoCheckedIn = false;
        if (incomingUser.getPosition() != null && UserDirectory.BRANCH_STAFF_ROLES.contains(incomingUser.getPosition())) {
            autoCheckedIn = autoAssignAndCheckInCashier(
                    savedShift,
                    incomingUser.getId(),
                    now,
                    "Tự động check-in khi nhận bàn giao két ca #" + savedShift.getId()
                            + (Roles.CASHIER.code().equals(incomingUser.roleCode()) ? "" : " (Gán tạm vai trò Thu ngân - Nhân sự thay thế)"),
                    true);
        }

        auditLogService.log(AuditAction.SHIFT_OPEN, "shift", savedShift.getId(), null,
                "Bàn giao két giữa ca (Chiều nhận) shiftId=" + savedShift.getId()
                        + ", handoverId=" + handover.getId()
                        + ", fromUserId=" + handover.getFromUserId()
                        + ", toUserId=" + incomingUser.getId()
                        + ", countedIn=" + counted
                        + ", varFromOut=" + varFromOut
                        + ", varFromBook=" + varFromBook
                        + ", autoCheckIn=" + autoCheckedIn);

        log.info("Hoàn tất bàn giao két giữa ca: shiftId={}, handoverId={}, toUserId={}, countedIn={}, varFromOut={}",
                savedShift.getId(), handover.getId(), incomingUser.getId(), counted, varFromOut);
        return enrichShiftResponse(savedShift);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShiftResponse cancelHandover(Integer shiftId, CancelShiftHandoverRequest request, UserPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException(ErrorCode.AUTH_REQUIRED);
        }
        Shift shift = getOpenShiftForUpdateOrThrow(shiftId);
        if (!shift.isHandoverPending()) {
            throw new BusinessRuleException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Ca làm việc hiện không có yêu cầu bàn giao két nào đang chờ nhận.");
        }

        ShiftHandover handover = shiftHandoverRepository
                .findFirstByShiftIdAndStatusOrderByIdDesc(shift.getId(), ShiftHandoverStatus.PENDING_RECEIVE)
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.INVALID_STATE_TRANSITION,
                        "Không tìm thấy phiếu bàn giao két đang chờ nhận."));

        Integer actorId = resolveActorId(principal);
        if (isCashier(principal)) {
            boolean isInitiator = actorId.equals(handover.getFromUserId()) || actorId.equals(handover.getOutgoingConfirmedBy());
            if (!isInitiator) {
                throw new ForbiddenException(ErrorCode.ACCESS_DENIED,
                        "Chỉ Quản lý chi nhánh, COO hoặc người vừa khởi tạo bàn giao mới có quyền hủy yêu cầu này.");
            }
        }

        String cancelReason = request != null && StringUtils.hasText(request.reason())
                ? request.reason().trim()
                : "Hủy yêu cầu bàn giao két giữa ca";

        handover.setStatus(ShiftHandoverStatus.CANCELLED);
        handover.setCancelledAt(Instant.now());
        handover.setCancelledBy(actorId);
        handover.setCancelReason(cancelReason);
        shiftHandoverRepository.save(handover);

        shift.setHandoverPending(Boolean.FALSE);
        Shift savedShift = shiftRepository.save(shift);

        if (isCashier(principal) && Objects.equals(actorId, handover.getFromUserId())) {
            autoAssignAndCheckInCashier(
                    savedShift,
                    actorId,
                    Instant.now(),
                    "Tự động check-in lại sau khi hủy bàn giao két ca #" + savedShift.getId(),
                    false);
        }

        auditLogService.log(AuditAction.SHIFT_OPEN, "shift", savedShift.getId(), "PENDING_RECEIVE",
                "Hủy bàn giao két shiftId=" + savedShift.getId() + ", handoverId=" + handover.getId() + ", lý do=" + cancelReason);

        log.info("Đã hủy yêu cầu bàn giao két: shiftId={}, handoverId={}, by={}",
                savedShift.getId(), handover.getId(), actorId);
        return enrichShiftResponse(savedShift);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShiftResponse closeShift(Integer shiftId, CloseShiftRequest request, UserPrincipal principal) {
        Shift shift = getOpenShiftForUpdateOrThrow(shiftId);

        if (shift.isHandoverPending() && isCashier(principal)) {
            throw new BusinessRuleException(BusinessErrorCode.SHIFT_HANDOVER_PENDING,
                    "Két tiền đang chờ xác nhận bàn giao giữa ca. Vui lòng hoàn tất kiểm két nhận ca (hoặc hủy bàn giao) trước khi đóng ca.");
        }
        if (shift.isManagerClosePending() && isCashier(principal)) {
            throw new BusinessRuleException(BusinessErrorCode.SHIFT_MANAGER_CLOSE_PENDING,
                    "Ca #" + shift.getId()
                            + " đã chốt kiểm két cuối ca và đang chờ Quản lý chi nhánh xác nhận nhận tiền / đóng ca.");
        }
        if (shift.isEarlyClosePending() && isCashier(principal)) {
            throw new BusinessRuleException(BusinessErrorCode.SHIFT_EARLY_CLOSE_PENDING,
                    "Yêu cầu đóng ca sớm cho ca #" + shift.getId()
                            + " đã được gửi và đang chờ Quản lý chi nhánh xác nhận. Vui lòng chờ Quản lý duyệt hoặc hủy yêu cầu để tiếp tục ca.");
        }
        boolean handoverToManager = Boolean.TRUE.equals(request.handoverToManager());
        if (handoverToManager && !isCashier(principal)) {
            throw new BusinessRuleException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Quản lý / COO đang trực tiếp thao tác không cần gửi bàn giao cho chính mình; vui lòng đóng ca trực tiếp và rút tiền sau kết ca nếu cần.");
        }

        BigDecimal expected = shift.getExpectedCash() != null ? shift.getExpectedCash() : shift.getOpeningCash();
        BigDecimal counted = request.countedCash();
        BigDecimal variance = ShiftCashCalculator.calculateVariance(counted, expected);
        String discrepancyReason = StringUtils.hasText(request.discrepancyReason())
                ? request.discrepancyReason().trim()
                : null;

        if (ShiftCashCalculator.hasDiscrepancy(counted, expected) && !StringUtils.hasText(discrepancyReason)) {
            throw new BusinessRuleException(BusinessErrorCode.REASON_REQUIRED,
                    "Phát hiện chênh lệch tiền mặt thực tế so với sổ sách (" + variance.toPlainString()
                            + " đ). Bắt buộc phải nhập lý do giải trình.");
        }

        if (handoverToManager && !StringUtils.hasText(request.pin())) {
            throw new InvalidPinException(
                    "Thu ngân bắt buộc nhập mã PIN cá nhân (4–6 số) để xác nhận kiểm két và bàn giao cuối ca cho Quản lý.");
        }

        User pinVerifiedActor = StringUtils.hasText(request.pin())
                ? verifyActorPinForShiftClose(principal, shift.getBranchId(), request.pin())
                : null;

        Instant now = Instant.now();
        Integer actorId = resolveActorId(principal);

        if (handoverToManager) {
            boolean earlyClose = ShiftSchedulePolicy.isEarlyClose(shift.getPlannedEnd(), now);
            String handoverReasonText = StringUtils.hasText(request.handoverReason())
                    ? request.handoverReason().trim()
                    : null;
            String earlyReasonText = StringUtils.hasText(request.earlyCloseReason())
                    ? request.earlyCloseReason().trim()
                    : null;
            String combinedReason;
            if (handoverReasonText != null && earlyReasonText != null && !handoverReasonText.equals(earlyReasonText)) {
                combinedReason = handoverReasonText + " (" + earlyReasonText + ")";
            } else if (handoverReasonText != null) {
                combinedReason = handoverReasonText;
            } else if (earlyReasonText != null) {
                combinedReason = "Đóng ca sớm: " + earlyReasonText;
            } else if (earlyClose) {
                combinedReason = "Bàn giao két cuối ca & đóng ca sớm chờ Quản lý xác nhận";
            } else {
                combinedReason = "Bàn giao két cuối ca chờ Quản lý xác nhận nhận tiền & đóng ca";
            }

            Integer fromUserId = pinVerifiedActor != null
                    ? pinVerifiedActor.getId()
                    : (actorId != null ? actorId : shift.getActiveCashierId());

            ShiftHandover handover = ShiftHandover.builder()
                    .shiftId(shift.getId())
                    .branchId(shift.getBranchId())
                    .fromUserId(fromUserId)
                    .toUserId(null)
                    .handoverType(ShiftHandoverType.MANAGER_CLOSE)
                    .status(ShiftHandoverStatus.PENDING_RECEIVE)
                    .reason(truncate(combinedReason, MAX_REASON_LEN))
                    .expectedCashAtOut(expected)
                    .outgoingCountedCash(counted)
                    .outgoingVariance(variance)
                    .outgoingNote(discrepancyReason)
                    .outgoingAt(now)
                    .outgoingConfirmedBy(actorId)
                    .build();
            ShiftHandover savedHandover = shiftHandoverRepository.save(handover);

            shift.setManagerClosePending(Boolean.TRUE);
            Shift savedShift = shiftRepository.save(shift);

            Set<Integer> checkoutIds = new LinkedHashSet<>();
            if (fromUserId != null) {
                checkoutIds.add(fromUserId);
            }
            if (shift.getActiveCashierId() != null) {
                checkoutIds.add(shift.getActiveCashierId());
            }
            List<Long> autoOutMinutesList = new ArrayList<>();
            for (Integer uid : checkoutIds) {
                boolean isPrimary = fromUserId != null && uid.equals(fromUserId);
                Long mins = isPrimary
                        ? autoCheckOutForUserId(uid, savedShift.getId(), now,
                                "Tự động check-out khi bàn giao két cuối ca #" + savedShift.getId())
                        : autoCheckOutIfBranchStaff(uid, savedShift.getId(), now,
                                "Tự động check-out khi bàn giao két cuối ca #" + savedShift.getId());
                if (mins != null) {
                    autoOutMinutesList.add(mins);
                }
            }

            String cashierName = resolveActorDisplayName(pinVerifiedActor, principal, fromUserId);
            createManagerCloseAlert(
                    savedShift, fromUserId, cashierName, counted, expected, variance,
                    combinedReason, discrepancyReason, earlyClose);

            auditLogService.log(AuditAction.SHIFT_CLOSE, "shift", savedShift.getId(), "OPEN",
                    "Thu ngân gửi bàn giao két cuối ca chờ Quản lý xác nhận shiftId=" + savedShift.getId()
                            + ", handoverId=" + savedHandover.getId()
                            + ", fromUserId=" + fromUserId
                            + ", countedOut=" + counted
                            + ", expected=" + expected
                            + ", variance=" + variance
                            + (!autoOutMinutesList.isEmpty() ? ", autoCheckOutMinutes=" + autoOutMinutesList : ""));

            log.info("Đã gửi bàn giao két cuối ca chờ Quản lý nhận: shiftId={}, handoverId={}, fromUserId={}, counted={}, variance={}, autoCheckOutMinutes={}",
                    savedShift.getId(), savedHandover.getId(), fromUserId, counted, variance, autoOutMinutesList);
            return enrichShiftResponse(savedShift);
        }

        if (isCashier(principal) && ShiftSchedulePolicy.isEarlyClose(shift.getPlannedEnd(), now)) {
            String earlyReason = StringUtils.hasText(request.earlyCloseReason())
                    ? truncate(request.earlyCloseReason().trim(), MAX_REASON_LEN)
                    : null;
            shift.setEarlyClosePending(Boolean.TRUE);
            shift.setEarlyCloseRequestedBy(actorId);
            shift.setEarlyCloseRequestedAt(now);
            shift.setEarlyCloseCountedCash(counted);
            shift.setEarlyCloseVariance(variance);
            shift.setEarlyCloseDiscrepancyReason(discrepancyReason);
            shift.setEarlyCloseReason(earlyReason);

            Shift savedShift = shiftRepository.save(shift);
            String requesterName = resolveActorDisplayName(pinVerifiedActor, principal, actorId);
            createEarlyCloseAlert(
                    savedShift, actorId, requesterName, counted, expected, variance, earlyReason, discrepancyReason);

            auditLogService.log(AuditAction.SHIFT_CLOSE, "shift", savedShift.getId(), "OPEN",
                    "Yêu cầu đóng ca sớm chờ Quản lý duyệt shiftId=" + savedShift.getId()
                            + ", requesterId=" + actorId
                            + ", Counted=" + counted
                            + ", Expected=" + expected
                            + ", Variance=" + variance
                            + (earlyReason != null ? ", Reason=" + earlyReason : ""));

            log.info("Đã gửi yêu cầu đóng ca sớm chờ Quản lý xác nhận: shiftId={}, requesterId={}, plannedEnd={}, counted={}, variance={}",
                    savedShift.getId(), actorId, savedShift.getPlannedEnd(), counted, variance);
            return enrichShiftResponse(savedShift);
        }

        Integer previousEarlyRequesterId = shift.getEarlyCloseRequestedBy();
        Integer previousActiveCashierId = shift.getActiveCashierId();
        if (shift.isEarlyClosePending()) {
            resolveEarlyCloseAlerts(shift.getId(), SecurityAlertStatus.APPROVED, principal, actorId, now,
                    "Quản lý / COO xác nhận đóng ca trực tiếp cho ca #" + shift.getId() + ".");
            shift.setEarlyClosePending(Boolean.FALSE);
        }

        if (shift.isManagerClosePending()) {
            resolveManagerCloseAlerts(shift.getId(), SecurityAlertStatus.APPROVED, principal, actorId, now,
                    "Quản lý / COO xác nhận đóng ca trực tiếp cho ca #" + shift.getId() + ".");
            cancelPendingHandoverOnForceClose(shift.getId(), actorId, now);
            shift.setManagerClosePending(Boolean.FALSE);
        }

        if (shift.isHandoverPending()) {
            cancelPendingHandoverOnForceClose(shift.getId(), actorId, now);
            shift.setHandoverPending(Boolean.FALSE);
        }

        shift.setStatus(ShiftStatus.CLOSED);
        shift.setCountedCash(counted);
        shift.setVariance(variance);
        shift.setDiscrepancyReason(discrepancyReason);
        shift.setClosedBy(actorId);
        shift.setClosedAt(now);

        Shift savedShift = shiftRepository.save(shift);

        CashTransaction closeTx = CashTransaction.builder()
                .shiftId(savedShift.getId())
                .type(CashTransactionType.CLOSE)
                .amount(counted)
                .reason("Đóng ca & kiểm kê két: Thực tế=" + counted + ", Chênh lệch=" + variance)
                .createdBy(actorId)
                .isSelfInitiated(false)
                .build();
        cashTransactionRepository.save(closeTx);

        Set<Integer> checkoutIds = new LinkedHashSet<>();
        if (pinVerifiedActor != null) {
            checkoutIds.add(pinVerifiedActor.getId());
        }
        if (previousEarlyRequesterId != null) {
            checkoutIds.add(previousEarlyRequesterId);
        }
        if (previousActiveCashierId != null) {
            checkoutIds.add(previousActiveCashierId);
        }
        List<Long> autoOutMinutesList = new ArrayList<>();
        for (Integer uid : checkoutIds) {
            boolean isPinActor = pinVerifiedActor != null && uid.equals(pinVerifiedActor.getId());
            Long mins = isPinActor
                    ? autoCheckOutForUserId(uid, savedShift.getId(), now,
                            "Tự động check-out khi đóng ca #" + savedShift.getId())
                    : autoCheckOutIfBranchStaff(uid, savedShift.getId(), now,
                            "Tự động check-out khi đóng ca #" + savedShift.getId());
            if (mins != null) {
                autoOutMinutesList.add(mins);
            }
        }

        String auditDetail = "Đóng ca ID=" + savedShift.getId()
                + ", Counted=" + counted
                + ", Expected=" + expected
                + ", Variance=" + variance;
        if (pinVerifiedActor != null) {
            auditDetail += ", pinVerified=true";
        }
        if (!autoOutMinutesList.isEmpty()) {
            auditDetail += ", autoCheckOutWorkedMinutes=" + autoOutMinutesList;
        }
        auditLogService.log(AuditAction.SHIFT_CLOSE, "shift", savedShift.getId(), null, auditDetail);

        log.info("Đóng ca thành công: shiftId={}, counted={}, expected={}, variance={}, autoCheckOutMinutes={}",
                savedShift.getId(), counted, expected, variance, autoOutMinutesList);
        return enrichShiftResponse(savedShift);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShiftResponse approveEarlyClose(Integer shiftId, ApproveEarlyCloseRequest request, UserPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException(ErrorCode.AUTH_REQUIRED);
        }
        Shift shift = getOpenShiftForUpdateOrThrow(shiftId);
        if (!shift.isEarlyClosePending()) {
            throw new BusinessRuleException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Ca #" + shift.getId() + " hiện không có yêu cầu đóng ca sớm nào đang chờ xác nhận.");
        }

        Integer actorId = resolveActorId(principal);
        if (request != null && StringUtils.hasText(request.pin())) {
            verifyActorPinForUser(actorId, shift.getBranchId(), request.pin(), "SHIFT_EARLY_CLOSE_APPROVE");
        }

        BigDecimal counted = (request != null && request.countedCash() != null)
                ? request.countedCash()
                : shift.getEarlyCloseCountedCash();
        if (counted == null) {
            throw new InvalidFieldValueException("countedCash", "Số tiền kiểm đếm thực tế không được để trống.");
        }

        BigDecimal expected = shift.getExpectedCash() != null ? shift.getExpectedCash() : shift.getOpeningCash();
        BigDecimal variance = ShiftCashCalculator.calculateVariance(counted, expected);
        String discrepancyReason = request != null && StringUtils.hasText(request.discrepancyReason())
                ? request.discrepancyReason().trim()
                : shift.getEarlyCloseDiscrepancyReason();

        if (ShiftCashCalculator.hasDiscrepancy(counted, expected) && !StringUtils.hasText(discrepancyReason)) {
            throw new BusinessRuleException(BusinessErrorCode.REASON_REQUIRED,
                    "Phát hiện chênh lệch tiền mặt thực tế so với sổ sách (" + variance.toPlainString()
                            + " đ). Bắt buộc phải nhập lý do giải trình.");
        }

        Instant now = Instant.now();
        Integer requesterId = shift.getEarlyCloseRequestedBy();
        Integer activeCashierId = shift.getActiveCashierId();

        if (shift.isHandoverPending()) {
            cancelPendingHandoverOnForceClose(shift.getId(), actorId, now);
            shift.setHandoverPending(Boolean.FALSE);
        }

        shift.setEarlyClosePending(Boolean.FALSE);
        shift.setStatus(ShiftStatus.CLOSED);
        shift.setCountedCash(counted);
        shift.setVariance(variance);
        shift.setDiscrepancyReason(discrepancyReason);
        shift.setClosedBy(actorId);
        shift.setClosedAt(now);

        Shift savedShift = shiftRepository.save(shift);

        String approveNote = request != null && StringUtils.hasText(request.approveNote())
                ? request.approveNote().trim()
                : null;
        String txReason = "Duyệt đóng ca sớm & kiểm kê két: Thực tế=" + counted + ", Chênh lệch=" + variance
                + (approveNote != null ? " — " + approveNote : "");
        CashTransaction closeTx = CashTransaction.builder()
                .shiftId(savedShift.getId())
                .type(CashTransactionType.CLOSE)
                .amount(counted)
                .reason(truncate(txReason, MAX_REASON_LEN))
                .createdBy(requesterId != null ? requesterId : actorId)
                .approvedBy(actorId)
                .isSelfInitiated(false)
                .build();
        cashTransactionRepository.save(closeTx);

        Set<Integer> checkoutIds = new LinkedHashSet<>();
        if (requesterId != null) {
            checkoutIds.add(requesterId);
        }
        if (activeCashierId != null) {
            checkoutIds.add(activeCashierId);
        }
        List<Long> autoOutMinutes = new ArrayList<>();
        for (Integer uid : checkoutIds) {
            boolean isRequester = requesterId != null && requesterId.equals(uid);
            Long mins = isRequester
                    ? autoCheckOutForUserId(uid, savedShift.getId(), now,
                            "Tự động check-out khi Quản lý duyệt đóng ca sớm #" + savedShift.getId())
                    : autoCheckOutIfBranchStaff(uid, savedShift.getId(), now,
                            "Tự động check-out khi Quản lý duyệt đóng ca sớm #" + savedShift.getId());
            if (mins != null) {
                autoOutMinutes.add(mins);
            }
        }

        String alertNote = approveNote != null
                ? approveNote
                : "Đã duyệt đóng ca sớm #" + savedShift.getId() + " & tự động Check-out Thu ngân.";
        resolveEarlyCloseAlerts(savedShift.getId(), SecurityAlertStatus.APPROVED, principal, actorId, now, alertNote);

        auditLogService.log(AuditAction.SHIFT_CLOSE, "shift", savedShift.getId(), "EARLY_CLOSE_PENDING",
                "Quản lý duyệt đóng ca sớm ID=" + savedShift.getId()
                        + ", requesterId=" + requesterId
                        + ", Counted=" + counted
                        + ", Expected=" + expected
                        + ", Variance=" + variance
                        + (!autoOutMinutes.isEmpty() ? ", autoCheckOutWorkedMinutes=" + autoOutMinutes : "")
                        + (approveNote != null ? ", note=" + approveNote : ""));

        log.info("Đã duyệt đóng ca sớm thành công: shiftId={}, approverId={}, requesterId={}, counted={}, variance={}, autoCheckOutMinutes={}",
                savedShift.getId(), actorId, requesterId, counted, variance, autoOutMinutes);
        return enrichShiftResponse(savedShift);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShiftResponse rejectEarlyClose(Integer shiftId, RejectEarlyCloseRequest request, UserPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException(ErrorCode.AUTH_REQUIRED);
        }
        Shift shift = getOpenShiftForUpdateOrThrow(shiftId);
        if (!shift.isEarlyClosePending()) {
            throw new BusinessRuleException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Ca #" + shift.getId() + " hiện không ở trạng thái chờ duyệt đóng ca sớm.");
        }

        Integer actorId = resolveActorId(principal);
        if (isCashier(principal)) {
            boolean isSelfRequester = actorId != null
                    && (actorId.equals(shift.getEarlyCloseRequestedBy()) || actorId.equals(shift.getActiveCashierId()));
            if (!isSelfRequester) {
                throw new ForbiddenException(ErrorCode.ACCESS_DENIED,
                        "Chỉ Quản lý chi nhánh, COO hoặc Thu ngân đã gửi yêu cầu mới có quyền hủy yêu cầu đóng ca sớm này.");
            }
        }

        Instant now = Instant.now();
        boolean selfCancelled = isCashier(principal);
        String rejectReason = request != null && StringUtils.hasText(request.reason())
                ? request.reason().trim()
                : (selfCancelled
                        ? "Thu ngân tự hủy yêu cầu đóng ca sớm để tiếp tục bán hàng"
                        : "Quản lý chi nhánh từ chối đóng ca sớm — tiếp tục mở lại quầy POS");

        shift.setEarlyClosePending(Boolean.FALSE);
        shift.setEarlyCloseRequestedBy(null);
        shift.setEarlyCloseRequestedAt(null);
        shift.setEarlyCloseCountedCash(null);
        shift.setEarlyCloseVariance(null);
        shift.setEarlyCloseDiscrepancyReason(null);
        shift.setEarlyCloseReason(null);

        Shift savedShift = shiftRepository.save(shift);
        resolveEarlyCloseAlerts(savedShift.getId(), SecurityAlertStatus.REJECTED, principal, actorId, now, rejectReason);

        auditLogService.log(AuditAction.SHIFT_OPEN, "shift", savedShift.getId(), "EARLY_CLOSE_PENDING",
                (selfCancelled ? "Thu ngân tự hủy" : "Quản lý từ chối")
                        + " yêu cầu đóng ca sớm shiftId=" + savedShift.getId()
                        + ", lý do=" + rejectReason);

        log.info("Đã từ chối / hủy yêu cầu đóng ca sớm: shiftId={}, actorId={}, selfCancelled={}",
                savedShift.getId(), actorId, selfCancelled);
        return enrichShiftResponse(savedShift);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShiftResponse approveManagerClose(Integer shiftId, ApproveManagerCloseRequest request, UserPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException(ErrorCode.AUTH_REQUIRED);
        }
        Shift shift = getOpenShiftForUpdateOrThrow(shiftId);
        if (!shift.isManagerClosePending()) {
            throw new BusinessRuleException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Ca #" + shift.getId() + " hiện không ở trạng thái chờ Quản lý xác nhận nhận két cuối ca.");
        }

        ShiftHandover handover = shiftHandoverRepository
                .findFirstByShiftIdAndStatusAndHandoverTypeOrderByIdDesc(
                        shift.getId(), ShiftHandoverStatus.PENDING_RECEIVE, ShiftHandoverType.MANAGER_CLOSE)
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.INVALID_STATE_TRANSITION,
                        "Không tìm thấy phiếu bàn giao két cuối ca đang chờ Quản lý xác nhận."));

        Integer actorId = resolveActorId(principal);
        if (request != null && StringUtils.hasText(request.pin())) {
            verifyActorPinForUser(actorId, shift.getBranchId(), request.pin(), "MANAGER_CLOSE_APPROVE");
        }

        BigDecimal incomingCounted = (request != null && request.countedCash() != null)
                ? request.countedCash()
                : handover.getOutgoingCountedCash();
        if (incomingCounted == null) {
            throw new InvalidFieldValueException("countedCash", "Số tiền Quản lý kiểm đếm thực tế không được để trống.");
        }

        BigDecimal expectedBefore = shift.getExpectedCash() != null ? shift.getExpectedCash() : shift.getOpeningCash();
        BigDecimal varFromOut = ShiftCashCalculator.calculateVariance(incomingCounted, handover.getOutgoingCountedCash());
        BigDecimal varFromBook = ShiftCashCalculator.calculateVariance(incomingCounted, expectedBefore);
        String managerDiscrepancyReason = request != null && StringUtils.hasText(request.discrepancyReason())
                ? request.discrepancyReason().trim()
                : null;
        String approveNote = request != null && StringUtils.hasText(request.approveNote())
                ? request.approveNote().trim()
                : null;

        if (varFromOut.compareTo(BigDecimal.ZERO) != 0 && !StringUtils.hasText(managerDiscrepancyReason)) {
            throw new BusinessRuleException(BusinessErrorCode.REASON_REQUIRED,
                    "Số tiền Quản lý kiểm đếm (" + incomingCounted.toPlainString()
                            + " đ) lệch " + varFromOut.toPlainString()
                            + " đ so với số Thu ngân bàn giao (" + handover.getOutgoingCountedCash().toPlainString()
                            + " đ). Vui lòng nhập lý do giải trình.");
        }
        if (varFromBook.compareTo(BigDecimal.ZERO) != 0
                && !StringUtils.hasText(managerDiscrepancyReason)
                && !StringUtils.hasText(handover.getOutgoingNote())) {
            throw new BusinessRuleException(BusinessErrorCode.REASON_REQUIRED,
                    "Phát hiện chênh lệch tiền mặt so với sổ sách (" + varFromBook.toPlainString()
                            + " đ). Vui lòng nhập lý do giải trình.");
        }

        BigDecimal withdrawAmount = (request != null && request.withdrawAmount() != null)
                ? request.withdrawAmount()
                : BigDecimal.ZERO;
        if (withdrawAmount.compareTo(BigDecimal.ZERO) > 0 && withdrawAmount.compareTo(incomingCounted) > 0) {
            throw new BusinessRuleException(BusinessErrorCode.INSUFFICIENT_CASH,
                    "Số tiền rút cất két Quản lý (" + withdrawAmount.toPlainString()
                            + " đ) không được vượt quá số tiền thực tế kiểm đếm trong két ("
                            + incomingCounted.toPlainString() + " đ).");
        }

        Instant now = Instant.now();
        String withdrawReason = null;
        BigDecimal expectedAfter = expectedBefore;
        if (withdrawAmount.compareTo(BigDecimal.ZERO) > 0) {
            withdrawReason = (request != null && StringUtils.hasText(request.withdrawReason()))
                    ? request.withdrawReason().trim()
                    : "Rút tiền cất két Quản lý khi xác nhận kết ca #" + shift.getId();
            CashTransaction withdrawTx = CashTransaction.builder()
                    .shiftId(shift.getId())
                    .type(CashTransactionType.CASH_OUT)
                    .amount(withdrawAmount)
                    .reason(truncate(withdrawReason, MAX_REASON_LEN))
                    .createdBy(actorId)
                    .approvedBy(actorId)
                    .isSelfInitiated(true)
                    .build();
            CashTransaction savedWithdrawTx = cashTransactionRepository.save(withdrawTx);
            expectedAfter = ShiftCashCalculator.deductCash(expectedBefore, withdrawAmount);

            auditLogService.log(AuditAction.CASH_OUT, "cash_transaction", savedWithdrawTx.getId(), null,
                    "Quản lý rút cất két khi nhận bàn giao cuối ca shiftId=" + shift.getId()
                            + ", số tiền=" + withdrawAmount + ", lý do=" + withdrawReason);
        }

        handover.setStatus(ShiftHandoverStatus.COMPLETED);
        handover.setToUserId(actorId);
        handover.setIncomingCountedCash(incomingCounted);
        handover.setIncomingVarianceFromOut(varFromOut);
        handover.setIncomingVarianceFromBook(varFromBook);
        handover.setIncomingNote(truncate(buildIncomingHandoverNote(managerDiscrepancyReason, approveNote), MAX_REASON_LEN));
        handover.setIncomingAt(now);
        handover.setIncomingConfirmedBy(actorId);
        handover.setWithdrawAmount(withdrawAmount.compareTo(BigDecimal.ZERO) > 0 ? withdrawAmount : BigDecimal.ZERO);
        handover.setWithdrawReason(truncate(withdrawReason, MAX_REASON_LEN));
        shiftHandoverRepository.save(handover);

        BigDecimal remainingCounted = incomingCounted.subtract(withdrawAmount);
        BigDecimal finalVariance = ShiftCashCalculator.calculateVariance(remainingCounted, expectedAfter);
        String finalDiscrepancyReason = StringUtils.hasText(managerDiscrepancyReason)
                ? managerDiscrepancyReason
                : handover.getOutgoingNote();

        if (shift.isHandoverPending()) {
            cancelPendingHandoverOnForceClose(shift.getId(), actorId, now);
            shift.setHandoverPending(Boolean.FALSE);
        }

        shift.setManagerClosePending(Boolean.FALSE);
        shift.setStatus(ShiftStatus.CLOSED);
        shift.setExpectedCash(expectedAfter);
        shift.setCountedCash(remainingCounted);
        shift.setVariance(finalVariance);
        shift.setDiscrepancyReason(truncate(finalDiscrepancyReason, MAX_REASON_LEN));
        shift.setClosedBy(actorId);
        shift.setClosedAt(now);
        Shift savedShift = shiftRepository.save(shift);

        String closeReason = "Xác nhận bàn giao két & đóng ca: Kiểm nhận=" + incomingCounted
                + (withdrawAmount.compareTo(BigDecimal.ZERO) > 0
                        ? ", Rút cất két QL=" + withdrawAmount + ", Lưu két=" + remainingCounted
                        : "")
                + ", Chênh lệch=" + finalVariance
                + (approveNote != null ? " — " + approveNote : "");
        CashTransaction closeTx = CashTransaction.builder()
                .shiftId(savedShift.getId())
                .type(CashTransactionType.CLOSE)
                .amount(remainingCounted)
                .reason(truncate(closeReason, MAX_REASON_LEN))
                .createdBy(handover.getFromUserId() != null ? handover.getFromUserId() : actorId)
                .approvedBy(actorId)
                .isSelfInitiated(false)
                .build();
        cashTransactionRepository.save(closeTx);

        Set<Integer> safetyCheckoutIds = new LinkedHashSet<>();
        if (handover.getFromUserId() != null) {
            safetyCheckoutIds.add(handover.getFromUserId());
        }
        if (savedShift.getActiveCashierId() != null) {
            safetyCheckoutIds.add(savedShift.getActiveCashierId());
        }
        for (Integer uid : safetyCheckoutIds) {
            autoCheckOutIfBranchStaff(uid, savedShift.getId(), now,
                    "Tự động check-out khi Quản lý chốt đóng ca #" + savedShift.getId());
        }

        String alertNote = approveNote != null
                ? approveNote
                : "Quản lý đã nhận két (" + incomingCounted.toPlainString() + " đ)"
                        + (withdrawAmount.compareTo(BigDecimal.ZERO) > 0
                                ? ", rút cất két " + withdrawAmount.toPlainString() + " đ, lưu két " + remainingCounted.toPlainString() + " đ."
                                : ", lưu két " + remainingCounted.toPlainString() + " đ.");
        resolveManagerCloseAlerts(savedShift.getId(), SecurityAlertStatus.APPROVED, principal, actorId, now, alertNote);

        auditLogService.log(AuditAction.SHIFT_CLOSE, "shift", savedShift.getId(), "MANAGER_CLOSE_PENDING",
                "Quản lý xác nhận nhận két & đóng ca ID=" + savedShift.getId()
                        + ", handoverId=" + handover.getId()
                        + ", incomingCounted=" + incomingCounted
                        + ", withdrawAmount=" + withdrawAmount
                        + ", remainingCounted=" + remainingCounted
                        + ", finalVariance=" + finalVariance);

        log.info("Hoàn tất xác nhận nhận két cuối ca & đóng ca: shiftId={}, approverId={}, incomingCounted={}, withdrawAmount={}, remainingCounted={}, finalVariance={}",
                savedShift.getId(), actorId, incomingCounted, withdrawAmount, remainingCounted, finalVariance);
        return enrichShiftResponse(savedShift);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShiftResponse rejectManagerClose(Integer shiftId, RejectManagerCloseRequest request, UserPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException(ErrorCode.AUTH_REQUIRED);
        }
        Shift shift = getOpenShiftForUpdateOrThrow(shiftId);
        if (!shift.isManagerClosePending()) {
            throw new BusinessRuleException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Ca #" + shift.getId() + " hiện không ở trạng thái chờ Quản lý xác nhận nhận két cuối ca.");
        }

        ShiftHandover handover = shiftHandoverRepository
                .findFirstByShiftIdAndStatusAndHandoverTypeOrderByIdDesc(
                        shift.getId(), ShiftHandoverStatus.PENDING_RECEIVE, ShiftHandoverType.MANAGER_CLOSE)
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.INVALID_STATE_TRANSITION,
                        "Không tìm thấy phiếu bàn giao két cuối ca đang chờ xác nhận."));

        Integer actorId = resolveActorId(principal);
        Instant now = Instant.now();
        String rejectReason = request != null && StringUtils.hasText(request.reason())
                ? request.reason().trim()
                : "Quản lý chi nhánh trả lại ca — mở lại quầy POS để kiểm tra thêm";

        handover.setStatus(ShiftHandoverStatus.CANCELLED);
        handover.setCancelledAt(now);
        handover.setCancelledBy(actorId);
        handover.setCancelReason(truncate(rejectReason, MAX_REASON_LEN));
        shiftHandoverRepository.save(handover);

        shift.setManagerClosePending(Boolean.FALSE);
        Shift savedShift = shiftRepository.save(shift);

        resolveManagerCloseAlerts(savedShift.getId(), SecurityAlertStatus.REJECTED, principal, actorId, now, rejectReason);

        auditLogService.log(AuditAction.SHIFT_OPEN, "shift", savedShift.getId(), "MANAGER_CLOSE_PENDING",
                "Quản lý trả lại ca chờ nhận két shiftId=" + savedShift.getId()
                        + ", handoverId=" + handover.getId() + ", lý do=" + rejectReason);

        log.info("Đã trả lại ca chờ nhận két & mở lại POS: shiftId={}, actorId={}", savedShift.getId(), actorId);
        return enrichShiftResponse(savedShift);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PreOpenCashAdjustmentResponse preOpenCashIn(PreOpenCashInRequest request, UserPrincipal principal) {
        ensureCanAdjustCash(principal);
        Integer branchId = BranchContext.resolveBranchScope(request.branchId());
        if (!branchRepository.existsById(branchId)) {
            throw new NotFoundException("chi nhánh");
        }
        if (shiftRepository.existsByBranchIdAndStatus(branchId, ShiftStatus.OPEN)) {
            throw new BusinessRuleException(BusinessErrorCode.SHIFT_ALREADY_OPEN,
                    "Chi nhánh hiện đang có ca mở. Vui lòng dùng chức năng Nạp tiền trong ca thay vì Nạp đầu ngày.");
        }

        User approver = resolveCashAdjustmentApprover(branchId, request.managerPin(), principal);
        Integer actorId = resolveActorId(principal);

        PreOpenCashAdjustment adj = PreOpenCashAdjustment.builder()
                .branchId(branchId)
                .amount(request.amount())
                .reason(truncate(request.reason().trim(), MAX_REASON_LEN))
                .createdBy(actorId != null ? actorId : approver.getId())
                .approvedBy(approver.getId())
                .build();
        PreOpenCashAdjustment saved = preOpenCashAdjustmentRepository.save(adj);

        auditLogService.log(AuditAction.CASH_IN, "pre_open_cash_adjustment", saved.getId(), null,
                "Nạp tiền đầu ngày trước mở ca branchId=" + branchId
                        + ", amount=" + saved.getAmount() + ", lý do=" + saved.getReason());

        log.info("Nạp tiền đầu ngày trước mở ca: id={}, branchId={}, amount={}, actorId={}",
                saved.getId(), branchId, saved.getAmount(), actorId);
        return toPreOpenCashAdjustmentResponses(List.of(saved)).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PreOpenCashAdjustmentResponse cancelPreOpenCashIn(Integer adjustmentId, String reason, UserPrincipal principal) {
        ensureCanAdjustCash(principal);
        PreOpenCashAdjustment adj = preOpenCashAdjustmentRepository.findByIdForUpdate(adjustmentId)
                .orElseThrow(() -> new NotFoundException("khoản nạp tiền đầu ngày"));
        BranchContext.resolveBranchScope(adj.getBranchId());

        if (adj.getAppliedShiftId() != null) {
            throw new BusinessRuleException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Khoản nạp tiền đầu ngày đã được ghi nhận vào ca #" + adj.getAppliedShiftId() + ", không thể hủy.");
        }
        if (adj.getCancelledAt() != null) {
            throw new BusinessRuleException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Khoản nạp tiền đầu ngày này đã được hủy trước đó.");
        }

        Integer actorId = resolveActorId(principal);
        String cancelReason = StringUtils.hasText(reason) ? reason.trim() : "Quản lý hủy khoản nạp đầu ngày";
        adj.setCancelledAt(Instant.now());
        adj.setCancelledBy(actorId);
        adj.setCancelReason(truncate(cancelReason, MAX_REASON_LEN));
        PreOpenCashAdjustment saved = preOpenCashAdjustmentRepository.save(adj);

        auditLogService.log(AuditAction.CASH_IN, "pre_open_cash_adjustment", saved.getId(), "PENDING",
                "Hủy khoản nạp đầu ngày ID=" + saved.getId() + ", branchId=" + saved.getBranchId() + ", lý do=" + cancelReason);

        log.info("Đã hủy khoản nạp tiền đầu ngày: id={}, branchId={}, actorId={}", saved.getId(), saved.getBranchId(), actorId);
        return toPreOpenCashAdjustmentResponses(List.of(saved)).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CashTransactionResponse postCloseCashOut(PostCloseCashOutRequest request, UserPrincipal principal) {
        ensureCanAdjustCash(principal);
        Integer branchId = BranchContext.resolveBranchScope(request.branchId());
        if (!branchRepository.existsById(branchId)) {
            throw new NotFoundException("chi nhánh");
        }
        if (shiftRepository.existsByBranchIdAndStatus(branchId, ShiftStatus.OPEN)) {
            throw new BusinessRuleException(BusinessErrorCode.SHIFT_ALREADY_OPEN,
                    "Chi nhánh hiện đang có ca mở. Vui lòng rút tiền trực tiếp trong ca đang mở.");
        }

        Shift latestClosed = shiftRepository
                .findFirstByBranchIdAndStatusOrderByClosedAtDescIdDesc(branchId, ShiftStatus.CLOSED)
                .flatMap(s -> shiftRepository.findByIdForUpdate(s.getId()))
                .orElseThrow(() -> new BusinessRuleException(BusinessErrorCode.SHIFT_CLOSED,
                        "Chi nhánh chưa có ca nào đã đóng để rút tiền sau kết ca."));

        if (shiftRepository.existsByBranchIdAndStatus(branchId, ShiftStatus.OPEN)) {
            throw new BusinessRuleException(BusinessErrorCode.SHIFT_ALREADY_OPEN,
                    "Chi nhánh hiện đang có ca mở. Vui lòng rút tiền trực tiếp trong ca đang mở.");
        }
        if (latestClosed.getStatus() != ShiftStatus.CLOSED) {
            throw new BusinessRuleException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Ca #" + latestClosed.getId() + " không còn ở trạng thái Đã đóng.");
        }

        User manager = resolveCashAdjustmentApprover(branchId, request.managerPin(), principal);
        Integer actorId = resolveActorId(principal);
        boolean isSelf = principal != null && principal.userId().equals(manager.getId());

        BigDecimal availableInDrawer = resolvePreviousClosingCash(latestClosed);
        if (!ShiftCashCalculator.isSufficientCash(availableInDrawer, request.amount())) {
            throw new BusinessRuleException(BusinessErrorCode.INSUFFICIENT_CASH,
                    "Số tiền lưu két sau kết ca #" + latestClosed.getId() + " ("
                            + (availableInDrawer != null ? availableInDrawer.toPlainString() : "0")
                            + " đ) không đủ để rút " + request.amount().toPlainString() + " đ.");
        }

        CashTransaction tx = CashTransaction.builder()
                .shiftId(latestClosed.getId())
                .type(CashTransactionType.CASH_OUT)
                .amount(request.amount())
                .reason(truncate("[Rút sau kết ca] " + request.reason().trim(), MAX_REASON_LEN))
                .createdBy(actorId != null ? actorId : manager.getId())
                .approvedBy(manager.getId())
                .isSelfInitiated(isSelf)
                .build();
        CashTransaction savedTx = cashTransactionRepository.save(tx);

        BigDecimal prevExpected = latestClosed.getExpectedCash() != null
                ? latestClosed.getExpectedCash()
                : (latestClosed.getOpeningCash() != null ? latestClosed.getOpeningCash() : BigDecimal.ZERO);
        BigDecimal newExpected = ShiftCashCalculator.deductCash(prevExpected, request.amount());
        BigDecimal newCounted = ShiftCashCalculator.deductCash(availableInDrawer, request.amount());
        BigDecimal preservedVariance = ShiftCashCalculator.calculateVariance(newCounted, newExpected);

        latestClosed.setExpectedCash(newExpected);
        latestClosed.setCountedCash(newCounted);
        latestClosed.setVariance(preservedVariance);
        shiftRepository.save(latestClosed);

        auditLogService.log(AuditAction.CASH_OUT, "cash_transaction", savedTx.getId(), null,
                "Rút tiền sau kết ca shiftId=" + latestClosed.getId()
                        + ", amount=" + request.amount()
                        + ", remainingClosingCash=" + newCounted
                        + ", lý do=" + request.reason().trim());

        log.info("Rút tiền sau kết ca thành công: shiftId={}, amount={}, newCounted={}, managerId={}",
                latestClosed.getId(), request.amount(), newCounted, manager.getId());
        return enrichCashTransactionResponse(savedTx);
    }

    @Override
    @Transactional(readOnly = true)
    public ShiftSummaryResponse getCurrentShiftSummary(Integer requestedBranchId) {
        Integer branchId = BranchContext.resolveBranchScope(requestedBranchId);
        Shift shift = shiftRepository.findFirstByBranchIdAndStatusOrderByOpenedAtDesc(branchId, ShiftStatus.OPEN)
                .orElseThrow(() -> new BusinessRuleException(BusinessErrorCode.SHIFT_NOT_OPEN,
                        "Chi nhánh chưa mở ca làm việc. Vui lòng mở ca trước khi giao dịch."));

        return buildShiftSummary(shift);
    }

    @Override
    @Transactional(readOnly = true)
    public ShiftSummaryResponse getShiftSummary(Integer shiftId, UserPrincipal principal) {
        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new NotFoundException("ca làm việc"));
        Integer branchId = BranchContext.resolveBranchScope(shift.getBranchId());
        if (isCashier(principal)) {
            boolean allowed = shift.isOpen();
            if (!allowed && shift.getStatus() == ShiftStatus.CLOSED) {
                Integer latestClosedId = shiftRepository
                        .findFirstByBranchIdAndStatusOrderByClosedAtDescIdDesc(branchId, ShiftStatus.CLOSED)
                        .map(Shift::getId)
                        .orElse(null);
                allowed = shift.getId().equals(latestClosedId);
            }
            if (!allowed) {
                throw new ForbiddenException(ErrorCode.ACCESS_DENIED,
                        "Thu ngân chỉ được xem chi tiết ca hiện tại hoặc ca đã đóng gần nhất.");
            }
        }
        return buildShiftSummary(shift);
    }

    @Override
    @Transactional(readOnly = true)
    public PreviousShiftSummaryResponse getPreviousClosedShift(Integer requestedBranchId) {
        if (requestedBranchId == null) {
            throw new InvalidFieldValueException("branchId", "Chi nhánh không được để trống.");
        }
        Integer branchId = BranchContext.resolveBranchScope(requestedBranchId);
        if (!branchRepository.existsById(branchId)) {
            throw new NotFoundException("chi nhánh");
        }

        List<PreOpenCashAdjustment> pendingPreOpen = preOpenCashAdjustmentRepository
                .findPendingByBranchIdOrderByCreatedAtAsc(branchId);
        BigDecimal preOpenTotal = pendingPreOpen.stream()
                .map(PreOpenCashAdjustment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<PreOpenCashAdjustmentResponse> preOpenDtos = toPreOpenCashAdjustmentResponses(pendingPreOpen);

        Shift prev = shiftRepository.findFirstByBranchIdAndStatusOrderByClosedAtDescIdDesc(branchId, ShiftStatus.CLOSED)
                .orElse(null);
        if (prev == null) {
            if (preOpenTotal.compareTo(BigDecimal.ZERO) > 0) {
                return new PreviousShiftSummaryResponse(
                        false, null, null, null, null, null, null, null, null, null,
                        preOpenTotal, preOpenTotal, preOpenDtos);
            }
            return PreviousShiftSummaryResponse.none();
        }

        String closedByName = prev.getClosedBy() != null
                ? loadUserNameMap(List.of(prev.getClosedBy())).get(prev.getClosedBy())
                : null;
        BigDecimal closingCash = resolvePreviousClosingCash(prev);
        BigDecimal expectedOpeningCash = (closingCash != null ? closingCash : BigDecimal.ZERO).add(preOpenTotal);
        return new PreviousShiftSummaryResponse(
                true,
                prev.getId(),
                prev.getShiftDate(),
                prev.getShiftType(),
                prev.getClosedAt(),
                prev.getClosedBy(),
                closedByName,
                prev.getExpectedCash(),
                closingCash,
                prev.getVariance(),
                preOpenTotal,
                expectedOpeningCash,
                preOpenDtos
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<BranchShiftOverviewResponse> getBranchesOverview() {
        List<Branch> branches = branchRepository.findAll(Sort.by(Sort.Order.asc("code"), Sort.Order.asc("id")));
        List<Shift> openShifts = shiftRepository.findByStatus(ShiftStatus.OPEN);
        List<Shift> closedShifts = shiftRepository.findByStatusOrderByClosedAtDescIdDesc(ShiftStatus.CLOSED);

        Map<Integer, Shift> openShiftByBranch = new HashMap<>();
        for (Shift s : openShifts) {
            openShiftByBranch.merge(s.getBranchId(), s, (existing, replacement) -> {
                Instant a = existing.getOpenedAt();
                Instant b = replacement.getOpenedAt();
                if (a == null) {
                    return replacement;
                }
                if (b == null) {
                    return existing;
                }
                return b.isAfter(a) ? replacement : existing;
            });
        }

        Map<Integer, Shift> lastClosedByBranch = new HashMap<>();
        for (Shift s : closedShifts) {
            lastClosedByBranch.putIfAbsent(s.getBranchId(), s);
        }

        Set<Integer> actorIds = new HashSet<>();
        for (Shift s : openShiftByBranch.values()) {
            if (s.getOpenedBy() != null) {
                actorIds.add(s.getOpenedBy());
            }
            if (s.getActiveCashierId() != null) {
                actorIds.add(s.getActiveCashierId());
            }
        }
        Map<Integer, String> userNames = loadUserNameMap(actorIds);

        return branches.stream().map(b -> {
            Shift open = openShiftByBranch.get(b.getId());
            Shift prevClosed = lastClosedByBranch.get(b.getId());
            Integer openedBy = open != null ? open.getOpenedBy() : null;
            Integer activeCashierId = open != null
                    ? (open.getActiveCashierId() != null ? open.getActiveCashierId() : open.getOpenedBy())
                    : null;
            return new BranchShiftOverviewResponse(
                    b.getId(),
                    b.getCode(),
                    b.getName(),
                    b.getStatus(),
                    open != null,
                    open != null ? open.getId() : null,
                    open != null ? open.getShiftType() : null,
                    open != null ? open.getOpenedAt() : null,
                    openedBy,
                    openedBy != null ? userNames.get(openedBy) : null,
                    open != null ? open.getOpeningCash() : null,
                    open != null ? open.getCashRevenue() : null,
                    open != null ? open.getExpectedCash() : null,
                    prevClosed != null ? prevClosed.getId() : null,
                    prevClosed != null ? resolvePreviousClosingCash(prevClosed) : null,
                    prevClosed != null ? prevClosed.getClosedAt() : null,
                    activeCashierId,
                    activeCashierId != null ? userNames.get(activeCashierId) : null,
                    open != null && open.isHandoverPending(),
                    open != null && open.isEarlyClosePending(),
                    open != null && open.isManagerClosePending(),
                    open != null ? resolvePreOpenCashIn(open) : null
            );
        }).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ShiftResponse> listShifts(ShiftListQuery query, UserPrincipal principal) {
        Integer branchId = BranchContext.resolveBranchScope(query.branchId());
        Sort.Direction dir = query.sortDir() == SortDirection.ASC ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(new Sort.Order(dir, "shiftDate"), new Sort.Order(dir, "plannedStart"),
                new Sort.Order(dir, "id"));
        Pageable pageable = PageQueryDefaults.toPageable(query.page(), query.size(), sort);

        if (isCashier(principal)) {
            Shift latestClosed = shiftRepository
                    .findFirstByBranchIdAndStatusOrderByClosedAtDescIdDesc(branchId, ShiftStatus.CLOSED)
                    .orElse(null);
            if (latestClosed == null || !matchesCashierPreviousFilter(latestClosed, query)) {
                return new PageImpl<>(List.of(), pageable, 0);
            }
            List<ShiftResponse> content = pageable.getPageNumber() == 0
                    ? enrichShiftResponses(List.of(latestClosed))
                    : List.of();
            return new PageImpl<>(content, pageable, 1);
        }

        Page<Shift> page = shiftRepository.findAll(
                ShiftSpecs.filter(branchId, query.status(), query.startDate(), query.endDate()),
                pageable);
        List<ShiftResponse> enriched = enrichShiftResponses(page.getContent());
        return new PageImpl<>(enriched, pageable, page.getTotalElements());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShiftScheduleResponse createSchedule(CreateScheduleShiftRequest request, UserPrincipal principal) {
        Integer branchId = BranchContext.resolveBranchScope(request.branchId());

        LocalDate todayVN = DateTimeUtil.todayVN();
        if (ShiftSchedulePolicy.isPastScheduleDate(request.shiftDate(), todayVN)) {
            throw new BusinessRuleException(BusinessErrorCode.INVALID_SHIFT_TIME,
                    "Không thể tạo lịch phân ca cho ngày đã qua (" + request.shiftDate() + ").");
        }
        validateScheduleTiming(request.shiftDate(), request.shiftType(), request.plannedStart(), request.plannedEnd());

        List<Integer> empIds = deduplicateIds(request.assignedEmployeeIds());
        Map<Integer, String> normalizedRoles = validateAndNormalizeShiftRoles(request.employeeRoles());
        Map<Integer, User> userMap = loadAndValidateBranchEmployees(branchId, empIds);
        validateScheduleConflicts(empIds, 0, request.plannedStart(), request.plannedEnd(), userMap);
        validateActiveSlotAvailable(branchId, request.shiftDate(), request.shiftType(), null);

        Shift shift = Shift.builder()
                .branchId(branchId)
                .shiftDate(request.shiftDate())
                .shiftType(request.shiftType())
                .plannedStart(request.plannedStart())
                .plannedEnd(request.plannedEnd())
                .status(ShiftStatus.SCHEDULED)
                .build();
        Shift savedShift = shiftRepository.save(shift);

        List<ShiftAssignment> savedAssignments = assignEmployeesToShift(savedShift, empIds, userMap, normalizedRoles, Set.of());
        List<Integer> assignedIds = savedAssignments.stream().map(a -> a.getId().getEmployeeId()).toList();
        Map<Integer, String> roleMap = buildRoleMap(savedAssignments, userMap);

        auditLogService.log(AuditAction.SCHEDULE_SAVE, "shift", savedShift.getId(), null,
                "Tạo lịch ca ID=" + savedShift.getId() + " ngày " + request.shiftDate() + ", nhân viên: " + assignedIds);

        return shiftMapper.toShiftScheduleResponse(savedShift, assignedIds, roleMap, List.of());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShiftScheduleResponse updateSchedule(Integer shiftId, UpdateScheduleShiftRequest request, UserPrincipal principal) {
        Shift shift = shiftRepository.findByIdForUpdate(shiftId)
                .orElseThrow(() -> new NotFoundException("ca làm việc"));
        Integer branchId = BranchContext.resolveBranchScope(shift.getBranchId());

        if (shift.getStatus() == ShiftStatus.CLOSED) {
            throw new BusinessRuleException(BusinessErrorCode.SHIFT_CLOSED,
                    "Ca làm việc đã đóng, không thể chỉnh sửa lịch hoặc phân công nhân sự.");
        }
        LocalDate todayVN = DateTimeUtil.todayVN();
        if (shift.getStatus() == ShiftStatus.SCHEDULED) {
            if (ShiftSchedulePolicy.isPastScheduleDate(shift.getShiftDate(), todayVN)) {
                throw new BusinessRuleException(BusinessErrorCode.INVALID_SHIFT_TIME,
                        "Lịch ca thuộc ngày đã qua (" + shift.getShiftDate() + "), không thể chỉnh sửa hoặc gán nhân sự.");
            }
            if (ShiftSchedulePolicy.isPastScheduleDate(request.shiftDate(), todayVN)) {
                throw new BusinessRuleException(BusinessErrorCode.INVALID_SHIFT_TIME,
                        "Không thể chuyển lịch phân ca sang ngày đã qua (" + request.shiftDate() + ").");
            }
            validateScheduleTiming(request.shiftDate(), request.shiftType(), request.plannedStart(), request.plannedEnd());
        } else {
            if (!ShiftSchedulePolicy.isValidShiftTime(request.plannedStart(), request.plannedEnd())) {
                throw new BusinessRuleException(BusinessErrorCode.INVALID_SHIFT_TIME,
                        "Thời gian bắt đầu ca phải trước thời gian kết thúc trong cùng ngày.");
            }
            boolean timesModified = !Objects.equals(shift.getPlannedStart(), request.plannedStart())
                    || !Objects.equals(shift.getPlannedEnd(), request.plannedEnd());
            if (timesModified) {
                validateScheduleTiming(shift.getShiftDate(), shift.getShiftType(), request.plannedStart(), request.plannedEnd());
            }
        }

        List<Integer> empIds = deduplicateIds(request.assignedEmployeeIds());
        Map<Integer, String> normalizedRoles = validateAndNormalizeShiftRoles(request.employeeRoles());
        Map<Integer, User> userMap = loadAndValidateBranchEmployees(branchId, empIds);
        validateScheduleConflicts(empIds, shiftId, request.plannedStart(), request.plannedEnd(), userMap);

        if (shift.getStatus() == ShiftStatus.SCHEDULED) {
            validateActiveSlotAvailable(branchId, request.shiftDate(), request.shiftType(), shiftId);
            shift.setShiftDate(request.shiftDate());
            shift.setShiftType(request.shiftType());
        }
        shift.setPlannedStart(request.plannedStart());
        shift.setPlannedEnd(request.plannedEnd());
        Shift savedShift = shiftRepository.save(shift);

        List<ShiftAssignment> previousAssignments = shiftAssignmentRepository.findByIdShiftId(savedShift.getId());
        Set<Integer> previouslyAssignedIds = previousAssignments.stream()
                .map(a -> a.getId().getEmployeeId())
                .collect(Collectors.toSet());
        Set<Integer> replacementEmpIds = new HashSet<>(previousAssignments.stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsReplacement()))
                .map(a -> a.getId().getEmployeeId())
                .toList());

        if (savedShift.getStatus() == ShiftStatus.OPEN) {
            for (Integer empId : empIds) {
                if (!previouslyAssignedIds.contains(empId)) {
                    replacementEmpIds.add(empId);
                }
            }
        }

        shiftAssignmentRepository.deleteByIdShiftId(savedShift.getId());
        shiftAssignmentRepository.flush();
        List<ShiftAssignment> savedAssignments = assignEmployeesToShift(savedShift, empIds, userMap, normalizedRoles, replacementEmpIds);
        List<Integer> assignedIds = savedAssignments.stream().map(a -> a.getId().getEmployeeId()).toList();
        List<Integer> replacementIds = savedAssignments.stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsReplacement()))
                .map(a -> a.getId().getEmployeeId())
                .toList();
        Map<Integer, String> roleMap = buildRoleMap(savedAssignments, userMap);

        auditLogService.log(AuditAction.SCHEDULE_SAVE, "shift", savedShift.getId(), null,
                "Cập nhật lịch ca ID=" + savedShift.getId() + " ngày " + savedShift.getShiftDate() + ", nhân viên: " + assignedIds);

        return shiftMapper.toShiftScheduleResponse(savedShift, assignedIds, roleMap, replacementIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSchedule(Integer shiftId, UserPrincipal principal) {
        Shift shift = shiftRepository.findByIdForUpdate(shiftId)
                .orElseThrow(() -> new NotFoundException("ca làm việc"));
        BranchContext.resolveBranchScope(shift.getBranchId());

        if (shift.getStatus() != ShiftStatus.SCHEDULED) {
            throw new BusinessRuleException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Chỉ có thể xóa ca ở trạng thái Đã lên lịch (chưa mở ca).");
        }

        shiftAssignmentRepository.deleteByIdShiftId(shift.getId());
        shiftAssignmentRepository.flush();
        shiftRepository.delete(shift);

        auditLogService.log(AuditAction.SCHEDULE_SAVE, "shift", shiftId, ShiftStatus.SCHEDULED.name(), "DELETED");
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShiftScheduleResponse> getSchedules(Integer requestedBranchId, LocalDate startDate, LocalDate endDate) {
        Integer branchId = BranchContext.resolveBranchScope(requestedBranchId);
        List<Shift> shifts = shiftRepository.findByBranchIdAndShiftDateBetweenOrderByShiftDateAscPlannedStartAsc(
                branchId, startDate, endDate);

        List<ShiftScheduleResponse> result = new ArrayList<>();
        for (Shift s : shifts) {
            List<ShiftAssignment> assignments = shiftAssignmentRepository.findByIdShiftId(s.getId());
            List<Integer> empIds = assignments.stream().map(a -> a.getId().getEmployeeId()).toList();
            List<Integer> replacementIds = assignments.stream()
                    .filter(a -> Boolean.TRUE.equals(a.getIsReplacement()))
                    .map(a -> a.getId().getEmployeeId())
                    .toList();
            Map<Integer, String> roleMap = buildRoleMap(assignments, Map.of());
            result.add(shiftMapper.toShiftScheduleResponse(s, empIds, roleMap, replacementIds));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShiftScheduleResponse> getOpenableScheduledShifts(Integer requestedBranchId, UserPrincipal principal) {
        Integer branchId = BranchContext.resolveBranchScope(requestedBranchId);
        LocalDate today = DateTimeUtil.todayVN();
        List<Shift> shifts = shiftRepository.findByBranchIdAndShiftDateBetweenOrderByShiftDateAscPlannedStartAsc(
                branchId, today.minusDays(1), today.plusDays(3));

        boolean cashierOnly = isCashier(principal);
        Integer actorId = resolveActorId(principal);
        List<ShiftScheduleResponse> result = new ArrayList<>();
        for (Shift s : shifts) {
            if (s.getStatus() != ShiftStatus.SCHEDULED) {
                continue;
            }
            List<ShiftAssignment> assignments = shiftAssignmentRepository.findByIdShiftId(s.getId());
            List<Integer> empIds;
            List<Integer> replacementIds;
            Map<Integer, String> roleMap;
            if (cashierOnly) {
                ShiftAssignment selfAssignment = actorId == null ? null : assignments.stream()
                        .filter(a -> actorId.equals(a.getId().getEmployeeId()))
                        .findFirst()
                        .orElse(null);
                empIds = selfAssignment != null ? List.of(actorId) : List.of();
                replacementIds = selfAssignment != null && Boolean.TRUE.equals(selfAssignment.getIsReplacement())
                        ? List.of(actorId)
                        : List.of();
                roleMap = selfAssignment != null && StringUtils.hasText(selfAssignment.getRoleInShift())
                        ? Map.of(actorId, selfAssignment.getRoleInShift())
                        : Map.of();
            } else {
                empIds = assignments.stream().map(a -> a.getId().getEmployeeId()).toList();
                replacementIds = assignments.stream()
                        .filter(a -> Boolean.TRUE.equals(a.getIsReplacement()))
                        .map(a -> a.getId().getEmployeeId())
                        .toList();
                roleMap = buildRoleMap(assignments, Map.of());
            }
            result.add(shiftMapper.toShiftScheduleResponse(s, empIds, roleMap, replacementIds));
        }
        return result;
    }

    private static boolean isCashier(UserPrincipal principal) {
        return principal != null && Roles.CASHIER.code().equals(principal.role());
    }

    private static boolean matchesCashierPreviousFilter(Shift shift, ShiftListQuery query) {
        if (query.status() != null && query.status() != ShiftStatus.CLOSED) {
            return false;
        }
        LocalDate date = shift.getShiftDate();
        if (query.startDate() != null && (date == null || date.isBefore(query.startDate()))) {
            return false;
        }
        if (query.endDate() != null && (date == null || date.isAfter(query.endDate()))) {
            return false;
        }
        return true;
    }

    private ShiftSummaryResponse buildShiftSummary(Shift shift) {
        List<CashTransaction> txList = cashTransactionRepository.findByShiftIdOrderByCreatedAtDesc(shift.getId());
        List<ShiftHandover> handovers = shiftHandoverRepository.findByShiftIdOrderByCreatedAtDescIdDesc(shift.getId());

        List<ShiftCashCalculator.CashEntry> entries = txList.stream()
                .map(tx -> new ShiftCashCalculator.CashEntry(tx.getType(), tx.getAmount()))
                .toList();
        ShiftCashCalculator.CashAggregation agg = ShiftCashCalculator.aggregate(entries);

        return new ShiftSummaryResponse(
                enrichShiftResponse(shift),
                enrichCashTransactionResponses(txList),
                shift.getOpeningCash(),
                shift.getCashRevenue(),
                shift.getTransferRevenue(),
                agg.totalIn(),
                agg.totalOut(),
                agg.totalRefund(),
                shift.getExpectedCash(),
                enrichHandoverResponses(handovers)
        );
    }

    private void validateNoOpenShiftInBranch(Integer branchId) {
        if (shiftRepository.existsByBranchIdAndStatus(branchId, ShiftStatus.OPEN)) {
            throw new BusinessRuleException(BusinessErrorCode.SHIFT_ALREADY_OPEN,
                    "Chi nhánh hiện đang có một ca làm việc đang mở. Vui lòng đóng ca trước khi mở ca mới.");
        }
    }

    private Shift resolveShift(OpenShiftRequest request, Integer branchId, Instant now) {
        if (request.scheduledShiftId() != null) {
            Shift scheduled = shiftRepository.findByIdForUpdate(request.scheduledShiftId())
                    .orElseThrow(() -> new NotFoundException("ca làm việc"));
            if (!branchId.equals(scheduled.getBranchId())) {
                throw new ForbiddenException(ErrorCode.CROSS_BRANCH_DENIED);
            }
            if (scheduled.getStatus() == ShiftStatus.CLOSED) {
                throw new BusinessRuleException(BusinessErrorCode.SHIFT_CLOSED,
                        "Ca làm việc này đã đóng, không thể mở lại.");
            }
            if (scheduled.getStatus() == ShiftStatus.OPEN) {
                throw new BusinessRuleException(BusinessErrorCode.SHIFT_ALREADY_OPEN);
            }
            LocalDate todayVN = DateTimeUtil.todayVN();
            if (ShiftSchedulePolicy.isTooEarlyToOpen(scheduled.getShiftDate(), scheduled.getPlannedStart(), now, todayVN)) {
                String plannedTimeLabel = scheduled.getPlannedStart() != null
                        ? scheduled.getShiftDate() + " lúc " + DateTimeUtil.formatHmVN(scheduled.getPlannedStart())
                        : String.valueOf(scheduled.getShiftDate());
                throw new BusinessRuleException(BusinessErrorCode.INVALID_SHIFT_TIME,
                        "Chưa đến ca làm việc #" + scheduled.getId() + " (" + plannedTimeLabel
                                + "). Chỉ được kiểm két mở ca trước giờ bắt đầu tối đa 60 phút.");
            }
            if (ShiftSchedulePolicy.isExpiredPastSchedule(scheduled.getShiftDate(), scheduled.getPlannedEnd(), now, todayVN)) {
                throw new BusinessRuleException(BusinessErrorCode.INVALID_SHIFT_TIME,
                        "Lịch ca #" + scheduled.getId() + " thuộc ngày đã qua (" + scheduled.getShiftDate()
                                + ") đã quá giờ hoạt động, không thể mở ca.");
            }
            scheduled.setStatus(ShiftStatus.OPEN);
            return scheduled;
        }

        LocalDate shiftDate = LocalDate.now(VN_ZONE);
        LocalTime localNow = LocalTime.now(VN_ZONE);
        ShiftType inferredType = ShiftSchedulePolicy.inferShiftType(localNow);
        shiftRepository.findFirstByBranchIdAndShiftDateAndShiftTypeAndStatusOrderByIdAsc(
                        branchId, shiftDate, inferredType, ShiftStatus.SCHEDULED)
                .ifPresent(scheduled -> {
                    throw new BusinessRuleException(BusinessErrorCode.SCHEDULE_CONFLICT,
                            "Chi nhánh đã có " + shiftTypeLabel(inferredType)
                                    + " Đã lên lịch hôm nay (#" + scheduled.getId()
                                    + "). Vui lòng mở ca đã lên lịch đó thay vì tạo ca phát sinh trùng ca.");
                });
        Instant plannedEnd = ShiftSchedulePolicy.resolveAdHocPlannedEnd(inferredType, shiftDate, now, VN_ZONE);
        return Shift.builder()
                .branchId(branchId)
                .shiftDate(shiftDate)
                .shiftType(inferredType)
                .plannedStart(now)
                .plannedEnd(plannedEnd)
                .status(ShiftStatus.OPEN)
                .build();
    }

    private static BigDecimal resolvePreviousClosingCash(Shift previousClosed) {
        return previousClosed.getCountedCash() != null
                ? previousClosed.getCountedCash()
                : previousClosed.getExpectedCash();
    }

    private void populateOpeningFields(Shift shift,
                                       BigDecimal baseOpeningCash,
                                       BigDecimal initialExpectedCash,
                                       BigDecimal preOpenCashIn,
                                       Integer actorId,
                                       Instant now,
                                       Integer previousShiftId,
                                       BigDecimal previousClosingCash,
                                       BigDecimal openingVariance,
                                       String openingNote) {
        shift.setOpenedBy(actorId);
        shift.setOpenedAt(now);
        shift.setActiveCashierId(actorId);
        shift.setHandoverPending(Boolean.FALSE);
        shift.setEarlyClosePending(Boolean.FALSE);
        shift.setManagerClosePending(Boolean.FALSE);
        shift.setOpeningCash(baseOpeningCash);
        shift.setPreOpenCashIn(preOpenCashIn != null ? preOpenCashIn : BigDecimal.ZERO);
        shift.setExpectedCash(initialExpectedCash);
        shift.setCashRevenue(BigDecimal.ZERO);
        shift.setTransferRevenue(BigDecimal.ZERO);
        shift.setPreviousShiftId(previousShiftId);
        shift.setPreviousClosingCash(previousClosingCash);
        shift.setOpeningVariance(openingVariance);
        shift.setOpeningNote(openingNote);
    }

    private void recordOpeningCashTransaction(Integer shiftId,
                                              BigDecimal baseOpeningCash,
                                              BigDecimal preOpenTotal,
                                              String note,
                                              BigDecimal previousClosingCash,
                                              BigDecimal openingVariance,
                                              Integer actorId) {
        String reason;
        boolean hasPreOpen = preOpenTotal != null && preOpenTotal.compareTo(BigDecimal.ZERO) > 0;
        if (openingVariance != null && openingVariance.compareTo(BigDecimal.ZERO) != 0) {
            String baselineDesc = hasPreOpen
                    ? ((previousClosingCash != null ? "kết ca trước " + previousClosingCash.toPlainString() + " đ + " : "")
                            + "nạp đầu ngày " + preOpenTotal.toPlainString() + " đ")
                    : ("kết ca trước (" + (previousClosingCash != null ? previousClosingCash.toPlainString() : "0") + " đ)");
            reason = "Đối soát đầu ca lệch " + openingVariance.toPlainString()
                    + " đ so với " + baselineDesc + ": " + note;
        } else if (hasPreOpen) {
            reason = StringUtils.hasText(note)
                    ? note
                    : (previousClosingCash != null
                            ? "Mở ca & khớp két đầu ngày (Kết ca trước " + previousClosingCash.toPlainString()
                                    + " đ + Nạp đầu ngày " + preOpenTotal.toPlainString() + " đ)"
                            : "Mở ca & khớp tiền Quản lý nạp đầu ngày (" + preOpenTotal.toPlainString() + " đ)");
        } else if (previousClosingCash != null) {
            reason = StringUtils.hasText(note)
                    ? note
                    : "Mở ca & khớp kết ca trước (" + previousClosingCash.toPlainString() + " đ)";
        } else {
            reason = StringUtils.hasText(note) ? note : "Mở ca & khai báo số dư đầu két";
        }

        CashTransaction openTx = CashTransaction.builder()
                .shiftId(shiftId)
                .type(CashTransactionType.OPEN)
                .amount(baseOpeningCash)
                .reason(truncate(reason, MAX_REASON_LEN))
                .createdBy(actorId)
                .isSelfInitiated(false)
                .build();
        cashTransactionRepository.save(openTx);
    }

    private Shift getOpenShiftForUpdateOrThrow(Integer shiftId) {
        Shift shift = shiftRepository.findByIdForUpdate(shiftId)
                .orElseThrow(() -> new NotFoundException("ca làm việc"));

        BranchContext.resolveBranchScope(shift.getBranchId());

        if (!shift.isOpen()) {
            throw new BusinessRuleException(BusinessErrorCode.SHIFT_CLOSED, "Ca làm việc này đã đóng, không thể thao tác.");
        }
        return shift;
    }

    private void ensureCanAdjustCash(UserPrincipal principal) {
        if (principal == null) {
            return;
        }
        String role = principal.role();
        if (!Roles.COO.code().equals(role) && !Roles.BRANCH_MANAGER.code().equals(role)) {
            throw new ForbiddenException(ErrorCode.ACCESS_DENIED,
                    "Thu ngân không có quyền nạp/rút tiền mặt két; thao tác này chỉ dành cho Quản lý chi nhánh hoặc COO.");
        }
    }

    private User resolveCashAdjustmentApprover(Integer branchId, String pin, UserPrincipal principal) {
        if (StringUtils.hasText(pin)) {
            return verifyManagerPin(branchId, pin);
        }
        if (principal != null
                && (Roles.COO.code().equals(principal.role()) || Roles.BRANCH_MANAGER.code().equals(principal.role()))) {
            return userRepository.findWithRoleAndBranchById(principal.userId())
                    .filter(User::isActive)
                    .orElseThrow(() -> new BusinessRuleException(BusinessErrorCode.MANAGER_PIN_REQUIRED,
                            "Tài khoản Quản lý / COO không còn hoạt động."));
        }
        throw new BusinessRuleException(BusinessErrorCode.MANAGER_PIN_REQUIRED,
                "Yêu cầu mã PIN Quản lý chi nhánh.");
    }

    private User verifyManagerPin(Integer branchId, String pin) {
        if (!StringUtils.hasText(pin)) {
            throw new BusinessRuleException(BusinessErrorCode.MANAGER_PIN_REQUIRED, "Yêu cầu mã PIN Quản lý chi nhánh.");
        }
        String lookupHash = pinHasher.lookupHash(branchId, pin.trim());
        User manager = userRepository.findFirstByBranchIdAndPinLookupHashAndStatus(branchId, lookupHash, UserStatus.ACTIVE)
                .orElseThrow(() -> new BusinessRuleException(BusinessErrorCode.INVALID_MANAGER_PIN,
                        "Mã PIN Quản lý không chính xác hoặc tài khoản không tồn tại."));

        if (!pinHasher.matches(pin.trim(), manager.getPinHash())) {
            throw new BusinessRuleException(BusinessErrorCode.INVALID_MANAGER_PIN, "Mã PIN Quản lý không chính xác.");
        }

        String roleCode = manager.roleCode();
        if (!Roles.BRANCH_MANAGER.code().equals(roleCode) && !Roles.COO.code().equals(roleCode)) {
            throw new BusinessRuleException(BusinessErrorCode.MANAGER_PIN_REQUIRED,
                    "Tài khoản xác thực PIN không có vai trò Quản lý chi nhánh (Branch Manager) hoặc COO.");
        }
        return manager;
    }

    private ShiftResponse enrichShiftResponse(Shift shift) {
        return enrichShiftResponses(List.of(shift)).get(0);
    }

    private List<ShiftResponse> enrichShiftResponses(List<Shift> shifts) {
        if (shifts.isEmpty()) {
            return List.of();
        }
        Set<Integer> branchIds = new HashSet<>();
        Set<Integer> userIds = new HashSet<>();
        List<Integer> pendingShiftIds = new ArrayList<>();
        for (Shift s : shifts) {
            if (s.getBranchId() != null) {
                branchIds.add(s.getBranchId());
            }
            if (s.getOpenedBy() != null) {
                userIds.add(s.getOpenedBy());
            }
            if (s.getClosedBy() != null) {
                userIds.add(s.getClosedBy());
            }
            if (s.getActiveCashierId() != null) {
                userIds.add(s.getActiveCashierId());
            }
            if (s.getEarlyCloseRequestedBy() != null) {
                userIds.add(s.getEarlyCloseRequestedBy());
            }
            if ((s.isHandoverPending() || s.isManagerClosePending()) && s.getId() != null) {
                pendingShiftIds.add(s.getId());
            }
        }

        Map<Integer, ShiftHandover> pendingByShiftId = new HashMap<>();
        if (!pendingShiftIds.isEmpty()) {
            List<ShiftHandover> pendingList = shiftHandoverRepository.findByShiftIdInAndStatus(
                    pendingShiftIds, ShiftHandoverStatus.PENDING_RECEIVE);
            for (ShiftHandover h : pendingList) {
                pendingByShiftId.putIfAbsent(h.getShiftId(), h);
                collectHandoverUserIds(h, userIds);
            }
        }

        Map<Integer, String> branchNames = loadBranchNameMap(branchIds);
        Map<Integer, String> userNames = loadUserNameMap(userIds);

        return shifts.stream().map(s -> {
            ShiftResponse base = shiftMapper.toShiftResponse(s);
            Integer activeCashierId = s.getActiveCashierId() != null ? s.getActiveCashierId() : s.getOpenedBy();
            ShiftHandover pendingRaw = pendingByShiftId.get(s.getId());
            ShiftHandoverResponse pendingDto = pendingRaw != null ? toEnrichedHandover(pendingRaw, userNames) : null;
            return new ShiftResponse(
                    base.id(),
                    base.branchId(),
                    base.shiftDate(),
                    base.shiftType(),
                    base.plannedStart(),
                    base.plannedEnd(),
                    base.openedBy(),
                    base.openedAt(),
                    base.closedBy(),
                    base.closedAt(),
                    base.openingCash(),
                    base.cashRevenue(),
                    base.transferRevenue(),
                    base.expectedCash(),
                    base.countedCash(),
                    base.variance(),
                    base.discrepancyReason(),
                    base.status(),
                    base.createdAt(),
                    s.getBranchId() != null ? branchNames.get(s.getBranchId()) : null,
                    s.getOpenedBy() != null ? userNames.get(s.getOpenedBy()) : null,
                    s.getClosedBy() != null ? userNames.get(s.getClosedBy()) : null,
                    base.previousShiftId(),
                    base.previousClosingCash(),
                    base.openingVariance(),
                    base.openingNote(),
                    activeCashierId,
                    activeCashierId != null ? userNames.get(activeCashierId) : null,
                    s.isHandoverPending(),
                    pendingDto,
                    s.isEarlyClosePending(),
                    s.getEarlyCloseRequestedBy(),
                    s.getEarlyCloseRequestedBy() != null ? userNames.get(s.getEarlyCloseRequestedBy()) : null,
                    s.getEarlyCloseRequestedAt(),
                    s.getEarlyCloseCountedCash(),
                    s.getEarlyCloseVariance(),
                    s.getEarlyCloseDiscrepancyReason(),
                    s.getEarlyCloseReason(),
                    s.isManagerClosePending(),
                    resolvePreOpenCashIn(s)
            );
        }).toList();
    }

    private List<ShiftHandoverResponse> enrichHandoverResponses(List<ShiftHandover> list) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        Set<Integer> userIds = new HashSet<>();
        for (ShiftHandover h : list) {
            collectHandoverUserIds(h, userIds);
        }
        Map<Integer, String> userNames = loadUserNameMap(userIds);
        return list.stream().map(h -> toEnrichedHandover(h, userNames)).toList();
    }

    private static void collectHandoverUserIds(ShiftHandover h, Set<Integer> userIds) {
        if (h.getFromUserId() != null) {
            userIds.add(h.getFromUserId());
        }
        if (h.getToUserId() != null) {
            userIds.add(h.getToUserId());
        }
        if (h.getOutgoingConfirmedBy() != null) {
            userIds.add(h.getOutgoingConfirmedBy());
        }
        if (h.getIncomingConfirmedBy() != null) {
            userIds.add(h.getIncomingConfirmedBy());
        }
        if (h.getCancelledBy() != null) {
            userIds.add(h.getCancelledBy());
        }
    }

    private ShiftHandoverResponse toEnrichedHandover(ShiftHandover h, Map<Integer, String> userNames) {
        ShiftHandoverResponse base = shiftMapper.toShiftHandoverResponse(h);
        return new ShiftHandoverResponse(
                base.id(),
                base.shiftId(),
                base.branchId(),
                base.fromUserId(),
                h.getFromUserId() != null ? userNames.get(h.getFromUserId()) : null,
                base.toUserId(),
                h.getToUserId() != null ? userNames.get(h.getToUserId()) : null,
                base.handoverType(),
                base.status(),
                base.reason(),
                base.expectedCashAtOut(),
                base.outgoingCountedCash(),
                base.outgoingVariance(),
                base.outgoingNote(),
                base.outgoingAt(),
                base.outgoingConfirmedBy(),
                h.getOutgoingConfirmedBy() != null ? userNames.get(h.getOutgoingConfirmedBy()) : null,
                base.incomingCountedCash(),
                base.incomingVarianceFromOut(),
                base.incomingVarianceFromBook(),
                base.incomingNote(),
                base.incomingAt(),
                base.incomingConfirmedBy(),
                h.getIncomingConfirmedBy() != null ? userNames.get(h.getIncomingConfirmedBy()) : null,
                base.withdrawAmount(),
                base.withdrawReason(),
                base.cancelledAt(),
                base.cancelledBy(),
                h.getCancelledBy() != null ? userNames.get(h.getCancelledBy()) : null,
                base.cancelReason(),
                base.createdAt()
        );
    }

    private CashTransactionResponse enrichCashTransactionResponse(CashTransaction tx) {
        return enrichCashTransactionResponses(List.of(tx)).get(0);
    }

    private List<CashTransactionResponse> enrichCashTransactionResponses(List<CashTransaction> txList) {
        if (txList.isEmpty()) {
            return List.of();
        }
        Set<Integer> userIds = new HashSet<>();
        for (CashTransaction tx : txList) {
            if (tx.getCreatedBy() != null) {
                userIds.add(tx.getCreatedBy());
            }
            if (tx.getApprovedBy() != null) {
                userIds.add(tx.getApprovedBy());
            }
        }
        Map<Integer, String> userNames = loadUserNameMap(userIds);

        return txList.stream().map(tx -> {
            CashTransactionResponse base = shiftMapper.toCashTransactionResponse(tx);
            return new CashTransactionResponse(
                    base.id(),
                    base.shiftId(),
                    base.type(),
                    base.amount(),
                    base.reason(),
                    base.createdBy(),
                    base.approvedBy(),
                    base.isSelfInitiated(),
                    base.createdAt(),
                    tx.getCreatedBy() != null ? userNames.get(tx.getCreatedBy()) : null,
                    tx.getApprovedBy() != null ? userNames.get(tx.getApprovedBy()) : null
            );
        }).toList();
    }

    private Map<Integer, String> loadBranchNameMap(Collection<Integer> ids) {
        List<Integer> cleanIds = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (cleanIds.isEmpty()) {
            return Map.of();
        }
        return branchRepository.findAllById(cleanIds).stream()
                .collect(Collectors.toMap(Branch::getId, Branch::getName, (a, b) -> a));
    }

    private Map<Integer, String> loadUserNameMap(Collection<Integer> ids) {
        List<Integer> cleanIds = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (cleanIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllWithRoleByIdIn(cleanIds).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName, (a, b) -> a));
    }

    private List<Integer> deduplicateIds(List<Integer> rawIds) {
        if (rawIds == null || rawIds.isEmpty()) {
            return List.of();
        }
        return rawIds.stream().filter(Objects::nonNull).distinct().toList();
    }

    private Map<Integer, String> validateAndNormalizeShiftRoles(Map<Integer, String> rawRoles) {
        if (rawRoles == null || rawRoles.isEmpty()) {
            return Map.of();
        }
        Map<Integer, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> entry : rawRoles.entrySet()) {
            Integer empId = entry.getKey();
            String val = entry.getValue();
            if (empId == null || !StringUtils.hasText(val)) {
                continue;
            }
            String upper = val.trim().toUpperCase(Locale.ROOT);
            if (!SHIFT_STAFF_ROLES.contains(upper)) {
                throw new InvalidFieldValueException("employeeRoles",
                        "Vai trò kiêm nhiệm theo ca không hợp lệ: " + val
                                + " (chỉ chấp nhận CASHIER, BARISTA, WAITER, BRANCH_MANAGER).");
            }
            normalized.put(empId, upper);
        }
        return normalized;
    }

    private static Map<Integer, String> buildRoleMap(List<ShiftAssignment> assignments, Map<Integer, User> userMap) {
        if (assignments == null || assignments.isEmpty()) {
            return Map.of();
        }
        Map<Integer, String> roles = new LinkedHashMap<>();
        for (ShiftAssignment a : assignments) {
            Integer empId = a.getId().getEmployeeId();
            String role = a.getRoleInShift();
            if (!StringUtils.hasText(role) && userMap.containsKey(empId)) {
                role = userMap.get(empId).roleCode();
            }
            if (StringUtils.hasText(role)) {
                roles.put(empId, role);
            }
        }
        return roles;
    }

    private Map<Integer, User> loadAndValidateBranchEmployees(Integer branchId, List<Integer> empIds) {
        if (empIds.isEmpty()) {
            return Map.of();
        }
        Map<Integer, User> userMap = userRepository.findAllWithRoleByIdIn(empIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        for (Integer empId : empIds) {
            User emp = userMap.get(empId);
            if (emp == null) {
                throw new NotFoundException("nhân viên ID=" + empId);
            }
            if (!branchId.equals(emp.getBranchId())) {
                throw new InvalidFieldValueException("assignedEmployeeIds",
                        "Nhân viên " + emp.getFullName() + " không thuộc chi nhánh này.");
            }
            if (emp.getStatus() != UserStatus.ACTIVE) {
                throw new InvalidFieldValueException("assignedEmployeeIds",
                        "Nhân viên " + emp.getFullName() + " đang ngừng hoạt động, không thể phân ca.");
            }
        }
        return userMap;
    }

    private void validateScheduleConflicts(List<Integer> empIds,
                                           Integer excludeShiftId,
                                           Instant plannedStart,
                                           Instant plannedEnd,
                                           Map<Integer, User> userMap) {
        if (empIds.isEmpty()) {
            return;
        }
        List<ShiftAssignment> conflicts = shiftAssignmentRepository.findConflictingAssignmentsForEmployees(
                empIds, excludeShiftId, plannedStart, plannedEnd);
        if (!conflicts.isEmpty()) {
            Integer conflictEmpId = conflicts.get(0).getId().getEmployeeId();
            User conflictEmp = userMap.get(conflictEmpId);
            String label = conflictEmp != null
                    ? conflictEmp.getFullName() + " (ID=" + conflictEmpId + ")"
                    : "ID=" + conflictEmpId;
            throw new BusinessRuleException(BusinessErrorCode.SCHEDULE_CONFLICT,
                    "Nhân viên " + label + " đã có ca làm việc khác trùng khung giờ này.");
        }
    }

    private void validateActiveSlotAvailable(Integer branchId,
                                             LocalDate shiftDate,
                                             ShiftType shiftType,
                                             Integer excludeShiftId) {
        boolean exists = excludeShiftId == null
                ? shiftRepository.existsByBranchIdAndShiftDateAndShiftTypeAndStatusNot(
                        branchId, shiftDate, shiftType, ShiftStatus.CLOSED)
                : shiftRepository.existsByBranchIdAndShiftDateAndShiftTypeAndStatusNotAndIdNot(
                        branchId, shiftDate, shiftType, ShiftStatus.CLOSED, excludeShiftId);
        if (exists) {
            throw new BusinessRuleException(BusinessErrorCode.SCHEDULE_CONFLICT,
                    "Chi nhánh đã có " + shiftTypeLabel(shiftType)
                            + " (Đã lên lịch hoặc Đang mở) trong ngày " + shiftDate
                            + ". Vui lòng chỉnh sửa ca hiện có thay vì tạo ca trùng.");
        }
    }

    private static void validateScheduleTiming(LocalDate shiftDate,
                                               ShiftType shiftType,
                                               Instant plannedStart,
                                               Instant plannedEnd) {
        if (!ShiftSchedulePolicy.isValidShiftTime(plannedStart, plannedEnd)) {
            throw new BusinessRuleException(BusinessErrorCode.INVALID_SHIFT_TIME,
                    "Thời gian bắt đầu ca phải trước thời gian kết thúc trong cùng ngày.");
        }
        if (!ShiftSchedulePolicy.isWithinShiftTypeWindow(shiftDate, shiftType, plannedStart, plannedEnd, VN_ZONE)) {
            throw new BusinessRuleException(BusinessErrorCode.INVALID_SHIFT_TIME,
                    shiftTypeLabel(shiftType) + " chỉ cho phép xếp lịch trong khoảng "
                            + ShiftSchedulePolicy.windowLabel(shiftType) + " trong cùng ngày (" + shiftDate + ").");
        }
    }

    private static String shiftTypeLabel(ShiftType type) {
        if (type == null) {
            return "ca";
        }
        return switch (type) {
            case MORNING -> "Ca sáng";
            case EVENING -> "Ca chiều";
            case NIGHT -> "Ca tối";
        };
    }

    private List<ShiftAssignment> assignEmployeesToShift(
            Shift shift,
            List<Integer> empIds,
            Map<Integer, User> userMap,
            Map<Integer, String> normalizedRoles,
            Set<Integer> replacementEmpIds) {
        if (empIds.isEmpty()) {
            return List.of();
        }
        List<ShiftAssignment> assignments = new ArrayList<>();

        for (Integer empId : empIds) {
            User emp = userMap.get(empId);
            String roleInShift = normalizedRoles.getOrDefault(empId, emp.roleCode());
            boolean isReplacement = replacementEmpIds != null && replacementEmpIds.contains(empId);
            ShiftAssignment sa = ShiftAssignment.builder()
                    .id(new ShiftAssignmentId(shift.getId(), empId))
                    .shift(shift)
                    .employee(emp)
                    .roleInShift(roleInShift)
                    .isReplacement(isReplacement)
                    .build();
            assignments.add(sa);
        }
        return shiftAssignmentRepository.saveAll(assignments);
    }

    private Integer resolveActorId(UserPrincipal principal) {
        return principal != null ? principal.userId() : null;
    }

    private boolean autoAssignAndCheckInCashier(
            Shift savedShift,
            Integer cashierId,
            Instant now,
            String attendanceNote,
            boolean isHandoverReplacement) {
        User cashier = userRepository.findWithRoleAndBranchById(cashierId).orElse(null);
        if (cashier == null || !cashier.isActive()) {
            return false;
        }
        if (cashier.getBranchId() != null && !cashier.getBranchId().equals(savedShift.getBranchId())) {
            return false;
        }

        ShiftAssignmentId assignmentId = new ShiftAssignmentId(savedShift.getId(), cashierId);
        ShiftAssignment existingSa = shiftAssignmentRepository.findById(assignmentId).orElse(null);
        if (existingSa == null) {
            List<ShiftAssignment> conflicts = shiftAssignmentRepository.findConflictingAssignments(
                    cashierId, savedShift.getId(), savedShift.getPlannedStart(), savedShift.getPlannedEnd());
            if (conflicts.isEmpty()) {
                ShiftAssignment sa = ShiftAssignment.builder()
                        .id(assignmentId)
                        .shift(savedShift)
                        .employee(cashier)
                        .roleInShift(Roles.CASHIER.code())
                        .isReplacement(isHandoverReplacement)
                        .build();
                shiftAssignmentRepository.save(sa);
            } else {
                log.warn("Bỏ qua tự gán Thu ngân {} vào ca #{} do trùng khung giờ ca khác.",
                        cashierId, savedShift.getId());
            }
        } else if (isHandoverReplacement && !Roles.CASHIER.code().equalsIgnoreCase(existingSa.getRoleInShift())) {
            // Nhân viên vốn đã có lịch trong ca nhưng nhận bàn giao đứng quầy tạm thời:
            // nâng role_in_shift thành CASHIER nhưng KHÔNG bật isReplacement để vẫn giữ nguyên theo dõi giờ công ca gốc.
            existingSa.setRoleInShift(Roles.CASHIER.code());
            shiftAssignmentRepository.save(existingSa);
        }

        Optional<Attendance> existingOpenOpt = attendanceRepository
                .findFirstByUserIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(cashierId);
        if (existingOpenOpt.isPresent()) {
            Attendance existingOpen = existingOpenOpt.get();
            if (savedShift.getId().equals(existingOpen.getShiftId())) {
                return false;
            }
            long worked = AttendanceCalculator.calculateWorkedMinutes(existingOpen.getCheckInTime(), now);
            existingOpen.setCheckOutTime(now);
            existingOpen.setWorkedMinutes(worked);
            existingOpen.setStatus(AttendanceStatus.COMPLETED);
            String note = "Tự động check-out ca trước khi mở ca mới #" + savedShift.getId();
            existingOpen.setNotes(StringUtils.hasText(existingOpen.getNotes()) ? existingOpen.getNotes() + "; " + note : note);
            attendanceRepository.save(existingOpen);
        }

        Attendance attendance = Attendance.builder()
                .userId(cashierId)
                .branchId(savedShift.getBranchId())
                .shiftId(savedShift.getId())
                .workDate(DateTimeUtil.todayVN())
                .method("PIN")
                .checkInTime(now)
                .status(AttendanceStatus.CHECKED_IN)
                .notes(truncate(attendanceNote, 255))
                .build();
        attendanceRepository.save(attendance);
        return true;
    }

    private User verifyActorPinForShiftClose(UserPrincipal principal, Integer branchId, String rawPin) {
        if (principal == null) {
            throw new UnauthorizedException(ErrorCode.AUTH_REQUIRED);
        }
        return verifyActorPinForUser(principal.userId(), branchId, rawPin, "SHIFT_CLOSE");
    }

    private User verifyActorPinForUser(Integer userId, Integer branchId, String rawPin, String auditActionName) {
        if (userId == null) {
            throw new UnauthorizedException(ErrorCode.AUTH_REQUIRED);
        }
        User actor = userRepository.findWithRoleAndBranchById(userId)
                .orElseThrow(() -> new UnauthorizedException(ErrorCode.AUTH_REQUIRED));
        if (!actor.isActive()) {
            throw new UnauthorizedException(ErrorCode.ACCOUNT_INACTIVE);
        }
        if (!StringUtils.hasText(actor.getPinHash())) {
            throw new InvalidPinException(
                    "Tài khoản " + actor.getFullName() + " chưa được thiết lập mã PIN. Vui lòng liên hệ Quản lý chi nhánh để cấp PIN.");
        }

        String cleanPin = rawPin.trim();
        pinLockoutService.checkNotLocked(branchId, actor.getId(), actor.getFullName());
        if (!pinHasher.matches(cleanPin, actor.getPinHash())) {
            PinLockoutService.FailureOutcome outcome = pinLockoutService.recordFailure(
                    branchId, actor.getId(), actor.getFullName(), actor.roleCode());
            auditLogService.log(AuditAction.LOGIN_FAILED, "user", actor.getId(), null,
                    Map.of("method", "PIN", "action", auditActionName, "branchId", branchId));
            throw outcome.locked()
                    ? new PinLockedException(outcome.retryAfterSeconds(), outcome.message())
                    : new InvalidPinException(outcome.message());
        }

        pinLockoutService.onSuccess(branchId, actor.getId());
        return actor;
    }

    private Long autoCheckOutForUserId(Integer userId, Integer shiftId, Instant now, String notePrefix) {
        Optional<Attendance> targetAtt = shiftId != null
                ? attendanceRepository.findFirstByUserIdAndShiftIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(userId, shiftId)
                : Optional.empty();
        if (targetAtt.isEmpty()) {
            targetAtt = attendanceRepository.findFirstByUserIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(userId);
        }
        return targetAtt
                .map(att -> {
                    long workedMinutes = AttendanceCalculator.calculateWorkedMinutes(att.getCheckInTime(), now);
                    att.setCheckOutTime(now);
                    att.setWorkedMinutes(workedMinutes);
                    att.setStatus(AttendanceStatus.COMPLETED);
                    if (att.getShiftId() == null && shiftId != null) {
                        att.setShiftId(shiftId);
                    }
                    att.setNotes(StringUtils.hasText(att.getNotes())
                            ? truncate(att.getNotes() + "; " + notePrefix, 255)
                            : truncate(notePrefix, 255));
                    attendanceRepository.save(att);
                    return workedMinutes;
                })
                .orElse(null);
    }

    private Long autoCheckOutIfBranchStaff(Integer userId, Integer shiftId, Instant now, String notePrefix) {
        if (userId == null) {
            return null;
        }
        User target = userRepository.findWithRoleAndBranchById(userId).orElse(null);
        if (target == null || target.getPosition() == null
                || (!UserDirectory.BRANCH_STAFF_ROLES.contains(target.getPosition()) && target.getPosition() != Roles.BRANCH_MANAGER)) {
            return null;
        }
        return autoCheckOutForUserId(userId, shiftId, now, notePrefix);
    }

    private void cancelPendingHandoverOnForceClose(Integer shiftId, Integer actorId, Instant now) {
        shiftHandoverRepository
                .findFirstByShiftIdAndStatusOrderByIdDesc(shiftId, ShiftHandoverStatus.PENDING_RECEIVE)
                .ifPresent(pending -> {
                    pending.setStatus(ShiftHandoverStatus.CANCELLED);
                    pending.setCancelledAt(now);
                    pending.setCancelledBy(actorId);
                    pending.setCancelReason("Hủy bàn giao tự động khi Quản lý/COO đóng hẳn ca làm việc.");
                    shiftHandoverRepository.save(pending);
                });
    }

    private void createEarlyCloseAlert(
            Shift shift,
            Integer requesterId,
            String requesterName,
            BigDecimal counted,
            BigDecimal expected,
            BigDecimal variance,
            String earlyReason,
            String discrepancyReason) {
        String branchName = branchRepository.findById(shift.getBranchId())
                .map(Branch::getName)
                .orElse("Chi nhánh #" + shift.getBranchId());
        String plannedEndHm = shift.getPlannedEnd() != null
                ? DateTimeUtil.formatHmVN(shift.getPlannedEnd())
                : "cuối ca";
        String shiftLabel = shiftTypeLabel(shift.getShiftType()) + " #" + shift.getId();
        boolean hasVariance = variance != null && variance.compareTo(BigDecimal.ZERO) != 0;

        String title = "Yêu cầu đóng ca sớm · " + shiftLabel + " · " + requesterName;
        StringBuilder msg = new StringBuilder();
        msg.append(requesterName)
                .append(" gửi yêu cầu đóng ")
                .append(shiftLabel)
                .append(" sớm hơn giờ kết thúc (").append(plannedEndHm).append(") tại ").append(branchName).append(". ")
                .append("Kiểm két: ").append(counted != null ? counted.toPlainString() : "0").append(" đ")
                .append(" (sổ sách ").append(expected != null ? expected.toPlainString() : "0").append(" đ");
        if (hasVariance) {
            msg.append(", lệch ").append(variance.toPlainString()).append(" đ");
            if (StringUtils.hasText(discrepancyReason)) {
                msg.append(" — ").append(discrepancyReason);
            }
        }
        msg.append(").");
        if (StringUtils.hasText(earlyReason)) {
            msg.append(" Lý do: ").append(earlyReason);
        }

        SecurityAlert alert = SecurityAlert.builder()
                .branchId(shift.getBranchId())
                .branchName(branchName)
                .alertType(SecurityAlertType.EARLY_SHIFT_CLOSE_REQUEST)
                .severity(SecurityAlertSeverity.HIGH)
                .status(SecurityAlertStatus.ACTIVE)
                .targetUserId(requesterId)
                .targetUserName(requesterName)
                .targetRole(Roles.CASHIER.code())
                .failureCount(0)
                .referenceShiftId(shift.getId())
                .title(truncate(title, 200))
                .message(truncate(msg.toString(), 500))
                .build();
        securityAlertRepository.save(alert);
    }

    private void resolveEarlyCloseAlerts(
            Integer shiftId,
            SecurityAlertStatus finalStatus,
            UserPrincipal actor,
            Integer actorId,
            Instant now,
            String note) {
        if (shiftId == null) {
            return;
        }
        List<SecurityAlert> alerts = securityAlertRepository.findByReferenceShiftIdAndAlertTypeAndStatusIn(
                shiftId,
                SecurityAlertType.EARLY_SHIFT_CLOSE_REQUEST,
                List.of(SecurityAlertStatus.ACTIVE, SecurityAlertStatus.ACKNOWLEDGED));
        if (alerts.isEmpty()) {
            return;
        }
        String resolverName = resolveActorDisplayName(null, actor, actorId);
        for (SecurityAlert alert : alerts) {
            alert.setStatus(finalStatus);
            alert.setResolvedBy(actorId);
            alert.setResolvedByName(resolverName);
            alert.setResolvedAt(now);
            alert.setResolveNote(truncate(note, 255));
        }
        securityAlertRepository.saveAll(alerts);
    }

    private void createManagerCloseAlert(
            Shift shift,
            Integer cashierId,
            String cashierName,
            BigDecimal counted,
            BigDecimal expected,
            BigDecimal variance,
            String handoverReason,
            String discrepancyReason,
            boolean earlyClose) {
        String branchName = branchRepository.findById(shift.getBranchId())
                .map(Branch::getName)
                .orElse("Chi nhánh #" + shift.getBranchId());
        String shiftLabel = shiftTypeLabel(shift.getShiftType()) + " #" + shift.getId();
        boolean hasVariance = variance != null && variance.compareTo(BigDecimal.ZERO) != 0;

        String title = "Bàn giao két cuối ca · " + shiftLabel + " · " + cashierName;
        StringBuilder msg = new StringBuilder();
        msg.append(cashierName)
                .append(" đã kiểm két cuối ca (").append(shiftLabel).append(" tại ").append(branchName)
                .append(earlyClose ? ", đóng sớm so với giờ kết thúc" : "")
                .append(") và tự check-out, chờ Quản lý xác nhận nhận tiền & đóng ca. ")
                .append("Số tiền bàn giao: ").append(counted != null ? counted.toPlainString() : "0").append(" đ")
                .append(" (sổ sách ").append(expected != null ? expected.toPlainString() : "0").append(" đ");
        if (hasVariance) {
            msg.append(", lệch ").append(variance.toPlainString()).append(" đ");
            if (StringUtils.hasText(discrepancyReason)) {
                msg.append(" — ").append(discrepancyReason);
            }
        }
        msg.append(").");
        if (StringUtils.hasText(handoverReason)) {
            msg.append(" Ghi chú: ").append(handoverReason);
        }

        SecurityAlert alert = SecurityAlert.builder()
                .branchId(shift.getBranchId())
                .branchName(branchName)
                .alertType(SecurityAlertType.MANAGER_SHIFT_CLOSE_REQUEST)
                .severity(SecurityAlertSeverity.HIGH)
                .status(SecurityAlertStatus.ACTIVE)
                .targetUserId(cashierId)
                .targetUserName(cashierName)
                .targetRole(Roles.CASHIER.code())
                .failureCount(0)
                .referenceShiftId(shift.getId())
                .title(truncate(title, 200))
                .message(truncate(msg.toString(), 500))
                .build();
        securityAlertRepository.save(alert);
    }

    private void resolveManagerCloseAlerts(
            Integer shiftId,
            SecurityAlertStatus finalStatus,
            UserPrincipal actor,
            Integer actorId,
            Instant now,
            String note) {
        if (shiftId == null) {
            return;
        }
        List<SecurityAlert> alerts = securityAlertRepository.findByReferenceShiftIdAndAlertTypeAndStatusIn(
                shiftId,
                SecurityAlertType.MANAGER_SHIFT_CLOSE_REQUEST,
                List.of(SecurityAlertStatus.ACTIVE, SecurityAlertStatus.ACKNOWLEDGED));
        if (alerts.isEmpty()) {
            return;
        }
        String resolverName = resolveActorDisplayName(null, actor, actorId);
        for (SecurityAlert alert : alerts) {
            alert.setStatus(finalStatus);
            alert.setResolvedBy(actorId);
            alert.setResolvedByName(resolverName);
            alert.setResolvedAt(now);
            alert.setResolveNote(truncate(note, 255));
        }
        securityAlertRepository.saveAll(alerts);
    }

    private List<PreOpenCashAdjustmentResponse> toPreOpenCashAdjustmentResponses(List<PreOpenCashAdjustment> list) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        Set<Integer> userIds = new HashSet<>();
        for (PreOpenCashAdjustment adj : list) {
            if (adj.getCreatedBy() != null) {
                userIds.add(adj.getCreatedBy());
            }
            if (adj.getApprovedBy() != null) {
                userIds.add(adj.getApprovedBy());
            }
        }
        Map<Integer, String> userNames = loadUserNameMap(userIds);
        return list.stream().map(adj -> new PreOpenCashAdjustmentResponse(
                adj.getId(),
                adj.getBranchId(),
                adj.getAmount(),
                adj.getReason(),
                adj.getCreatedBy(),
                adj.getCreatedBy() != null ? userNames.get(adj.getCreatedBy()) : null,
                adj.getApprovedBy(),
                adj.getApprovedBy() != null ? userNames.get(adj.getApprovedBy()) : null,
                adj.getAppliedShiftId(),
                adj.getAppliedAt(),
                adj.getCancelledAt(),
                adj.getCreatedAt()
        )).toList();
    }

    private static BigDecimal resolvePreOpenCashIn(Shift shift) {
        return shift != null && shift.getPreOpenCashIn() != null ? shift.getPreOpenCashIn() : BigDecimal.ZERO;
    }

    private static String buildIncomingHandoverNote(String discrepancyReason, String approveNote) {
        if (StringUtils.hasText(discrepancyReason) && StringUtils.hasText(approveNote)) {
            return discrepancyReason + " — " + approveNote;
        }
        if (StringUtils.hasText(discrepancyReason)) {
            return discrepancyReason;
        }
        return StringUtils.hasText(approveNote) ? approveNote : null;
    }

    private String resolveActorDisplayName(User loadedUser, UserPrincipal principal, Integer actorId) {
        if (loadedUser != null && StringUtils.hasText(loadedUser.getFullName())) {
            return loadedUser.getFullName();
        }
        if (principal != null && StringUtils.hasText(principal.fullName())) {
            return principal.fullName();
        }
        if (actorId != null) {
            return userRepository.findById(actorId)
                    .map(User::getFullName)
                    .orElse(principal != null && StringUtils.hasText(principal.username())
                            ? principal.username()
                            : "Nhân viên #" + actorId);
        }
        return "Hệ thống";
    }

    private static String truncate(String s, int maxLen) {
        if (s == null || s.length() <= maxLen) {
            return s;
        }
        return s.substring(0, maxLen - 1) + "…";
    }
}
