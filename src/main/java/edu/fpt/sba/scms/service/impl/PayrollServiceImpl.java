package edu.fpt.sba.scms.service.impl;

import edu.fpt.sba.scms.common.audit.AuditLogService;
import edu.fpt.sba.scms.common.enums.AuditAction;
import edu.fpt.sba.scms.common.enums.BusinessErrorCode;
import edu.fpt.sba.scms.common.enums.PayPeriodStatus;
import edu.fpt.sba.scms.common.exception.BusinessRuleException;
import edu.fpt.sba.scms.common.exception.NotFoundException;
import edu.fpt.sba.scms.domain.payroll.PayrollCalculator;
import edu.fpt.sba.scms.domain.payroll.PayrollPeriodPolicy;
import edu.fpt.sba.scms.dto.request.CreatePayPeriodRequest;
import edu.fpt.sba.scms.dto.request.UpdatePayrollItemRequest;
import edu.fpt.sba.scms.dto.response.PayPeriodResponse;
import edu.fpt.sba.scms.dto.response.PayrollItemResponse;
import edu.fpt.sba.scms.mapper.PayrollMapper;
import edu.fpt.sba.scms.model.Attendance;
import edu.fpt.sba.scms.model.PayPeriod;
import edu.fpt.sba.scms.model.PayrollItem;
import edu.fpt.sba.scms.model.User;
import edu.fpt.sba.scms.model.WageRate;
import edu.fpt.sba.scms.repository.AttendanceRepository;
import edu.fpt.sba.scms.repository.PayPeriodRepository;
import edu.fpt.sba.scms.repository.PayrollItemRepository;
import edu.fpt.sba.scms.repository.UserRepository;
import edu.fpt.sba.scms.repository.WageRateRepository;
import edu.fpt.sba.scms.security.principal.UserPrincipal;
import edu.fpt.sba.scms.service.PayrollService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollServiceImpl implements PayrollService {

    private static final BigDecimal DEFAULT_HOURLY_RATE = BigDecimal.valueOf(25000); // 25.000 đ/giờ mặc định

    private final PayPeriodRepository payPeriodRepository;
    private final PayrollItemRepository payrollItemRepository;
    private final AttendanceRepository attendanceRepository;
    private final WageRateRepository wageRateRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final PayrollMapper payrollMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayPeriodResponse createPayPeriod(CreatePayPeriodRequest request, UserPrincipal principal) {
        if (request.startDate().isAfter(request.endDate())) {
            throw new BusinessRuleException(BusinessErrorCode.REASON_REQUIRED,
                    "Ngày bắt đầu kỳ lương phải trước hoặc bằng ngày kết thúc.");
        }

        Instant startInstant = request.startDate().atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endInstant = request.endDate().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusNanos(1);

        PayPeriod period = PayPeriod.builder()
                .branchId(request.branchId())
                .periodName(request.periodName())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .status(PayPeriodStatus.DRAFT)
                .totalAmount(BigDecimal.ZERO)
                .createdBy(principal != null ? principal.userId() : 1)
                .build();
        PayPeriod savedPeriod = payPeriodRepository.save(period);

        // 1. Quét toàn bộ chấm công hoàn tất trong kỳ để tổng hợp giờ làm từng nhân viên
        List<Attendance> attendances = attendanceRepository.findCompletedAttendancesInPeriod(
                request.branchId(), startInstant, endInstant);

        Map<Integer, Long> minutesByUser = new HashMap<>();
        for (Attendance att : attendances) {
            if (att.getCheckInTime() != null && att.getCheckOutTime() != null) {
                long mins = Duration.between(att.getCheckInTime(), att.getCheckOutTime()).toMinutes();
                minutesByUser.merge(att.getUserId(), Math.max(0, mins), Long::sum);
            }
        }

        BigDecimal totalPeriodSalary = BigDecimal.ZERO;
        int employeeCount = 0;

        for (Map.Entry<Integer, Long> entry : minutesByUser.entrySet()) {
            Integer userId = entry.getKey();
            Long totalMinutes = entry.getValue();

            // Quy đổi phút làm sang giờ làm và tính lương qua PayrollCalculator (Domain)
            BigDecimal hours = PayrollCalculator.minutesToHours(totalMinutes);

            WageRate wageRate = wageRateRepository.findFirstByUserIdOrderByEffectiveFromDesc(userId).orElse(null);
            BigDecimal rate = wageRate != null ? wageRate.getHourlyRate() : DEFAULT_HOURLY_RATE;
            BigDecimal baseSalary = PayrollCalculator.calculateBaseSalary(hours, rate);

            PayrollItem item = PayrollItem.builder()
                    .payPeriodId(savedPeriod.getId())
                    .userId(userId)
                    .totalHours(hours)
                    .hourlyRate(rate)
                    .baseSalary(baseSalary)
                    .bonus(BigDecimal.ZERO)
                    .deduction(BigDecimal.ZERO)
                    .netSalary(baseSalary)
                    .note("Tổng hợp tự động từ " + totalMinutes + " phút chấm công")
                    .build();
            payrollItemRepository.save(item);

            totalPeriodSalary = totalPeriodSalary.add(baseSalary);
            employeeCount++;
        }

        savedPeriod.setTotalAmount(totalPeriodSalary);
        payPeriodRepository.save(savedPeriod);

        auditLogService.log(AuditAction.PAYROLL_CREATE, "pay_period", savedPeriod.getId(), null,
                "Tạo kỳ lương ID=" + savedPeriod.getId() + " (" + request.periodName() + "), tổng tiền=" + totalPeriodSalary);

        log.info("Tạo kỳ tính lương thành công: periodId={}, branchId={}, totalEmployees={}, totalAmount={}",
                savedPeriod.getId(), request.branchId(), employeeCount, totalPeriodSalary);

        return payrollMapper.toPayPeriodResponse(savedPeriod, employeeCount);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayrollItemResponse updatePayrollItem(Integer itemId, UpdatePayrollItemRequest request, UserPrincipal principal) {
        PayrollItem item = payrollItemRepository.findById(itemId)
                .orElseThrow(() -> new NotFoundException("phiếu lương"));

        PayPeriod period = payPeriodRepository.findById(item.getPayPeriodId())
                .orElseThrow(() -> new NotFoundException("kỳ lương"));

        // BR-53: Nếu bảng lương đã duyệt hoặc đã thanh toán thì không được sửa (kiểm tra qua PayrollPeriodPolicy)
        if (!PayrollPeriodPolicy.isEditable(period.getStatus())) {
            throw new BusinessRuleException(BusinessErrorCode.PAYROLL_LOCKED,
                    "Kỳ lương đã được phê duyệt hoặc thanh toán, không thể chỉnh sửa.");
        }

        item.setBonus(request.bonus());
        item.setDeduction(request.deduction());
        if (request.note() != null) {
            item.setNote(request.note());
        }

        // Net = Base + Bonus - Deduction qua PayrollCalculator (Domain)
        BigDecimal net = PayrollCalculator.calculateNetSalary(item.getBaseSalary(), request.bonus(), request.deduction());
        item.setNetSalary(net);
        PayrollItem savedItem = payrollItemRepository.save(item);

        // Cập nhật lại tổng tiền kỳ lương qua PayrollCalculator (Domain)
        List<PayrollItem> allItems = payrollItemRepository.findByPayPeriodId(period.getId());
        List<BigDecimal> netList = allItems.stream().map(PayrollItem::getNetSalary).toList();
        BigDecimal total = PayrollCalculator.calculatePeriodTotal(netList);
        period.setTotalAmount(total);
        payPeriodRepository.save(period);

        User user = userRepository.findById(item.getUserId()).orElse(null);
        String fullName = user != null ? user.getFullName() : "Nhân viên #" + item.getUserId();
        String role = user != null ? user.roleCode() : "";

        return payrollMapper.toPayrollItemResponse(savedItem, fullName, role);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayPeriodResponse submitPayroll(Integer periodId, UserPrincipal principal) {
        PayPeriod period = payPeriodRepository.findById(periodId)
                .orElseThrow(() -> new NotFoundException("kỳ lương"));

        if (period.getStatus() != PayPeriodStatus.DRAFT) {
            throw new BusinessRuleException(BusinessErrorCode.PAYROLL_LOCKED,
                    "Chỉ kỳ lương ở trạng thái DRAFT mới có thể gửi phê duyệt.");
        }

        period.setStatus(PayPeriodStatus.SUBMITTED);
        PayPeriod saved = payPeriodRepository.save(period);

        auditLogService.log(AuditAction.PAYROLL_SUBMIT, "pay_period", saved.getId(), null,
                "Gửi duyệt kỳ lương ID=" + saved.getId() + " lên Giám đốc vận hành (COO)");

        List<PayrollItem> items = payrollItemRepository.findByPayPeriodId(period.getId());
        return payrollMapper.toPayPeriodResponse(saved, items.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayPeriodResponse approvePayroll(Integer periodId, UserPrincipal principal) {
        PayPeriod period = payPeriodRepository.findById(periodId)
                .orElseThrow(() -> new NotFoundException("kỳ lương"));

        if (period.getStatus() != PayPeriodStatus.SUBMITTED) {
            throw new BusinessRuleException(BusinessErrorCode.PAYROLL_LOCKED,
                    "Chỉ kỳ lương ở trạng thái SUBMITTED mới có thể phê duyệt.");
        }

        period.setStatus(PayPeriodStatus.APPROVED);
        period.setApprovedBy(principal != null ? principal.userId() : 1);
        period.setApprovedAt(Instant.now());
        PayPeriod saved = payPeriodRepository.save(period);

        auditLogService.log(AuditAction.PAYROLL_APPROVE, "pay_period", saved.getId(), null,
                "COO phê duyệt kỳ lương ID=" + saved.getId() + ", tổng ngân sách lương=" + saved.getTotalAmount());

        List<PayrollItem> items = payrollItemRepository.findByPayPeriodId(period.getId());
        return payrollMapper.toPayPeriodResponse(saved, items.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayPeriodResponse rejectPayroll(Integer periodId, String reason, UserPrincipal principal) {
        PayPeriod period = payPeriodRepository.findById(periodId)
                .orElseThrow(() -> new NotFoundException("kỳ lương"));

        period.setStatus(PayPeriodStatus.DRAFT);
        period.setRejectionReason(reason);
        PayPeriod saved = payPeriodRepository.save(period);

        auditLogService.log(AuditAction.PAYROLL_REJECT, "pay_period", saved.getId(), null,
                "COO từ chối kỳ lương ID=" + saved.getId() + ", lý do: " + reason);

        List<PayrollItem> items = payrollItemRepository.findByPayPeriodId(period.getId());
        return payrollMapper.toPayPeriodResponse(saved, items.size());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PayPeriodResponse> listPayPeriods(Integer branchId) {
        List<PayPeriod> periods = payPeriodRepository.findByBranchIdOrderByStartDateDesc(branchId);
        List<PayPeriodResponse> result = new ArrayList<>();
        for (PayPeriod p : periods) {
            List<PayrollItem> items = payrollItemRepository.findByPayPeriodId(p.getId());
            result.add(payrollMapper.toPayPeriodResponse(p, items.size()));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PayrollItemResponse> getPayrollItems(Integer periodId) {
        List<PayrollItem> items = payrollItemRepository.findByPayPeriodId(periodId);
        List<PayrollItemResponse> result = new ArrayList<>();
        for (PayrollItem item : items) {
            User user = userRepository.findById(item.getUserId()).orElse(null);
            String fullName = user != null ? user.getFullName() : "Nhân viên #" + item.getUserId();
            String role = user != null ? user.roleCode() : "";
            result.add(payrollMapper.toPayrollItemResponse(item, fullName, role));
        }
        return result;
    }
}

