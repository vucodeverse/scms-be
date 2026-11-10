package edu.fpt.sba.scms.mapper;

import edu.fpt.sba.scms.dto.response.AttendanceResultDto;
import edu.fpt.sba.scms.dto.response.KioskEmployeeDto;
import edu.fpt.sba.scms.model.Attendance;
import edu.fpt.sba.scms.model.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper chuyển đổi Entity ↔ DTO cho chấm công Kiosk (UC03, UC04).
 */
@Mapper(componentModel = "spring")
public interface AttendanceMapper {

    @Mapping(target = "attendanceId", source = "attendance.id")
    @Mapping(target = "employeeId", source = "user.id")
    @Mapping(target = "employeeName", expression = "java(user.getFullName())")
    @Mapping(target = "role", expression = "java(user.roleCode())")
    @Mapping(target = "branchId", source = "attendance.branchId")
    @Mapping(target = "shiftId", source = "attendance.shiftId")
    @Mapping(target = "shiftLabel", source = "shiftLabel")
    @Mapping(target = "checkInTime", source = "attendance.checkInTime")
    @Mapping(target = "checkOutTime", source = "attendance.checkOutTime")
    @Mapping(target = "workedMinutes", source = "attendance.workedMinutes")
    @Mapping(target = "status", source = "attendance.status")
    @Mapping(target = "message", source = "message")
    @Mapping(target = "flaggedForgotCheckout", source = "attendance.flaggedForgotCheckout")
    AttendanceResultDto toResultDto(Attendance attendance, User user, String shiftLabel, String message);

    default AttendanceResultDto toResultDto(Attendance attendance, User user, String message) {
        return toResultDto(attendance, user, null, message);
    }

    @Mapping(target = "attendanceId", ignore = true)
    @Mapping(target = "employeeId", source = "user.id")
    @Mapping(target = "employeeName", expression = "java(user.getFullName())")
    @Mapping(target = "role", expression = "java(user.roleCode())")
    @Mapping(target = "branchId", source = "user.branchId")
    @Mapping(target = "shiftId", ignore = true)
    @Mapping(target = "shiftLabel", ignore = true)
    @Mapping(target = "checkInTime", ignore = true)
    @Mapping(target = "checkOutTime", ignore = true)
    @Mapping(target = "workedMinutes", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "message", source = "message")
    @Mapping(target = "flaggedForgotCheckout", ignore = true)
    AttendanceResultDto toNoRecordDto(User user, String message);

    @Mapping(target = "employeeId", source = "user.id")
    @Mapping(target = "fullName", expression = "java(user.getFullName())")
    @Mapping(target = "role", expression = "java(user.roleCode())")
    @Mapping(target = "isCheckedIn", source = "isCheckedIn")
    @Mapping(target = "hasAssignedShift", source = "hasAssignedShift")
    @Mapping(target = "shiftId", source = "shiftId")
    @Mapping(target = "shiftLabel", source = "shiftLabel")
    @Mapping(target = "shiftStatus", source = "shiftStatus")
    @Mapping(target = "pinLocked", source = "pinLocked")
    @Mapping(target = "pinLockedUntil", source = "pinLockedUntil")
    @Mapping(target = "isPreviousShiftUnclosed", source = "isPreviousShiftUnclosed")
    @Mapping(target = "flaggedForgotCheckout", source = "flaggedForgotCheckout")
    KioskEmployeeDto toKioskDto(
            User user,
            boolean isCheckedIn,
            boolean hasAssignedShift,
            Integer shiftId,
            String shiftLabel,
            String shiftStatus,
            boolean pinLocked,
            java.time.Instant pinLockedUntil,
            boolean isPreviousShiftUnclosed,
            boolean flaggedForgotCheckout);
}
