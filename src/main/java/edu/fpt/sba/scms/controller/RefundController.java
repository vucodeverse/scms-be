package edu.fpt.sba.scms.controller;

import edu.fpt.sba.scms.dto.ApiErrorResponse;
import edu.fpt.sba.scms.dto.request.CreateRefundRequest;
import edu.fpt.sba.scms.dto.response.RefundResponse;
import edu.fpt.sba.scms.security.principal.UserPrincipal;
import edu.fpt.sba.scms.service.RefundService;
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
 * Controller xử lý hoàn tiền đơn hàng đã thanh toán (UC23, BR-32/33/34/35).
 */
@RestController
@RequestMapping("/api/v1/refunds")
@RequiredArgsConstructor
@Tag(name = "19. Refunds", description = "Xử lý hoàn tiền cho đơn hàng đã thanh toán (UC23)")
@SecurityRequirement(name = "bearerAuth")
public class RefundController {

    private final RefundService refundService;

    @Operation(summary = "Xử lý hoàn tiền cho đơn hàng đã thanh toán (UC23)",
            description = "Thu ngân khởi tạo yêu cầu hoàn tiền. Bắt buộc có lý do, xác thực mã PIN của Quản lý chi nhánh (BR-32), xuất tiền mặt từ ca hiện tại (BR-34) và đổi trạng thái đơn sang REFUNDED.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Hoàn tiền thành công"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy đơn hàng",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Đơn chưa thanh toán hoặc số dư két không đủ",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    @PostMapping
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER', 'CASHIER')")
    public RefundResponse processRefund(
            @RequestBody @Valid CreateRefundRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return refundService.processRefund(request, principal);
    }
}

