package edu.fpt.sba.scms.service.impl;

import edu.fpt.sba.scms.common.enums.AttendanceStatus;
import edu.fpt.sba.scms.common.enums.BusinessErrorCode;
import edu.fpt.sba.scms.common.enums.ErrorCode;
import edu.fpt.sba.scms.common.enums.UserStatus;
import edu.fpt.sba.scms.common.exception.BusinessRuleException;
import edu.fpt.sba.scms.common.exception.InvalidPinException;
import edu.fpt.sba.scms.common.exception.NotFoundException;
import edu.fpt.sba.scms.dto.request.CardAttendanceRequest;
import edu.fpt.sba.scms.dto.request.PinAttendanceRequest;
import edu.fpt.sba.scms.dto.response.AttendanceResultDto;
import edu.fpt.sba.scms.dto.response.KioskEmployeeDto;
import edu.fpt.sba.scms.model.Attendance;
import edu.fpt.sba.scms.model.User;
import edu.fpt.sba.scms.repository.AttendanceRepository;
import edu.fpt.sba.scms.repository.UserRepository;
import edu.fpt.sba.scms.service.AttendanceService;
import edu.fpt.sba.scms.service.support.PinHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceServiceImpl implements AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;
    private final PinHasher pinHasher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AttendanceResultDto checkIn(PinAttendanceRequest request) {
        User user = authenticateByPin(request);

        // Kiểm tra xem nhân viên đã có lượt check-in mở hay chưa
        Optional<Attendance> openAttendance = attendanceRepository
                .findFirstByUserIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(user.getId());
        if (openAttendance.isPresent()) {
            throw new BusinessRuleException(BusinessErrorCode.ATTENDANCE_ALREADY_CHECKED_IN);
        }

        Instant now = Instant.now();
        Attendance attendance = Attendance.builder()
                .userId(user.getId())
                .branchId(request.branchId())
                .workDate(LocalDate.now())
                .method("PIN")
                .checkInTime(now)
                .status(AttendanceStatus.CHECKED_IN)
                .notes(request.notes())
                .build();

        Attendance saved = attendanceRepository.save(attendance);
        log.info("Nhân viên '{}' (ID={}) check-in thành công tại chi nhánh {}", user.getFullName(), user.getId(), request.branchId());

        return toDto(saved, user, "Check-in thành công.");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AttendanceResultDto checkOut(PinAttendanceRequest request) {
        User user = authenticateByPin(request);

        // Tìm lượt check-in mở
        Attendance attendance = attendanceRepository
                .findFirstByUserIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(user.getId())
                .orElseThrow(() -> new BusinessRuleException(BusinessErrorCode.ATTENDANCE_NO_OPEN));

        Instant now = Instant.now();
        long workedMinutes = Math.max(0, Duration.between(attendance.getCheckInTime(), now).toMinutes());

        // BR-07: Khoảng cách tối thiểu giữa lần Check-in và Check-out phải >= 15 phút
        if (workedMinutes < 15) {
            throw new BusinessRuleException(BusinessErrorCode.ATTENDANCE_MIN_INTERVAL,
                    "Khoảng cách chấm công tối thiểu giữa lần check-in và check-out phải từ 15 phút trở lên (BR-07).");
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

        return toDto(saved, user, "Check-out thành công. Tổng thời gian làm việc: " + workedMinutes + " phút.");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AttendanceResultDto cardSwipe(CardAttendanceRequest request) {
        List<User> activeUsers = userRepository.findAllByBranchIdAndStatus(request.branchId(), UserStatus.ACTIVE);
        User user = activeUsers.stream()
                .filter(u -> request.cardCode().equalsIgnoreCase(u.getCardCode()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("nhân viên với mã thẻ này"));

        Optional<Attendance> openOpt = attendanceRepository
                .findFirstByUserIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(user.getId());

        if (openOpt.isPresent()) {
            // Đã check-in -> thực hiện check-out
            Attendance attendance = openOpt.get();
            Instant now = Instant.now();
            long workedMinutes = Math.max(0, Duration.between(attendance.getCheckInTime(), now).toMinutes());

            // BR-07: Khoảng cách tối thiểu giữa lần Check-in và Check-out phải >= 15 phút
            if (workedMinutes < 15) {
                throw new BusinessRuleException(BusinessErrorCode.ATTENDANCE_MIN_INTERVAL,
                        "Khoảng cách chấm công tối thiểu giữa lần check-in và check-out phải từ 15 phút trở lên (BR-07).");
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
            return toDto(saved, user, "Check-out thành công qua thẻ. Tổng thời gian: " + workedMinutes + " phút.");
        } else {
            // Chưa check-in -> thực hiện check-in
            Instant now = Instant.now();
            Attendance attendance = Attendance.builder()
                    .userId(user.getId())
                    .branchId(request.branchId())
                    .workDate(LocalDate.now())
                    .method("RFID")
                    .checkInTime(now)
                    .status(AttendanceStatus.CHECKED_IN)
                    .notes(request.notes())
                    .build();
            Attendance saved = attendanceRepository.save(attendance);
            log.info("Quẹt thẻ check-in: user={}, branch={}", user.getId(), request.branchId());
            return toDto(saved, user, "Check-in thành công qua thẻ.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<KioskEmployeeDto> getKioskEmployees(Integer branchId) {
        List<User> users = userRepository.findAllByBranchIdAndStatus(branchId, UserStatus.ACTIVE);
        List<Attendance> openAttendances = attendanceRepository.findAllByBranchIdAndCheckOutTimeIsNull(branchId);
        Set<Integer> checkedInIds = openAttendances.stream()
                .map(Attendance::getUserId)
                .collect(Collectors.toSet());

        return users.stream()
                .map(u -> new KioskEmployeeDto(
                        u.getId(),
                        u.getFullName(),
                        u.roleCode(),
                        checkedInIds.contains(u.getId())))
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
            return toDto(openAttendance.get(), user, "Đang trong ca làm việc.");
        }

        // Tìm lượt gần nhất đã hoàn thành
        List<Attendance> list = attendanceRepository.findAllByUserIdOrderByCheckInTimeDesc(employeeId);
        if (!list.isEmpty()) {
            return toDto(list.get(0), user, "Chưa vào ca làm việc.");
        }

        return new AttendanceResultDto(
                null,
                user.getId(),
                user.getFullName(),
                user.roleCode(),
                user.getBranchId(),
                null,
                null,
                null,
                null,
                "Chưa có dữ liệu chấm công."
        );
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
        } else {
            // Tra cứu tự động theo chi nhánh + hash PIN
            String lookupHash = pinHasher.lookupHash(request.branchId(), request.pin());
            user = userRepository.findFirstByBranchIdAndPinLookupHashAndStatus(request.branchId(), lookupHash, UserStatus.ACTIVE)
                    .orElseThrow(InvalidPinException::new);
        }

        // Xác thực lại với BCrypt PIN hash
        if (!pinHasher.matches(request.pin(), user.getPinHash())) {
            throw new InvalidPinException();
        }

        return user;
    }

    private AttendanceResultDto toDto(Attendance attendance, User user, String message) {
        return new AttendanceResultDto(
                attendance.getId(),
                user.getId(),
                user.getFullName(),
                user.roleCode(),
                attendance.getBranchId(),
                attendance.getCheckInTime(),
                attendance.getCheckOutTime(),
                attendance.getWorkedMinutes(),
                attendance.getStatus(),
                message
        );
    }
}
