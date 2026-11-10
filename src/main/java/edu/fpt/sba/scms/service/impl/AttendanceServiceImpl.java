package edu.fpt.sba.scms.service.impl;

import edu.fpt.sba.scms.common.enums.AttendanceStatus;
import edu.fpt.sba.scms.common.enums.BranchStatus;
import edu.fpt.sba.scms.common.enums.BusinessErrorCode;
import edu.fpt.sba.scms.common.enums.ErrorCode;
import edu.fpt.sba.scms.common.enums.Roles;
import edu.fpt.sba.scms.common.enums.ShiftStatus;
import edu.fpt.sba.scms.common.enums.ShiftType;
import edu.fpt.sba.scms.common.enums.UserStatus;
import edu.fpt.sba.scms.common.exception.BusinessRuleException;
import edu.fpt.sba.scms.common.exception.ForbiddenException;
import edu.fpt.sba.scms.common.exception.InvalidFieldValueException;
import edu.fpt.sba.scms.common.exception.InvalidPinException;
import edu.fpt.sba.scms.common.exception.NotFoundException;
import edu.fpt.sba.scms.common.exception.PinLockedException;
import edu.fpt.sba.scms.common.util.DateTimeUtil;
import edu.fpt.sba.scms.domain.attendance.AttendanceCalculator;
import edu.fpt.sba.scms.dto.request.CardAttendanceRequest;
import edu.fpt.sba.scms.dto.request.PinAttendanceRequest;
import edu.fpt.sba.scms.dto.response.AttendanceResultDto;
import edu.fpt.sba.scms.dto.response.KioskEmployeeDto;
import edu.fpt.sba.scms.mapper.AttendanceMapper;
import edu.fpt.sba.scms.model.Attendance;
import edu.fpt.sba.scms.model.Branch;
import edu.fpt.sba.scms.model.Shift;
import edu.fpt.sba.scms.model.ShiftAssignment;
import edu.fpt.sba.scms.model.User;
import edu.fpt.sba.scms.repository.AttendanceRepository;
import edu.fpt.sba.scms.repository.BranchRepository;
import edu.fpt.sba.scms.repository.ShiftAssignmentRepository;
import edu.fpt.sba.scms.repository.ShiftRepository;
import edu.fpt.sba.scms.repository.UserRepository;
import edu.fpt.sba.scms.service.AttendanceService;
import edu.fpt.sba.scms.service.support.PinHasher;
import edu.fpt.sba.scms.service.support.PinLockoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceServiceImpl implements AttendanceService {

    private static final Duration OVERNIGHT_GRACE = Duration.ofHours(1);
    private static final Duration CHECKOUT_CLOSED_SHIFT_GRACE = Duration.ofHours(2);
    private static final DateTimeFormatter HM_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final AttendanceRepository attendanceRepository;
    private final BranchRepository branchRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final ShiftRepository shiftRepository;
    private final UserRepository userRepository;
    private final PinHasher pinHasher;
    private final PinLockoutService pinLockoutService;
    private final AttendanceMapper attendanceMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AttendanceResultDto checkIn(PinAttendanceRequest request) {
        requireActiveBranch(request.branchId());
        User user = authenticateByPin(request);
        Instant now = Instant.now();
        LocalDate todayVN = DateTimeUtil.todayVN();

        requireAssignedShiftForCheckIn(user, request.branchId(), now, todayVN);

        // Kiểm tra xem nhân viên đã có lượt check-in mở hay chưa
        Optional<Attendance> openAttendance = attendanceRepository
                .findFirstByUserIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(user.getId());
        if (openAttendance.isPresent()) {
            throw new BusinessRuleException(BusinessErrorCode.ATTENDANCE_ALREADY_CHECKED_IN);
        }

        Attendance attendance = Attendance.builder()
                .userId(user.getId())
                .branchId(request.branchId())
                .workDate(todayVN)
                .method("PIN")
                .checkInTime(now)
                .status(AttendanceStatus.CHECKED_IN)
                .notes(request.notes())
                .build();

        Attendance saved = attendanceRepository.save(attendance);
        log.info("Nhân viên '{}' (ID={}) check-in thành công tại chi nhánh {}", user.getFullName(), user.getId(), request.branchId());

        return attendanceMapper.toResultDto(saved, user, "Check-in thành công.");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AttendanceResultDto checkOut(PinAttendanceRequest request) {
        requireActiveBranch(request.branchId());
        User user = authenticateByPin(request);
        Instant now = Instant.now();
        LocalDate todayVN = DateTimeUtil.todayVN();

        Optional<Attendance> openOpt = attendanceRepository
                .findFirstByUserIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(user.getId());

        requireAssignedShiftForCheckOut(user, request.branchId(), openOpt.orElse(null), now, todayVN);

        Attendance attendance = openOpt
                .orElseThrow(() -> new BusinessRuleException(BusinessErrorCode.ATTENDANCE_NO_OPEN));

        long workedMinutes = AttendanceCalculator.calculateWorkedMinutes(attendance.getCheckInTime(), now);

        // BR-07: Khoảng cách tối thiểu giữa lần Check-in và Check-out phải >= 15 phút (kiểm tra qua AttendanceCalculator)
        if (!AttendanceCalculator.isMinIntervalSatisfied(workedMinutes)) {
            throw new BusinessRuleException(BusinessErrorCode.ATTENDANCE_MIN_INTERVAL,
                    "Khoảng cách chấm công tối thiểu giữa lần check-in và check-out phải từ 15 phút trở lên.");
        }

        attendance.setCheckOutTime(now);
        attendance.setWorkedMinutes(workedMinutes);
        attendance.setStatus(AttendanceStatus.COMPLETED);
        if (request.notes() != null && !request.notes().isBlank()) {
            String existingNotes = attendance.getNotes();
            attendance.setNotes(existingNotes != null ? existingNotes + "; " + request.notes() : request.notes());
        }

        Attendance saved = attendanceRepository.save(attendance);
        log.info("Nhân viên '{}' (ID={}) check-out thành công. Tổng phút làm việc: {}", user.getFullName(), user.getId(), workedMinutes);

        return attendanceMapper.toResultDto(saved, user, "Check-out thành công. Tổng thời gian làm việc: " + workedMinutes + " phút.");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AttendanceResultDto cardSwipe(CardAttendanceRequest request) {
        requireActiveBranch(request.branchId());
        List<User> activeUsers = userRepository.findAllByBranchIdAndStatus(request.branchId(), UserStatus.ACTIVE);
        User user = activeUsers.stream()
                .filter(u -> request.cardCode().equalsIgnoreCase(u.getCardCode()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("nhân viên với mã thẻ này"));

        Instant now = Instant.now();
        LocalDate todayVN = DateTimeUtil.todayVN();

        Optional<Attendance> openOpt = attendanceRepository
                .findFirstByUserIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(user.getId());

        if (openOpt.isPresent()) {
            // Đã check-in -> kiểm tra gán ca rồi thực hiện check-out
            Attendance attendance = openOpt.get();
            requireAssignedShiftForCheckOut(user, request.branchId(), attendance, now, todayVN);

            long workedMinutes = AttendanceCalculator.calculateWorkedMinutes(attendance.getCheckInTime(), now);

            // BR-07: Khoảng cách tối thiểu giữa lần Check-in và Check-out phải >= 15 phút
            if (!AttendanceCalculator.isMinIntervalSatisfied(workedMinutes)) {
                throw new BusinessRuleException(BusinessErrorCode.ATTENDANCE_MIN_INTERVAL,
                        "Khoảng cách chấm công tối thiểu giữa lần check-in và check-out phải từ 15 phút trở lên.");
            }

            attendance.setCheckOutTime(now);
            attendance.setWorkedMinutes(workedMinutes);
            attendance.setStatus(AttendanceStatus.COMPLETED);
            attendance.setMethod("RFID");
            if (request.notes() != null && !request.notes().isBlank()) {
                attendance.setNotes(request.notes());
            }
            Attendance saved = attendanceRepository.save(attendance);
            log.info("Quẹt thẻ check-out: user={}, workedMinutes={}", user.getId(), workedMinutes);
            return attendanceMapper.toResultDto(saved, user, "Check-out thành công qua thẻ. Tổng thời gian: " + workedMinutes + " phút.");
        } else {
            // Chưa check-in -> kiểm tra gán ca rồi thực hiện check-in
            requireAssignedShiftForCheckIn(user, request.branchId(), now, todayVN);

            Attendance attendance = Attendance.builder()
                    .userId(user.getId())
                    .branchId(request.branchId())
                    .workDate(todayVN)
                    .method("RFID")
                    .checkInTime(now)
                    .status(AttendanceStatus.CHECKED_IN)
                    .notes(request.notes())
                    .build();
            Attendance saved = attendanceRepository.save(attendance);
            log.info("Quẹt thẻ check-in: user={}, branch={}", user.getId(), request.branchId());
            return attendanceMapper.toResultDto(saved, user, "Check-in thành công qua thẻ.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<KioskEmployeeDto> getKioskEmployees(Integer branchId) {
        return getKioskEmployees(branchId, null, false, false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<KioskEmployeeDto> getKioskEmployees(
            Integer branchId,
            Integer shiftId,
            Boolean onlyAssigned,
            Boolean excludeCashier) {
        requireActiveBranch(branchId);
        List<User> users = userRepository.findAllByBranchIdAndStatus(branchId, UserStatus.ACTIVE);
        List<Attendance> openAttendances = attendanceRepository.findAllByBranchIdAndCheckOutTimeIsNull(branchId);
        Map<Integer, Attendance> openByUser = openAttendances.stream()
                .collect(Collectors.toMap(
                        Attendance::getUserId,
                        Function.identity(),
                        (a, b) -> a.getCheckInTime().isAfter(b.getCheckInTime()) ? a : b));

        Instant now = Instant.now();
        LocalDate todayVN = DateTimeUtil.todayVN();
        LocalDate fromDate = openAttendances.stream()
                .map(a -> DateTimeUtil.toVNDate(a.getCheckInTime()))
                .min(LocalDate::compareTo)
                .map(d -> d.isBefore(todayVN.minusDays(1)) ? d : todayVN.minusDays(1))
                .orElse(todayVN.minusDays(1));

        Map<Integer, List<Shift>> shiftsByUser = shiftAssignmentRepository
                .findAttendanceRelevantByBranch(branchId, fromDate)
                .stream()
                .collect(Collectors.groupingBy(
                        sa -> sa.getId().getEmployeeId(),
                        Collectors.mapping(ShiftAssignment::getShift, Collectors.toList())));

        Map<Integer, Instant> activePinLocks = pinLockoutService.findActiveEmployeeLocks(branchId);

        Shift specifiedShift = shiftId != null ? shiftRepository.findById(shiftId).orElse(null) : null;
        Integer activeCashierId = specifiedShift != null ? specifiedShift.getActiveCashierId() : null;
        Map<Integer, ShiftAssignment> targetSaByEmployee = specifiedShift != null
                ? shiftAssignmentRepository.findByShiftIdWithEmployee(shiftId).stream()
                        .collect(Collectors.toMap(sa -> sa.getId().getEmployeeId(), Function.identity(), (a, b) -> a))
                : null;

        return users.stream()
                .filter(u -> {
                    if (Boolean.TRUE.equals(excludeCashier) && Roles.CASHIER.code().equals(u.roleCode())) {
                        return false;
                    }
                    if (Boolean.TRUE.equals(excludeCashier) && activeCashierId != null && activeCashierId.equals(u.getId())) {
                        return false;
                    }
                    if (targetSaByEmployee != null) {
                        ShiftAssignment targetSa = targetSaByEmployee.get(u.getId());
                        if (targetSa == null) {
                            return false;
                        }
                        if (Boolean.TRUE.equals(excludeCashier)
                                && Roles.CASHIER.code().equalsIgnoreCase(targetSa.getRoleInShift())) {
                            return false;
                        }
                    }
                    return true;
                })
                .map(u -> buildKioskDto(
                        u,
                        openByUser.get(u.getId()),
                        shiftsByUser.getOrDefault(u.getId(), List.of()),
                        activePinLocks.get(u.getId()),
                        now,
                        todayVN))
                .filter(dto -> !Boolean.TRUE.equals(onlyAssigned) || dto.hasAssignedShift())
                .filter(dto -> !Boolean.TRUE.equals(excludeCashier) || !Roles.CASHIER.code().equalsIgnoreCase(dto.role()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AttendanceResultDto getCurrentStatus(Integer employeeId) {
        User user = userRepository.findWithRoleAndBranchById(employeeId)
                .orElseThrow(() -> new NotFoundException("nhân viên"));

        Optional<Attendance> openAttendance = attendanceRepository
                .findFirstByUserIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(employeeId);

        if (openAttendance.isPresent()) {
            return attendanceMapper.toResultDto(openAttendance.get(), user, "Đang trong ca làm việc.");
        }

        // Tìm lượt gần nhất đã hoàn thành
        List<Attendance> list = attendanceRepository.findAllByUserIdOrderByCheckInTimeDesc(employeeId);
        if (!list.isEmpty()) {
            return attendanceMapper.toResultDto(list.get(0), user, "Chưa vào ca làm việc.");
        }

        return attendanceMapper.toNoRecordDto(user, "Chưa có dữ liệu chấm công.");
    }

    private KioskEmployeeDto buildKioskDto(
            User user,
            Attendance openAtt,
            List<Shift> assignedShifts,
            Instant pinLockedUntil,
            Instant now,
            LocalDate todayVN) {
        boolean pinLocked = pinLockedUntil != null && pinLockedUntil.isAfter(now);
        boolean isCheckedIn = openAtt != null;
        Optional<Shift> eligibleShift = isCheckedIn
                ? pickBestShift(assignedShifts, s -> isEligibleForCheckOut(s, openAtt, now, todayVN))
                : pickBestShift(assignedShifts, s -> isEligibleForCheckIn(s, now, todayVN));

        if (eligibleShift.isPresent()) {
            Shift s = eligibleShift.get();
            return attendanceMapper.toKioskDto(
                    user,
                    isCheckedIn,
                    true,
                    formatShiftLabel(s),
                    s.getStatus().name(),
                    pinLocked,
                    pinLocked ? pinLockedUntil : null);
        }

        // Nếu chưa vào ca và chỉ có ca hôm nay đã đóng -> hiển thị nhãn thông tin (nhưng hasAssignedShift = false)
        Optional<Shift> todayClosedShift = assignedShifts.stream()
                .filter(s -> s.getStatus() == ShiftStatus.CLOSED && todayVN.equals(s.getShiftDate()))
                .max(Comparator.comparing(Shift::getPlannedStart));

        String shiftLabel = todayClosedShift.map(s -> formatShiftLabel(s) + " (Đã đóng)").orElse(null);
        String shiftStatus = todayClosedShift.map(s -> s.getStatus().name()).orElse(null);
        return attendanceMapper.toKioskDto(user, isCheckedIn, false, shiftLabel, shiftStatus, pinLocked, pinLocked ? pinLockedUntil : null);
    }

    private void requireAssignedShiftForCheckIn(User user, Integer branchId, Instant now, LocalDate todayVN) {
        List<Shift> assigned = loadAssignedShifts(user.getId(), branchId, todayVN.minusDays(1));
        boolean ok = assigned.stream().anyMatch(s -> isEligibleForCheckIn(s, now, todayVN));
        if (!ok) {
            throw new BusinessRuleException(
                    BusinessErrorCode.SHIFT_ASSIGNMENT_REQUIRED,
                    "Nhân viên " + user.getFullName() + " chưa được phân công ca làm việc phù hợp hôm nay nên không thể check-in.");
        }
    }

    private void requireAssignedShiftForCheckOut(
            User user,
            Integer branchId,
            Attendance openAtt,
            Instant now,
            LocalDate todayVN) {
        LocalDate fromDate = todayVN.minusDays(1);
        if (openAtt != null) {
            LocalDate checkInDateVN = DateTimeUtil.toVNDate(openAtt.getCheckInTime());
            if (checkInDateVN.isBefore(fromDate)) {
                fromDate = checkInDateVN;
            }
        }
        List<Shift> assigned = loadAssignedShifts(user.getId(), branchId, fromDate);
        boolean ok = assigned.stream().anyMatch(s -> isEligibleForCheckOut(s, openAtt, now, todayVN));
        if (!ok) {
            throw new BusinessRuleException(
                    BusinessErrorCode.SHIFT_ASSIGNMENT_REQUIRED,
                    "Nhân viên " + user.getFullName() + " chưa được phân công ca làm việc phù hợp nên không thể check-out.");
        }
    }

    private List<Shift> loadAssignedShifts(Integer employeeId, Integer branchId, LocalDate fromDate) {
        return shiftAssignmentRepository
                .findAttendanceRelevantForEmployee(employeeId, branchId, fromDate)
                .stream()
                .map(ShiftAssignment::getShift)
                .toList();
    }

    private static Optional<Shift> pickBestShift(List<Shift> shifts, java.util.function.Predicate<Shift> filter) {
        return shifts.stream()
                .filter(filter)
                .min(Comparator
                        .comparingInt((Shift s) -> statusPriority(s.getStatus()))
                        .thenComparing(Shift::getPlannedStart));
    }

    private static int statusPriority(ShiftStatus status) {
        if (status == ShiftStatus.OPEN) return 0;
        if (status == ShiftStatus.SCHEDULED) return 1;
        return 2;
    }

    private static boolean isEligibleForCheckIn(Shift s, Instant now, LocalDate todayVN) {
        if (s.getStatus() == ShiftStatus.CLOSED) {
            return false;
        }
        if (s.getStatus() == ShiftStatus.OPEN) {
            return true;
        }
        return todayVN.equals(s.getShiftDate()) || isWithinWindow(s, now, OVERNIGHT_GRACE);
    }

    private static boolean isEligibleForCheckOut(Shift s, Attendance openAtt, Instant now, LocalDate todayVN) {
        if (isEligibleForCheckIn(s, now, todayVN)) {
            return true;
        }
        if (openAtt == null) {
            return false;
        }
        LocalDate checkInDateVN = DateTimeUtil.toVNDate(openAtt.getCheckInTime());
        return checkInDateVN.equals(s.getShiftDate())
                || isWithinWindow(s, openAtt.getCheckInTime(), CHECKOUT_CLOSED_SHIFT_GRACE);
    }

    private static boolean isWithinWindow(Shift s, Instant instant, Duration grace) {
        if (s.getPlannedStart() == null || s.getPlannedEnd() == null) {
            return false;
        }
        Instant start = s.getPlannedStart().minus(grace);
        Instant end = s.getPlannedEnd().plus(grace);
        return !instant.isBefore(start) && !instant.isAfter(end);
    }

    private static String formatShiftLabel(Shift s) {
        String typeLabel = switch (s.getShiftType() != null ? s.getShiftType() : ShiftType.MORNING) {
            case MORNING -> "Ca sáng";
            case EVENING -> "Ca chiều";
            case NIGHT -> "Ca tối";
        };
        if (s.getPlannedStart() == null || s.getPlannedEnd() == null) {
            return typeLabel;
        }
        String startHm = s.getPlannedStart().atZone(DateTimeUtil.VN).toLocalTime().format(HM_FORMATTER);
        String endHm = s.getPlannedEnd().atZone(DateTimeUtil.VN).toLocalTime().format(HM_FORMATTER);
        return typeLabel + " · " + startHm + "–" + endHm;
    }

    private void requireActiveBranch(Integer branchId) {
        if (branchId == null) {
            throw new InvalidFieldValueException("branchId", "Chi nhánh không được để trống.");
        }
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new NotFoundException("chi nhánh"));
        if (branch.getStatus() == BranchStatus.INACTIVE) {
            throw new ForbiddenException(ErrorCode.ACCESS_DENIED, "Chi nhánh đã ngừng hoạt động.");
        }
    }

    private User authenticateByPin(PinAttendanceRequest request) {
        User user;
        if (request.employeeId() != null) {
            user = userRepository.findWithRoleAndBranchById(request.employeeId())
                    .orElseThrow(() -> new NotFoundException("nhân viên"));

            if (user.getStatus() != UserStatus.ACTIVE) {
                throw new BusinessRuleException(BusinessErrorCode.SELF_ACTION_FORBIDDEN, "Tài khoản nhân viên đang bị khóa hoặc ngưng hoạt động.");
            }
            if (user.getBranchId() != null && !user.getBranchId().equals(request.branchId())) {
                throw new BusinessRuleException(BusinessErrorCode.SELF_ACTION_FORBIDDEN, "Nhân viên không thuộc chi nhánh này.");
            }
            pinLockoutService.checkNotLocked(request.branchId(), user.getId(), user.getFullName());

            if (!pinHasher.matches(request.pin(), user.getPinHash())) {
                throw toPinFailureException(pinLockoutService.recordFailure(
                        request.branchId(), user.getId(), user.getFullName(), user.roleCode()));
            }
            pinLockoutService.onSuccess(request.branchId(), user.getId());
            return user;
        }

        // Tra cứu tự động theo chi nhánh + hash PIN
        pinLockoutService.checkNotLocked(request.branchId(), null, null);
        String lookupHash = pinHasher.lookupHash(request.branchId(), request.pin());
        Optional<User> opt = userRepository.findFirstByBranchIdAndPinLookupHashAndStatus(
                request.branchId(), lookupHash, UserStatus.ACTIVE);
        if (opt.isEmpty()) {
            throw toPinFailureException(pinLockoutService.recordFailure(request.branchId(), null, null, null));
        }

        user = opt.get();
        pinLockoutService.checkNotLocked(request.branchId(), user.getId(), user.getFullName());
        if (!pinHasher.matches(request.pin(), user.getPinHash())) {
            throw toPinFailureException(pinLockoutService.recordFailure(
                    request.branchId(), user.getId(), user.getFullName(), user.roleCode()));
        }

        pinLockoutService.onSuccess(request.branchId(), user.getId());
        pinLockoutService.onSuccess(request.branchId(), null);
        return user;
    }

    private static RuntimeException toPinFailureException(PinLockoutService.FailureOutcome outcome) {
        if (outcome.locked()) {
            return new PinLockedException(outcome.retryAfterSeconds(), outcome.message());
        }
        return new InvalidPinException(outcome.message());
    }
}
