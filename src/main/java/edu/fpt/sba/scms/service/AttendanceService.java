package edu.fpt.sba.scms.service;

import edu.fpt.sba.scms.dto.request.CardAttendanceRequest;
import edu.fpt.sba.scms.dto.request.PinAttendanceRequest;
import edu.fpt.sba.scms.dto.response.AttendanceResultDto;
import edu.fpt.sba.scms.dto.response.KioskEmployeeDto;

import java.util.List;

/**
 * Nghiệp vụ chấm công Kiosk Check-in (UC03) & Check-out (UC04).
 */
public interface AttendanceService {

    /** Check-in bằng mã PIN (UC03). */
    AttendanceResultDto checkIn(PinAttendanceRequest request);

    /** Check-out bằng mã PIN (UC04). */
    AttendanceResultDto checkOut(PinAttendanceRequest request);

    /** Quẹt thẻ RFID: nếu chưa check-in thì check-in, nếu đang trong ca thì check-out. */
    AttendanceResultDto cardSwipe(CardAttendanceRequest request);

    /** Lấy danh sách nhân viên chi nhánh hiển thị trên màn hình chọn Kiosk. */
    List<KioskEmployeeDto> getKioskEmployees(Integer branchId);

    /** Xem trạng thái chấm công hiện tại của nhân viên. */
    AttendanceResultDto getCurrentStatus(Integer employeeId);
}

