package edu.fpt.sba.scms.controller;

import edu.fpt.sba.scms.dto.ApiErrorResponse;
import edu.fpt.sba.scms.dto.request.CreatePayPeriodRequest;
import edu.fpt.sba.scms.dto.request.UpdatePayrollItemRequest;
import edu.fpt.sba.scms.dto.response.PayPeriodResponse;
import edu.fpt.sba.scms.dto.response.PayrollItemResponse;
import edu.fpt.sba.scms.security.principal.UserPrincipal;
import edu.fpt.sba.scms.service.PayrollService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller phục vụ Quản lý & Tính lương chi nhánh (UC45, BR-53).
 */
@RestController
@RequestMapping("/api/v1/payroll")
@RequiredArgsConstructor
@Tag(name = "20. Payroll", description = "Quản lý kỳ lương, tổng hợp giờ công & phê duyệt bảng lương (UC45)")
@SecurityRequirement(name = "bearerAuth")
public class PayrollController {

    private final PayrollService payrollService;

    @Operation(summary = "Tạo kỳ tính lương mới & tự động tổng hợp giờ công (UC45)",
            description = "Quản lý chi nhánh tạo kỳ lương. Hệ thống quét toàn bộ dữ liệu điểm danh thực tế, tính lương cơ sở theo giờ (BR-53).")
    @PostMapping("/periods")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER')")
    public PayPeriodResponse createPayPeriod(
            @RequestBody @Valid CreatePayPeriodRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return payrollService.createPayPeriod(request, principal);
    }

    @Operation(summary = "Danh sách các kỳ tính lương của chi nhánh (UC45)")
    @GetMapping("/periods")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER')")
    public List<PayPeriodResponse> listPayPeriods(@RequestParam Integer branchId) {
        return payrollService.listPayPeriods(branchId);
    }

    @Operation(summary = "Chi tiết bảng lương nhân viên trong kỳ tính lương (UC45)")
    @GetMapping("/periods/{id}/items")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER')")
    public List<PayrollItemResponse> getPayrollItems(@PathVariable Integer id) {
        return payrollService.getPayrollItems(id);
    }

    @Operation(summary = "Điều chỉnh tiền thưởng / khấu trừ của nhân viên trong kỳ lương (UC45)")
    @PutMapping("/items/{id}")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER')")
    public PayrollItemResponse updatePayrollItem(
            @PathVariable Integer id,
            @RequestBody @Valid UpdatePayrollItemRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return payrollService.updatePayrollItem(id, request, principal);
    }

    @Operation(summary = "Quản lý chi nhánh nộp bảng lương lên Giám đốc vận hành COO duyệt (UC45)")
    @PostMapping("/periods/{id}/submit")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER')")
    public PayPeriodResponse submitPayroll(
            @PathVariable Integer id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return payrollService.submitPayroll(id, principal);
    }

    @Operation(summary = "Giám đốc vận hành (COO) phê duyệt bảng lương (UC45)")
    @PostMapping("/periods/{id}/approve")
    @PreAuthorize("hasAuthority('COO')")
    public PayPeriodResponse approvePayroll(
            @PathVariable Integer id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return payrollService.approvePayroll(id, principal);
    }

    @Operation(summary = "Giám đốc vận hành (COO) từ chối bảng lương yêu cầu điều chỉnh lại (UC45)")
    @PostMapping("/periods/{id}/reject")
    @PreAuthorize("hasAuthority('COO')")
    public PayPeriodResponse rejectPayroll(
            @PathVariable Integer id,
            @RequestParam String reason,
            @AuthenticationPrincipal UserPrincipal principal) {
        return payrollService.rejectPayroll(id, reason, principal);
    }
}

