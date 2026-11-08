package edu.fpt.sba.scms.service.impl;

import edu.fpt.sba.scms.common.audit.AuditLogService;
import edu.fpt.sba.scms.common.enums.AuditAction;
import edu.fpt.sba.scms.common.enums.BusinessErrorCode;
import edu.fpt.sba.scms.common.enums.CashTransactionType;
import edu.fpt.sba.scms.common.enums.Roles;
import edu.fpt.sba.scms.common.enums.ShiftStatus;
import edu.fpt.sba.scms.common.enums.ShiftType;
import edu.fpt.sba.scms.common.enums.UserStatus;
import edu.fpt.sba.scms.common.exception.BusinessRuleException;
import edu.fpt.sba.scms.common.exception.NotFoundException;
import edu.fpt.sba.scms.domain.shift.ShiftCashCalculator;
import edu.fpt.sba.scms.domain.shift.ShiftSchedulePolicy;
import edu.fpt.sba.scms.dto.request.CashAdjustmentRequest;
import edu.fpt.sba.scms.dto.request.CloseShiftRequest;
import edu.fpt.sba.scms.dto.request.CreateScheduleShiftRequest;
import edu.fpt.sba.scms.dto.request.OpenShiftRequest;
import edu.fpt.sba.scms.dto.response.CashTransactionResponse;
import edu.fpt.sba.scms.dto.response.ShiftResponse;
import edu.fpt.sba.scms.dto.response.ShiftScheduleResponse;
import edu.fpt.sba.scms.dto.response.ShiftSummaryResponse;
import edu.fpt.sba.scms.mapper.ShiftMapper;
import edu.fpt.sba.scms.model.CashTransaction;
import edu.fpt.sba.scms.model.Shift;
import edu.fpt.sba.scms.model.ShiftAssignment;
import edu.fpt.sba.scms.model.ShiftAssignmentId;
import edu.fpt.sba.scms.model.User;
import edu.fpt.sba.scms.repository.CashTransactionRepository;
import edu.fpt.sba.scms.repository.ShiftAssignmentRepository;
import edu.fpt.sba.scms.repository.ShiftRepository;
import edu.fpt.sba.scms.repository.UserRepository;
import edu.fpt.sba.scms.security.BranchContext;
import edu.fpt.sba.scms.security.principal.UserPrincipal;
import edu.fpt.sba.scms.service.ShiftService;
import edu.fpt.sba.scms.service.support.PinHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShiftServiceImpl implements ShiftService {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final ShiftRepository shiftRepository;
    private final CashTransactionRepository cashTransactionRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final UserRepository userRepository;
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
        Integer actorId = resolveActorId(principal);
        populateOpeningFields(shift, request.openingCash(), actorId, now);

        Shift savedShift = shiftRepository.save(shift);
        recordOpeningCashTransaction(savedShift.getId(), request.openingCash(), request.note(), actorId);

        auditLogService.log(AuditAction.SHIFT_OPEN, "shift", savedShift.getId(), null,
                "Mở ca ID=" + savedShift.getId() + ", tiền đầu ca=" + request.openingCash());

        log.info("Mở ca thành công: shiftId={}, branchId={}, openingCash={}", savedShift.getId(), branchId,
                request.openingCash());
        return shiftMapper.toShiftResponse(savedShift);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CashTransactionResponse cashIn(Integer shiftId, CashAdjustmentRequest request, UserPrincipal principal) {
        Shift shift = getOpenShiftForUpdateOrThrow(shiftId);

        User manager = verifyManagerPin(shift.getBranchId(), request.managerPin());
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
        return shiftMapper.toCashTransactionResponse(savedTx);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CashTransactionResponse cashOut(Integer shiftId, CashAdjustmentRequest request, UserPrincipal principal) {
        Shift shift = getOpenShiftForUpdateOrThrow(shiftId);

        User manager = verifyManagerPin(shift.getBranchId(), request.managerPin());
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
        return shiftMapper.toCashTransactionResponse(savedTx);
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
        return shiftMapper.toShiftResponse(savedShift);
    }

    @Override
    @Transactional(readOnly = true)
    public ShiftSummaryResponse getCurrentShiftSummary(Integer requestedBranchId) {
        Integer branchId = BranchContext.resolveBranchScope(requestedBranchId);
        Shift shift = shiftRepository.findFirstByBranchIdAndStatusOrderByOpenedAtDesc(branchId, ShiftStatus.OPEN)
                .orElseThrow(() -> new BusinessRuleException(BusinessErrorCode.SHIFT_NOT_OPEN,
                        "Chi nhánh chưa mở ca làm việc. Vui lòng mở ca trước khi giao dịch."));

        List<CashTransaction> txList = cashTransactionRepository.findByShiftIdOrderByCreatedAtDesc(shift.getId());

        List<ShiftCashCalculator.CashEntry> entries = txList.stream()
                .map(tx -> new ShiftCashCalculator.CashEntry(tx.getType(), tx.getAmount()))
                .toList();
        ShiftCashCalculator.CashAggregation agg = ShiftCashCalculator.aggregate(entries);

        return new ShiftSummaryResponse(
                shiftMapper.toShiftResponse(shift),
                shiftMapper.toCashTransactionResponses(txList),
                shift.getOpeningCash(),
                shift.getCashRevenue(),
                shift.getTransferRevenue(),
                agg.totalIn(),
                agg.totalOut(),
                agg.totalRefund(),
                shift.getExpectedCash()
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShiftScheduleResponse createSchedule(CreateScheduleShiftRequest request, UserPrincipal principal) {
        Integer branchId = BranchContext.resolveBranchScope(request.branchId());

        if (!ShiftSchedulePolicy.isValidShiftTime(request.plannedStart(), request.plannedEnd())) {
            throw new BusinessRuleException(BusinessErrorCode.INVALID_SHIFT_TIME,
                    "Thời gian bắt đầu ca phải trước thời gian kết thúc.");
        }

        List<Integer> empIds = request.assignedEmployeeIds();
        validateScheduleConflicts(empIds, request.plannedStart(), request.plannedEnd());

        Shift shift = Shift.builder()
                .branchId(branchId)
                .shiftDate(request.shiftDate())
                .shiftType(request.shiftType())
                .plannedStart(request.plannedStart())
                .plannedEnd(request.plannedEnd())
                .status(ShiftStatus.SCHEDULED)
                .build();
        Shift savedShift = shiftRepository.save(shift);

        List<Integer> assignedIds = assignEmployeesToShift(savedShift, empIds);

        auditLogService.log(AuditAction.SCHEDULE_SAVE, "shift", savedShift.getId(), null,
                "Tạo lịch ca ID=" + savedShift.getId() + " ngày " + request.shiftDate() + ", nhân viên: " + assignedIds);

        return shiftMapper.toShiftScheduleResponse(savedShift, assignedIds);
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

    private void validateNoOpenShiftInBranch(Integer branchId) {
        if (shiftRepository.existsByBranchIdAndStatus(branchId, ShiftStatus.OPEN)) {
            throw new BusinessRuleException(BusinessErrorCode.SHIFT_ALREADY_OPEN,
                    "Chi nhánh hiện đang có một ca làm việc đang mở. Vui lòng đóng ca trước khi mở ca mới.");
        }
    }

    private Shift resolveShift(OpenShiftRequest request, Integer branchId, Instant now) {
        if (request.scheduledShiftId() != null) {
            Shift scheduled = shiftRepository.findById(request.scheduledShiftId())
                    .orElseThrow(() -> new NotFoundException("ca làm việc"));
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

    private void populateOpeningFields(Shift shift, BigDecimal openingCash, Integer actorId, Instant now) {
        shift.setOpenedBy(actorId);
        shift.setOpenedAt(now);
        shift.setOpeningCash(openingCash);
        shift.setExpectedCash(openingCash);
        shift.setCashRevenue(BigDecimal.ZERO);
        shift.setTransferRevenue(BigDecimal.ZERO);
    }

    private void recordOpeningCashTransaction(Integer shiftId, BigDecimal openingCash, String note, Integer actorId) {
        CashTransaction openTx = CashTransaction.builder()
                .shiftId(shiftId)
                .type(CashTransactionType.OPEN)
                .amount(openingCash)
                .reason(StringUtils.hasText(note) ? note : "Mở ca & khai báo số dư đầu két")
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

    private void validateScheduleConflicts(List<Integer> empIds, Instant plannedStart, Instant plannedEnd) {
        if (empIds == null || empIds.isEmpty()) {
            return;
        }
        List<ShiftAssignment> conflicts = shiftAssignmentRepository.findConflictingAssignmentsForEmployees(
                empIds, 0, plannedStart, plannedEnd);
        if (!conflicts.isEmpty()) {
            Integer conflictEmpId = conflicts.get(0).getId().getEmployeeId();
            throw new BusinessRuleException(BusinessErrorCode.SCHEDULE_CONFLICT,
                    "Nhân viên ID=" + conflictEmpId + " đã có ca làm việc khác trong khung giờ này.");
        }
    }

    private List<Integer> assignEmployeesToShift(Shift shift, List<Integer> empIds) {
        if (empIds == null || empIds.isEmpty()) {
            return List.of();
        }
        Map<Integer, User> userMap = userRepository.findAllById(empIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<ShiftAssignment> assignments = new ArrayList<>();
        List<Integer> assignedIds = new ArrayList<>();

        for (Integer empId : empIds) {
            User emp = userMap.get(empId);
            if (emp == null) {
                throw new NotFoundException("nhân viên ID=" + empId);
            }
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
}
