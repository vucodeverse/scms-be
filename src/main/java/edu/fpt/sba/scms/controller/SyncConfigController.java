package edu.fpt.sba.scms.controller;

import edu.fpt.sba.scms.dto.ApiErrorResponse;
import edu.fpt.sba.scms.dto.request.SyncConfigRequest;
import edu.fpt.sba.scms.dto.response.SyncConfigResponse;
import edu.fpt.sba.scms.security.principal.UserPrincipal;
import edu.fpt.sba.scms.service.SyncConfigService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller phục vụ Quản trị đồng bộ cấu hình toàn chuỗi (UC14).
 */
@RestController
@RequestMapping("/api/v1/chain-sync")
@RequiredArgsConstructor
@Tag(name = "21. Chain Synchronization", description = "Đồng bộ danh mục, sản phẩm, khuyến mãi từ chuỗi xuống các chi nhánh (UC14)")
@SecurityRequirement(name = "bearerAuth")
public class SyncConfigController {

    private final SyncConfigService syncConfigService;

    @Operation(summary = "Đồng bộ cấu hình chuỗi xuống chi nhánh mục tiêu (UC14)",
            description = "Giám đốc vận hành (COO) phát lệnh đồng bộ Menu danh mục, Sản phẩm, Bảng giá hoặc Voucher xuống các chi nhánh được chỉ định.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đồng bộ thành công"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy chi nhánh mục tiêu",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    @PostMapping
    @PreAuthorize("hasAuthority('COO')")
    public SyncConfigResponse syncToBranches(
            @RequestBody @Valid SyncConfigRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return syncConfigService.syncToBranches(request, principal);
    }
}

