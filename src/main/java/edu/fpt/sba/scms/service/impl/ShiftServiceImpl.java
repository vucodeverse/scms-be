package edu.fpt.sba.scms.service.impl;

import edu.fpt.sba.scms.common.audit.AuditLogService;
import edu.fpt.sba.scms.common.enums.AuditAction;
import edu.fpt.sba.scms.common.enums.BusinessErrorCode;
import edu.fpt.sba.scms.common.enums.CashTransactionType;
import edu.fpt.sba.scms.common.enums.ErrorCode;
import edu.fpt.sba.scms.common.enums.Roles;
import edu.fpt.sba.scms.common.enums.ShiftStatus;
import edu.fpt.sba.scms.common.enums.SortDirection;
import edu.fpt.sba.scms.common.enums.UserStatus;
import edu.fpt.sba.scms.common.exception.BusinessRuleException;
import edu.fpt.sba.scms.common.exception.ForbiddenException;
import edu.fpt.sba.scms.common.exception.InvalidFieldValueException;
import edu.fpt.sba.scms.common.exception.NotFoundException;
import edu.fpt.sba.scms.domain.shift.ShiftCashCalculator;
import edu.fpt.sba.scms.domain.shift.ShiftSchedulePolicy;
import edu.fpt.sba.scms.dto.PageQueryDefaults;
import edu.fpt.sba.scms.dto.request.CashAdjustmentRequest;
import edu.fpt.sba.scms.dto.request.CloseShiftRequest;
import edu.fpt.sba.scms.dto.request.CreateScheduleShiftRequest;
import edu.fpt.sba.scms.dto.request.OpenShiftRequest;
import edu.fpt.sba.scms.dto.request.UpdateScheduleShiftRequest;
import edu.fpt.sba.scms.dto.request.query.ShiftListQuery;
import edu.fpt.sba.scms.dto.response.BranchShiftOverviewResponse;
import edu.fpt.sba.scms.dto.response.CashTransactionResponse;
import edu.fpt.sba.scms.dto.response.PreviousShiftSummaryResponse;
import edu.fpt.sba.scms.dto.response.ShiftResponse;
import edu.fpt.sba.scms.dto.response.ShiftScheduleResponse;
import edu.fpt.sba.scms.dto.response.ShiftSummaryResponse;
import edu.fpt.sba.scms.mapper.ShiftMapper;
import edu.fpt.sba.scms.model.Branch;
import edu.fpt.sba.scms.model.CashTransaction;
import edu.fpt.sba.scms.model.Shift;
import edu.fpt.sba.scms.model.ShiftAssignment;
import edu.fpt.sba.scms.model.ShiftAssignmentId;
import edu.fpt.sba.scms.model.User;
import edu.fpt.sba.scms.repository.BranchRepository;
import edu.fpt.sba.scms.repository.CashTransactionRepository;
import edu.fpt.sba.scms.repository.ShiftAssignmentRepository;
import edu.fpt.sba.scms.repository.ShiftRepository;
import edu.fpt.sba.scms.repository.ShiftSpecs;
import edu.fpt.sba.scms.repository.UserRepository;
import edu.fpt.sba.scms.security.BranchContext;
import edu.fpt.sba.scms.security.principal.UserPrincipal;
import edu.fpt.sba.scms.service.ShiftService;
import edu.fpt.sba.scms.service.support.PinHasher;
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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShiftServiceImpl implements ShiftService {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int MAX_REASON_LEN = 500;

    private final ShiftRepository shiftRepository;
    private final CashTransactionRepository cashTransactionRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final PinHasher pinHasher;
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

        Integer previousShiftId = null;
        BigDecimal previousClosingCash = null;
        BigDecimal openingVariance = null;
        String trimmedNote = StringUtils.hasText(request.note()) ? request.note().trim() : null;

        if (previousClosed != null) {
            previousShiftId = previousClosed.getId();
            previousClosingCash = resolvePreviousClosingCash(previousClosed);
            openingVariance = ShiftCashCalculator.calculateOpeningVariance(
                    request.openingCash(), previousClosingCash);
            if (ShiftCashCalculator.hasOpeningDiscrepancy(request.openingCash(), previousClosingCash)
                    && !StringUtils.hasText(trimmedNote)) {
                throw new BusinessRuleException(BusinessErrorCode.REASON_REQUIRED,
                        "Tiền đầu ca (" + request.openingCash().toPlainString()
                                + " đ) lệch " + openingVariance.toPlainString()
                                + " đ so với kết ca trước (" + previousClosingCash.toPlainString()
                                + " đ). Vui lòng nhập ghi chú bàn giao / giải trình chênh lệch đầu ca.");
            }
        }

        Integer actorId = resolveActorId(principal);
        populateOpeningFields(shift, request.openingCash(), actorId, now,
                previousShiftId, previousClosingCash, openingVariance, trimmedNote);

        Shift savedShift = shiftRepository.save(shift);
        recordOpeningCashTransaction(
                savedShift.getId(), request.openingCash(), trimmedNote,
                previousClosingCash, openingVariance, actorId);

        String auditDetail = "Mở ca ID=" + savedShift.getId() + ", tiền đầu ca=" + request.openingCash();
        if (previousClosingCash != null) {
            auditDetail += ", kết ca trước=" + previousClosingCash + ", chênh lệch đầu ca=" + openingVariance;
        }
        auditLogService.log(AuditAction.SHIFT_OPEN, "shift", savedShift.getId(), null, auditDetail);

        log.info("Mở ca thành công: shiftId={}, branchId={}, openingCash={}, openingVariance={}",
                savedShift.getId(), branchId, request.openingCash(), openingVariance);
        return enrichShiftResponse(savedShift);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CashTransactionResponse cashIn(Integer shiftId, CashAdjustmentRequest request, UserPrincipal principal) {
        ensureCanAdjustCash(principal);
        Shift shift = getOpenShiftForUpdateOrThrow(shiftId);

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
    public ShiftResponse closeShift(Integer shiftId, CloseShiftRequest request, UserPrincipal principal) {
        Shift shift = getOpenShiftForUpdateOrThrow(shiftId);

        BigDecimal expected = shift.getExpectedCash() != null ? shift.getExpectedCash() : shift.getOpeningCash();
        BigDecimal counted = request.countedCash();
        BigDecimal variance = ShiftCashCalculator.calculateVariance(counted, expected);

        if (ShiftCashCalculator.hasDiscrepancy(counted, expected) && !StringUtils.hasText(request.discrepancyReason())) {
            throw new BusinessRuleException(BusinessErrorCode.REASON_REQUIRED,
                    "Phát hiện chênh lệch tiền mặt thực tế so với sổ sách (" + variance.toPlainString()
                            + " đ). Bắt buộc phải nhập lý do giải trình theo quy định BR-38.");
        }

        Instant now = Instant.now();
        Integer actorId = resolveActorId(principal);
        shift.setStatus(ShiftStatus.CLOSED);
        shift.setCountedCash(counted);
        shift.setVariance(variance);
        shift.setDiscrepancyReason(request.discrepancyReason());
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

        auditLogService.log(AuditAction.SHIFT_CLOSE, "shift", savedShift.getId(), null,
                "Đóng ca ID=" + savedShift.getId() + ", Counted=" + counted + ", Expected=" + expected + ", Variance=" + variance);

        log.info("Đóng ca thành công: shiftId={}, counted={}, expected={}, variance={}", savedShift.getId(), counted,
                expected, variance);
        return enrichShiftResponse(savedShift);
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
    public ShiftSummaryResponse getShiftSummary(Integer shiftId) {
        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new NotFoundException("ca làm việc"));
        BranchContext.resolveBranchScope(shift.getBranchId());
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

        return shiftRepository.findFirstByBranchIdAndStatusOrderByClosedAtDescIdDesc(branchId, ShiftStatus.CLOSED)
                .map(prev -> {
                    String closedByName = prev.getClosedBy() != null
                            ? loadUserNameMap(List.of(prev.getClosedBy())).get(prev.getClosedBy())
                            : null;
                    BigDecimal closingCash = resolvePreviousClosingCash(prev);
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
                            prev.getVariance()
                    );
                })
                .orElseGet(PreviousShiftSummaryResponse::none);
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

        Set<Integer> openerIds = openShiftByBranch.values().stream()
                .map(Shift::getOpenedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Integer, String> userNames = loadUserNameMap(openerIds);

        return branches.stream().map(b -> {
            Shift open = openShiftByBranch.get(b.getId());
            Shift prevClosed = lastClosedByBranch.get(b.getId());
            Integer openedBy = open != null ? open.getOpenedBy() : null;
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
                    prevClosed != null ? prevClosed.getClosedAt() : null
            );
        }).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ShiftResponse> listShifts(ShiftListQuery query) {
        Integer branchId = BranchContext.resolveBranchScope(query.branchId());
        Sort.Direction dir = query.sortDir() == SortDirection.ASC ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(new Sort.Order(dir, "shiftDate"), new Sort.Order(dir, "plannedStart"),
                new Sort.Order(dir, "id"));
        Pageable pageable = PageQueryDefaults.toPageable(query.page(), query.size(), sort);
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

        if (!ShiftSchedulePolicy.isValidShiftTime(request.plannedStart(), request.plannedEnd())) {
            throw new BusinessRuleException(BusinessErrorCode.INVALID_SHIFT_TIME,
                    "Thời gian bắt đầu ca phải trước thời gian kết thúc.");
        }

        List<Integer> empIds = deduplicateIds(request.assignedEmployeeIds());
        Map<Integer, User> userMap = loadAndValidateBranchEmployees(branchId, empIds);
        validateScheduleConflicts(empIds, 0, request.plannedStart(), request.plannedEnd(), userMap);

        Shift shift = Shift.builder()
                .branchId(branchId)
                .shiftDate(request.shiftDate())
                .shiftType(request.shiftType())
                .plannedStart(request.plannedStart())
                .plannedEnd(request.plannedEnd())
                .status(ShiftStatus.SCHEDULED)
                .build();
        Shift savedShift = shiftRepository.save(shift);

        List<Integer> assignedIds = assignEmployeesToShift(savedShift, empIds, userMap);

        auditLogService.log(AuditAction.SCHEDULE_SAVE, "shift", savedShift.getId(), null,
                "Tạo lịch ca ID=" + savedShift.getId() + " ngày " + request.shiftDate() + ", nhân viên: " + assignedIds);

        return shiftMapper.toShiftScheduleResponse(savedShift, assignedIds);
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
        if (!ShiftSchedulePolicy.isValidShiftTime(request.plannedStart(), request.plannedEnd())) {
            throw new BusinessRuleException(BusinessErrorCode.INVALID_SHIFT_TIME,
                    "Thời gian bắt đầu ca phải trước thời gian kết thúc.");
        }

        List<Integer> empIds = deduplicateIds(request.assignedEmployeeIds());
        Map<Integer, User> userMap = loadAndValidateBranchEmployees(branchId, empIds);
        validateScheduleConflicts(empIds, shiftId, request.plannedStart(), request.plannedEnd(), userMap);

        if (shift.getStatus() == ShiftStatus.SCHEDULED) {
            shift.setShiftDate(request.shiftDate());
            shift.setShiftType(request.shiftType());
        }
        shift.setPlannedStart(request.plannedStart());
        shift.setPlannedEnd(request.plannedEnd());
        Shift savedShift = shiftRepository.save(shift);

        shiftAssignmentRepository.deleteByIdShiftId(savedShift.getId());
        shiftAssignmentRepository.flush();
        List<Integer> assignedIds = assignEmployeesToShift(savedShift, empIds, userMap);

        auditLogService.log(AuditAction.SCHEDULE_SAVE, "shift", savedShift.getId(), null,
                "Cập nhật lịch ca ID=" + savedShift.getId() + " ngày " + savedShift.getShiftDate() + ", nhân viên: " + assignedIds);

        return shiftMapper.toShiftScheduleResponse(savedShift, assignedIds);
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
            result.add(shiftMapper.toShiftScheduleResponse(s, empIds));
        }
        return result;
    }

    private ShiftSummaryResponse buildShiftSummary(Shift shift) {
        List<CashTransaction> txList = cashTransactionRepository.findByShiftIdOrderByCreatedAtDesc(shift.getId());

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
                shift.getExpectedCash()
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
            scheduled.setStatus(ShiftStatus.OPEN);
            return scheduled;
        }

        LocalTime localNow = LocalTime.now(VN_ZONE);
        return Shift.builder()
                .branchId(branchId)
                .shiftDate(LocalDate.now(VN_ZONE))
                .shiftType(ShiftSchedulePolicy.inferShiftType(localNow))
                .plannedStart(now)
                .plannedEnd(now.plus(Duration.ofHours(8)))
                .status(ShiftStatus.OPEN)
                .build();
    }

    private static BigDecimal resolvePreviousClosingCash(Shift previousClosed) {
        return previousClosed.getCountedCash() != null
                ? previousClosed.getCountedCash()
                : previousClosed.getExpectedCash();
    }

    private void populateOpeningFields(Shift shift,
                                       BigDecimal openingCash,
                                       Integer actorId,
                                       Instant now,
                                       Integer previousShiftId,
                                       BigDecimal previousClosingCash,
                                       BigDecimal openingVariance,
                                       String openingNote) {
        shift.setOpenedBy(actorId);
        shift.setOpenedAt(now);
        shift.setOpeningCash(openingCash);
        shift.setExpectedCash(openingCash);
        shift.setCashRevenue(BigDecimal.ZERO);
        shift.setTransferRevenue(BigDecimal.ZERO);
        shift.setPreviousShiftId(previousShiftId);
        shift.setPreviousClosingCash(previousClosingCash);
        shift.setOpeningVariance(openingVariance);
        shift.setOpeningNote(openingNote);
    }

    private void recordOpeningCashTransaction(Integer shiftId,
                                              BigDecimal openingCash,
                                              String note,
                                              BigDecimal previousClosingCash,
                                              BigDecimal openingVariance,
                                              Integer actorId) {
        String reason;
        if (previousClosingCash != null && openingVariance != null && openingVariance.compareTo(BigDecimal.ZERO) != 0) {
            reason = "Đối soát đầu ca lệch " + openingVariance.toPlainString()
                    + " đ so với kết ca trước (" + previousClosingCash.toPlainString() + " đ): " + note;
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
                .amount(openingCash)
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
        }
        Map<Integer, String> branchNames = loadBranchNameMap(branchIds);
        Map<Integer, String> userNames = loadUserNameMap(userIds);

        return shifts.stream().map(s -> {
            ShiftResponse base = shiftMapper.toShiftResponse(s);
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
                    base.openingNote()
            );
        }).toList();
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

    private List<Integer> assignEmployeesToShift(Shift shift, List<Integer> empIds, Map<Integer, User> userMap) {
        if (empIds.isEmpty()) {
            return List.of();
        }
        List<ShiftAssignment> assignments = new ArrayList<>();
        List<Integer> assignedIds = new ArrayList<>();

        for (Integer empId : empIds) {
            User emp = userMap.get(empId);
            ShiftAssignment sa = ShiftAssignment.builder()
                    .id(new ShiftAssignmentId(shift.getId(), empId))
                    .shift(shift)
                    .employee(emp)
                    .roleInShift(emp.roleCode())
                    .build();
            assignments.add(sa);
            assignedIds.add(empId);
        }
        shiftAssignmentRepository.saveAll(assignments);
        return assignedIds;
    }

    private Integer resolveActorId(UserPrincipal principal) {
        return principal != null ? principal.userId() : null;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null || s.length() <= maxLen) {
            return s;
        }
        return s.substring(0, maxLen - 1) + "…";
    }
}
