package edu.fpt.sba.scms.controller;

import edu.fpt.sba.scms.dto.ApiErrorResponse;
import edu.fpt.sba.scms.dto.PageResponse;
import edu.fpt.sba.scms.dto.request.AcceptShiftHandoverRequest;
import edu.fpt.sba.scms.dto.request.ApproveEarlyCloseRequest;
import edu.fpt.sba.scms.dto.request.ApproveManagerCloseRequest;
import edu.fpt.sba.scms.dto.request.CancelShiftHandoverRequest;
import edu.fpt.sba.scms.dto.request.CashAdjustmentRequest;
import edu.fpt.sba.scms.dto.request.CloseShiftRequest;
import edu.fpt.sba.scms.dto.request.CreateScheduleShiftRequest;
import edu.fpt.sba.scms.dto.request.InitiateShiftHandoverRequest;
import edu.fpt.sba.scms.dto.request.OpenShiftRequest;
import edu.fpt.sba.scms.dto.request.PostCloseCashOutRequest;
import edu.fpt.sba.scms.dto.request.PreOpenCashInRequest;
import edu.fpt.sba.scms.dto.request.RejectEarlyCloseRequest;
import edu.fpt.sba.scms.dto.request.RejectManagerCloseRequest;
import edu.fpt.sba.scms.dto.request.UpdateScheduleShiftRequest;
import edu.fpt.sba.scms.dto.request.query.ShiftListQuery;
import edu.fpt.sba.scms.dto.response.BranchShiftOverviewResponse;
import edu.fpt.sba.scms.dto.response.CashTransactionResponse;
import edu.fpt.sba.scms.dto.response.MessageResponse;
import edu.fpt.sba.scms.dto.response.PreOpenCashAdjustmentResponse;
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
            description = "Tra cứu các phiên ca theo chi nhánh (COO có thể xem tất cả chi nhánh), trạng thái và khoảng ngày (phân trang). Thu ngân chỉ xem được ca đã đóng gần nhất của chi nhánh mình.")
    @GetMapping
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER', 'CASHIER')")
    public PageResponse<ShiftResponse> listShifts(
            @ModelAttribute @Valid ShiftListQuery query,
            @AuthenticationPrincipal UserPrincipal principal) {
        return PageResponse.of(shiftService.listShifts(query, principal));
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

    @Operation(summary = "Khởi tạo bàn giao két giữa ca (Mid-shift Handover)",
            description = "Thu ngân đang trực (hoặc Quản lý chi nhánh) kiểm đếm két giữa ca để bàn giao cho người khác mà chưa đóng ca; tự check-out người giao và tạm khóa POS cho đến khi người nhận xác nhận.")
    @PostMapping("/{id}/handover/initiate")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER', 'CASHIER')")
    public ShiftResponse initiateHandover(
            @PathVariable Integer id,
            @RequestBody @Valid InitiateShiftHandoverRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return shiftService.initiateHandover(id, request, principal);
    }

    @Operation(summary = "Xác nhận kiểm két nhận ca giữa ca",
            description = "Thu ngân mới (nhập PIN) hoặc Quản lý chi nhánh đứng quầy đếm lại tiền két để tiếp nhận phiên ca đang mở, tự động check-in thu ngân nhận và mở khóa POS.")
    @PostMapping("/{id}/handover/accept")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER', 'CASHIER')")
    public ShiftResponse acceptHandover(
            @PathVariable Integer id,
            @RequestBody @Valid AcceptShiftHandoverRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return shiftService.acceptHandover(id, request, principal);
    }

    @Operation(summary = "Hủy yêu cầu bàn giao két giữa ca đang chờ",
            description = "Quản lý chi nhánh, COO hoặc người khởi tạo hủy phiếu bàn giao đang chờ nhận để mở lại hoạt động ca.")
    @PostMapping("/{id}/handover/cancel")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER', 'CASHIER')")
    public ShiftResponse cancelHandover(
            @PathVariable Integer id,
            @RequestBody(required = false) @Valid CancelShiftHandoverRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return shiftService.cancelHandover(id, request, principal);
    }

    @Operation(summary = "Đóng ca & kiểm đếm tiền mặt két - Close Shift (UC27)",
            description = "Thu ngân / Quản lý chốt ca, kiểm đếm số tiền mặt thực tế. Nếu Thu ngân đóng sớm hơn 10 phút so với giờ dự kiến kết thúc sẽ gửi yêu cầu chờ Quản lý xác nhận; đúng giờ hoặc Quản lý đóng trực tiếp sẽ chốt đóng ca ngay.")
    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER', 'CASHIER')")
    public ShiftResponse closeShift(
            @PathVariable Integer id,
            @RequestBody @Valid CloseShiftRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return shiftService.closeShift(id, request, principal);
    }

    @Operation(summary = "Quản lý chi nhánh / COO xác nhận duyệt đóng ca sớm",
            description = "Xác nhận yêu cầu đóng ca sớm từ Thu ngân, chốt số liệu đóng ca, tự động Check-out Thu ngân và hoàn tất thông báo chờ duyệt.")
    @PostMapping("/{id}/early-close/approve")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER')")
    public ShiftResponse approveEarlyClose(
            @PathVariable Integer id,
            @RequestBody(required = false) @Valid ApproveEarlyCloseRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return shiftService.approveEarlyClose(id, request, principal);
    }

    @Operation(summary = "Từ chối hoặc Thu ngân tự hủy yêu cầu đóng ca sớm",
            description = "Quản lý chi nhánh / COO từ chối yêu cầu đóng ca sớm (hoặc chính Thu ngân gửi yêu cầu tự hủy) để mở khóa lại POS tiếp tục ca làm việc.")
    @PostMapping("/{id}/early-close/reject")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER', 'CASHIER')")
    public ShiftResponse rejectEarlyClose(
            @PathVariable Integer id,
            @RequestBody(required = false) @Valid RejectEarlyCloseRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return shiftService.rejectEarlyClose(id, request, principal);
    }

    @Operation(summary = "Quản lý xác nhận nhận két cuối ca & tùy chọn rút tiền cất két Quản lý",
            description = "Quản lý chi nhánh / COO kiểm nhận lại tiền két Thu ngân bàn giao cuối ca, có thể rút bớt một khoản về két Quản lý và chốt đóng ca với số dư lưu két còn lại.")
    @PostMapping("/{id}/manager-close/approve")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER')")
    public ShiftResponse approveManagerClose(
            @PathVariable Integer id,
            @RequestBody(required = false) @Valid ApproveManagerCloseRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return shiftService.approveManagerClose(id, request, principal);
    }

    @Operation(summary = "Quản lý trả lại ca đang chờ nhận két để mở lại quầy POS",
            description = "Hủy phiếu bàn giao cuối ca đang chờ Quản lý nhận để mở lại hoạt động POS của ca hiện tại.")
    @PostMapping("/{id}/manager-close/reject")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER')")
    public ShiftResponse rejectManagerClose(
            @PathVariable Integer id,
            @RequestBody(required = false) @Valid RejectManagerCloseRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return shiftService.rejectManagerClose(id, request, principal);
    }

    @Operation(summary = "Quản lý nạp thêm tiền mặt vào két đầu ngày (khi chưa mở ca)",
            description = "Quản lý chi nhánh / COO bổ sung tiền mặt/tiền lẻ vào két khi chưa có ca mở. Khoản này được cộng vào tiền dự kiến mở ca và tự ghi CASH_IN vào ca mới khi mở.")
    @PostMapping("/pre-open-cash-in")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER')")
    public PreOpenCashAdjustmentResponse preOpenCashIn(
            @RequestBody @Valid PreOpenCashInRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return shiftService.preOpenCashIn(request, principal);
    }

    @Operation(summary = "Hủy khoản nạp tiền đầu ngày chưa gắn vào ca",
            description = "Cho phép Quản lý chi nhánh / COO hủy khoản nạp đầu ngày nếu nhập nhầm trước khi ca tiếp theo được mở.")
    @DeleteMapping("/pre-open-cash-in/{adjustmentId}")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER')")
    public PreOpenCashAdjustmentResponse cancelPreOpenCashIn(
            @PathVariable Integer adjustmentId,
            @RequestParam(required = false) String reason,
            @AuthenticationPrincipal UserPrincipal principal) {
        return shiftService.cancelPreOpenCashIn(adjustmentId, reason, principal);
    }

    @Operation(summary = "Quản lý rút tiền khỏi két sau khi ca gần nhất đã đóng",
            description = "Khi không có ca mở, Quản lý chi nhánh / COO có thể rút tiền mặt cất két Quản lý từ số dư lưu két của ca đã đóng gần nhất.")
    @PostMapping("/previous-closed/cash-out")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER')")
    public CashTransactionResponse postCloseCashOut(
            @RequestBody @Valid PostCloseCashOutRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return shiftService.postCloseCashOut(request, principal);
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
            description = "Xem lại số dư đầu ca, doanh thu và toàn bộ giao dịch két của một ca. Thu ngân chỉ xem được chi tiết ca hiện tại hoặc ca đã đóng gần nhất.")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER', 'CASHIER')")
    public ShiftSummaryResponse getShiftSummary(
            @PathVariable Integer id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return shiftService.getShiftSummary(id, principal);
    }

    @Operation(summary = "Danh sách ca Đã lên lịch quanh hôm nay có thể mở ca",
            description = "Cung cấp các ca ở trạng thái SCHEDULED quanh ngày hiện tại để Thu ngân / Quản lý chọn khi Kiểm két & Mở ca mà không lộ lịch phân công nhân sự của toàn chi nhánh.")
    @GetMapping("/open-candidates")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER', 'CASHIER')")
    public List<ShiftScheduleResponse> getOpenCandidates(
            @RequestParam Integer branchId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return shiftService.getOpenableScheduledShifts(branchId, principal);
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
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER')")
    public List<ShiftScheduleResponse> getSchedules(
            @RequestParam Integer branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return shiftService.getSchedules(branchId, startDate, endDate);
    }
}
