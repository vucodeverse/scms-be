package edu.fpt.sba.scms.controller;

import edu.fpt.sba.scms.dto.ApiErrorResponse;
import edu.fpt.sba.scms.dto.request.CardAttendanceRequest;
import edu.fpt.sba.scms.dto.request.PinAttendanceRequest;
import edu.fpt.sba.scms.dto.response.AttendanceResultDto;
import edu.fpt.sba.scms.dto.response.KioskEmployeeDto;
import edu.fpt.sba.scms.service.AttendanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller phục vụ chấm công Kiosk (UC03: Check-in, UC04: Check-out).
 */
@RestController
@RequestMapping("/api/v1/attendance")
@RequiredArgsConstructor
@Tag(name = "03. Attendance", description = "Chấm công vào ca / ra ca qua Kiosk (UC03, UC04)")
public class AttendanceController {

    private final AttendanceService attendanceService;

    @Operation(summary = "Danh sách nhân viên Kiosk",
            description = "Lấy danh sách nhân viên đang hoạt động của chi nhánh để hiển thị trên màn hình chọn Kiosk.")
    @GetMapping("/kiosk/employees")
    public List<KioskEmployeeDto> kioskEmployees(@RequestParam Integer branchId) {
        return attendanceService.getKioskEmployees(branchId);
    }

    @Operation(summary = "Check-in vào ca bằng PIN (UC03)",
            description = "Xác thực bằng mã PIN 4-6 số. Ghi nhận thời gian bắt đầu ca và tạo bản ghi chấm công.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Check-in thành công"),
            @ApiResponse(responseCode = "401", description = "INVALID_PIN: Mã PIN không đúng",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "ATTENDANCE_ALREADY_CHECKED_IN: Đã check-in trước đó",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    @PostMapping("/check-in")
    @ResponseStatus(HttpStatus.CREATED)
    public AttendanceResultDto checkIn(@RequestBody @Valid PinAttendanceRequest request) {
        return attendanceService.checkIn(request);
    }

    @Operation(summary = "Check-out kết thúc ca bằng PIN (UC04)",
            description = "Xác thực bằng mã PIN. Ghi nhận thời gian kết thúc ca và tính tổng số phút làm việc.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Check-out thành công"),
            @ApiResponse(responseCode = "401", description = "INVALID_PIN: Mã PIN không đúng",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "ATTENDANCE_NO_OPEN: Chưa có lượt check-in mở",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    @PostMapping("/check-out")
    public AttendanceResultDto checkOut(@RequestBody @Valid PinAttendanceRequest request) {
        return attendanceService.checkOut(request);
    }

    @Operation(summary = "Chấm công qua quẹt thẻ RFID",
            description = "Tự động nhận diện: nếu đang mở ca thì check-out, nếu chưa mở ca thì check-in.")
    @PostMapping("/card-swipe")
    public AttendanceResultDto cardSwipe(@RequestBody @Valid CardAttendanceRequest request) {
        return attendanceService.cardSwipe(request);
    }

    @Operation(summary = "Trạng thái chấm công hiện tại của nhân viên")
    @GetMapping("/status/{employeeId}")
    public AttendanceResultDto currentStatus(@PathVariable Integer employeeId) {
        return attendanceService.getCurrentStatus(employeeId);
    }
}

