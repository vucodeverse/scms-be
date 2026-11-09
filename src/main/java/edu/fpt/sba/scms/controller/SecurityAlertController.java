package edu.fpt.sba.scms.controller;

import edu.fpt.sba.scms.dto.ApiErrorResponse;
import edu.fpt.sba.scms.dto.request.PinLockActionRequest;
import edu.fpt.sba.scms.dto.response.SecurityAlertDto;
import edu.fpt.sba.scms.dto.response.SecurityAlertSummaryDto;
import edu.fpt.sba.scms.security.principal.UserPrincipal;
import edu.fpt.sba.scms.service.SecurityAlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cảnh báo bảo mật &amp; mở khóa PIN sớm cho Quản lý chi nhánh và COO.
 */
@RestController
@RequestMapping("/api/v1/security-alerts")
@RequiredArgsConstructor
@Tag(name = "23. Security Alerts", description = "Cảnh báo khóa tạm PIN Kiosk và mở khóa sớm cho cấp trên")
@SecurityRequirement(name = "bearerAuth")
public class SecurityAlertController {

    private final SecurityAlertService securityAlertService;

    @Operation(summary = "Danh sách cảnh báo bảo mật",
            description = "BM xem cảnh báo chi nhánh mình; COO xem toàn chuỗi hoặc lọc theo branchId.")
    @GetMapping
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER')")
    public SecurityAlertSummaryDto list(
            @RequestParam(required = false) Integer branchId,
            @RequestParam(defaultValue = "false") boolean activeOnly,
            @RequestParam(defaultValue = "20") int limit) {
        return securityAlertService.listAlerts(branchId, activeOnly, limit);
    }

    @Operation(summary = "Xác nhận đã xem cảnh báo")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "403", description = "CROSS_BRANCH_DENIED",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "RESOURCE_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    @PostMapping("/{id}/acknowledge")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER')")
    public SecurityAlertDto acknowledge(
            @PathVariable Integer id,
            @RequestBody(required = false) @Valid PinLockActionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        String note = request != null ? request.note() : null;
        return securityAlertService.acknowledge(id, note, principal);
    }

    @Operation(summary = "Mở khóa PIN sớm cho nhân viên/Kiosk",
            description = "Xóa khóa tạm PIN ngay lập tức để nhân viên có thể chấm công lại và đánh dấu cảnh báo đã xử lý.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "403", description = "CROSS_BRANCH_DENIED",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "RESOURCE_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    @PostMapping("/{id}/unlock-pin")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER')")
    public SecurityAlertDto unlockPin(
            @PathVariable Integer id,
            @RequestBody(required = false) @Valid PinLockActionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        String note = request != null ? request.note() : null;
        return securityAlertService.unlockPin(id, note, principal);
    }
}
