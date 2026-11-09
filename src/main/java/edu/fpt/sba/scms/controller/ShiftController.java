package edu.fpt.sba.scms.controller;

import edu.fpt.sba.scms.dto.ApiErrorResponse;
import edu.fpt.sba.scms.dto.PageResponse;
import edu.fpt.sba.scms.dto.request.CashAdjustmentRequest;
import edu.fpt.sba.scms.dto.request.CloseShiftRequest;
import edu.fpt.sba.scms.dto.request.CreateScheduleShiftRequest;
import edu.fpt.sba.scms.dto.request.OpenShiftRequest;
import edu.fpt.sba.scms.dto.request.UpdateScheduleShiftRequest;
import edu.fpt.sba.scms.dto.request.query.ShiftListQuery;
import edu.fpt.sba.scms.dto.response.BranchShiftOverviewResponse;
import edu.fpt.sba.scms.dto.response.CashTransactionResponse;
import edu.fpt.sba.scms.dto.response.MessageResponse;
import edu.fpt.sba.scms.dto.response.PreviousShiftSummaryResponse;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

    @Operation(summary = "Danh sách lịch sử ca làm việc & đối soát két",
            description = "Tra cứu các phiên ca theo chi nhánh (COO có thể xem tất cả chi nhánh), trạng thái và khoảng ngày (phân trang).")
    @GetMapping
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER', 'CASHIER')")
    public PageResponse<ShiftResponse> listShifts(@ModelAttribute @Valid ShiftListQuery query) {
        return PageResponse.of(shiftService.listShifts(query));
    }

    @Operation(summary = "Mở ca bán hàng & khai báo tiền đầu ca (UC24)",
            description = "Thu ngân / Quản lý mở ca làm việc tại chi nhánh, kiểm tra kết ca trước và khai báo tiền đầu két. Mỗi chi nhánh chỉ được mở 1 ca tại 1 thời điểm (BR-36).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Mở ca thành công"),
            @ApiResponse(responseCode = "422", description = "Chi nhánh đang có ca mở chưa đóng hoặc lệch kết ca cũ thiếu ghi chú giải trình",
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
            description = "Chỉ Quản lý chi nhánh hoặc COO có quyền nạp thêm tiền mặt vào két. Bắt buộc có lý do (BR-37).")
    @PostMapping("/{id}/cash-in")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER')")
    public CashTransactionResponse cashIn(
            @PathVariable Integer id,
            @RequestBody @Valid CashAdjustmentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return shiftService.cashIn(id, request, principal);
    }

    @Operation(summary = "Rút bớt tiền khỏi két - Cash Out (UC26)",
            description = "Chỉ Quản lý chi nhánh hoặc COO có quyền rút tiền mặt khỏi két. Bắt buộc có lý do và kiểm tra đủ số dư (BR-37).")
    @PostMapping("/{id}/cash-out")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER')")
    public CashTransactionResponse cashOut(
            @PathVariable Integer id,
            @RequestBody @Valid CashAdjustmentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return shiftService.cashOut(id, request, principal);
    }

    @Operation(summary = "Đóng ca & kiểm đếm tiền mặt két - Close Shift (UC27)",
            description = "Thu ngân / Quản lý chốt ca, kiểm đếm số tiền mặt thực tế. Hệ thống đối soát và tính chênh lệch; nếu lệch bắt buộc giải trình (BR-38, BR-39).")
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

    @Operation(summary = "Thông tin ca đóng gần nhất để đối soát kết đầu ca mới",
            description = "Lấy số tiền kết ca trước đó (countedCash / expectedCash) của chi nhánh để đối chiếu khi mở ca tiếp theo.")
    @GetMapping("/previous-closed")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER', 'CASHIER')")
    public PreviousShiftSummaryResponse getPreviousClosedShift(@RequestParam Integer branchId) {
        return shiftService.getPreviousClosedShift(branchId);
    }

    @Operation(summary = "Tổng quan ca & két tiền tất cả chi nhánh (màn COO)",
            description = "COO theo dõi ca đang mở, số dư két hiện tại và kết ca trước đó của toàn bộ chi nhánh trong chuỗi.")
    @GetMapping("/branches-overview")
    @PreAuthorize("hasAuthority('COO')")
    public List<BranchShiftOverviewResponse> getBranchesOverview() {
        return shiftService.getBranchesOverview();
    }

    @Operation(summary = "Xem chi tiết tổng kết một ca làm việc theo ID",
            description = "Xem lại số dư đầu ca, doanh thu và toàn bộ giao dịch két của một ca bất kỳ.")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER', 'CASHIER')")
    public ShiftSummaryResponse getShiftSummary(@PathVariable Integer id) {
        return shiftService.getShiftSummary(id);
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

    @Operation(summary = "Cập nhật lịch ca & phân công nhân viên (UC08)",
            description = "Chỉnh sửa khung giờ (khi chưa đóng ca) và cập nhật danh sách nhân viên trực ca; tự kiểm tra trùng lịch (BR-15).")
    @PutMapping("/schedules/{id}")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER')")
    public ShiftScheduleResponse updateSchedule(
            @PathVariable Integer id,
            @RequestBody @Valid UpdateScheduleShiftRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return shiftService.updateSchedule(id, request, principal);
    }

    @Operation(summary = "Xóa lịch ca làm việc chưa mở (UC08)",
            description = "Chỉ cho phép xóa ca ở trạng thái SCHEDULED (chưa mở ca / chưa có giao dịch két).")
    @DeleteMapping("/schedules/{id}")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER')")
    public MessageResponse deleteSchedule(
            @PathVariable Integer id,
            @AuthenticationPrincipal UserPrincipal principal) {
        shiftService.deleteSchedule(id, principal);
        return new MessageResponse("Đã xóa lịch ca làm việc.");
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
