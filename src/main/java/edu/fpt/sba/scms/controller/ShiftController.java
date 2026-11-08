package edu.fpt.sba.scms.controller;

import edu.fpt.sba.scms.dto.ApiErrorResponse;
import edu.fpt.sba.scms.dto.request.CashAdjustmentRequest;
import edu.fpt.sba.scms.dto.request.CloseShiftRequest;
import edu.fpt.sba.scms.dto.request.CreateScheduleShiftRequest;
import edu.fpt.sba.scms.dto.request.OpenShiftRequest;
import edu.fpt.sba.scms.dto.response.CashTransactionResponse;
import edu.fpt.sba.scms.dto.response.ShiftResponse;
import edu.fpt.sba.scms.dto.response.ShiftScheduleResponse;
import edu.fpt.sba.scms.dto.response.ShiftSummaryResponse;
import edu.fpt.sba.scms.security.principal.UserPrincipal;
import edu.fpt.sba.scms.service.ShiftService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Controller phục vụ Quản lý ca làm việc & Vận hành két tiền POS (UC24, UC25, UC26, UC27, UC08).
 */
@RestController
@RequestMapping("/api/v1/shifts")
@RequiredArgsConstructor
@Tag(name = "18. Shifts & Cash Drawer", description = "Quản lý ca làm việc, lịch phân ca & ngăn kéo tiền mặt (UC24-27, UC08)")
@SecurityRequirement(name = "bearerAuth")
public class ShiftController {

    private final ShiftService shiftService;

    @Operation(summary = "Mở ca bán hàng & khai báo tiền đầu ca (UC24)",
            description = "Thu ngân mở ca làm việc tại chi nhánh, khai báo số tiền mặt ban đầu trong két. Mỗi chi nhánh chỉ được mở 1 ca tại 1 thời điểm (BR-36).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Mở ca thành công"),
            @ApiResponse(responseCode = "422", description = "Chi nhánh đang có ca mở chưa đóng",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    @PostMapping("/open")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER', 'CASHIER', 'STAFF')")
    public ShiftResponse openShift(
            @RequestBody @Valid OpenShiftRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return shiftService.openShift(request, principal);
    }

    @Operation(summary = "Nạp thêm tiền lẻ vào két trong ca - Cash In (UC25)",
            description = "Nạp thêm tiền lẻ vào két để thối. Bắt buộc có lý do và xác thực mã PIN của Quản lý chi nhánh (BR-32, BR-37).")
    @PostMapping("/{id}/cash-in")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER', 'CASHIER')")
    public CashTransactionResponse cashIn(
            @PathVariable Integer id,
            @RequestBody @Valid CashAdjustmentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return shiftService.cashIn(id, request, principal);
    }

    @Operation(summary = "Rút bớt tiền khỏi két - Cash Out (UC26)",
            description = "Rút bớt tiền mặt từ két nộp quỹ chi nhánh. Bắt buộc có lý do, kiểm tra đủ tiền và xác thực mã PIN Quản lý (BR-32, BR-37).")
    @PostMapping("/{id}/cash-out")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER', 'CASHIER')")
    public CashTransactionResponse cashOut(
            @PathVariable Integer id,
            @RequestBody @Valid CashAdjustmentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return shiftService.cashOut(id, request, principal);
    }

    @Operation(summary = "Đóng ca & kiểm đếm tiền mặt két - Close Shift (UC27)",
            description = "Thu ngân chốt ca, kiểm đếm số tiền mặt thực tế. Hệ thống đối soát và tính chênh lệch; nếu lệch bắt buộc giải trình (BR-38, BR-39).")
    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER', 'CASHIER')")
    public ShiftResponse closeShift(
            @PathVariable Integer id,
            @RequestBody @Valid CloseShiftRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return shiftService.closeShift(id, request, principal);
    }

    @Operation(summary = "Xem tổng kết ca & số dư két hiện tại",
            description = "Thu ngân / Quản lý theo dõi số dư tiền mặt lý thuyết, doanh thu trong ca, lịch sử nạp rút tiền.")
    @GetMapping("/current")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER', 'CASHIER', 'STAFF')")
    public ShiftSummaryResponse getCurrentShiftSummary(@RequestParam Integer branchId) {
        return shiftService.getCurrentShiftSummary(branchId);
    }

    @Operation(summary = "Tạo lịch phân ca làm việc (UC08)",
            description = "Quản lý chi nhánh xếp lịch ca làm việc và phân công nhân viên. Tự động kiểm tra cảnh báo trùng lịch ca (BR-15).")
    @PostMapping("/schedules")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER')")
    public ShiftScheduleResponse createSchedule(
            @RequestBody @Valid CreateScheduleShiftRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return shiftService.createSchedule(request, principal);
    }

    @Operation(summary = "Tra cứu lịch ca làm việc theo khoảng thời gian (UC08)")
    @GetMapping("/schedules")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER', 'CASHIER', 'STAFF')")
    public List<ShiftScheduleResponse> getSchedules(
            @RequestParam Integer branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return shiftService.getSchedules(branchId, startDate, endDate);
    }
}

