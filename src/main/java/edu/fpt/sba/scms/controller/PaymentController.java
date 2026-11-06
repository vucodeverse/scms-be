package edu.fpt.sba.scms.controller;

import edu.fpt.sba.scms.dto.ApiErrorResponse;
import edu.fpt.sba.scms.dto.request.CreateOrderRequest;
import edu.fpt.sba.scms.dto.request.GenerateQrRequest;
import edu.fpt.sba.scms.dto.response.OrderResponse;
import edu.fpt.sba.scms.dto.response.PaymentStatusResponse;
import edu.fpt.sba.scms.dto.response.VietQrResponse;
import edu.fpt.sba.scms.security.principal.UserPrincipal;
import edu.fpt.sba.scms.service.PaymentService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller phục vụ tạo đơn hàng POS và thanh toán VietQR động (UC43, UC20, POP-02).
 */
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "16. Payments & VietQR", description = "Tạo đơn hàng & tạo mã VietQR thanh toán (UC43, UC20)")
@SecurityRequirement(name = "bearerAuth")
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "Tạo đơn hàng tại quầy POS",
            description = "Tạo đơn hàng mới để chuyển sang bước thanh toán tiền mặt hoặc VietQR.")
    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER', 'CASHIER', 'STAFF')")
    public OrderResponse createOrder(
            @RequestBody @Valid CreateOrderRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return paymentService.createOrder(request, principal);
    }

    @Operation(summary = "Tạo mã VietQR động theo đơn hàng (UC43)",
            description = "Tự động lấy thông tin tài khoản thụ hưởng, số tiền và sinh tiền tố CAFE{orderId} để tạo mã VietQR chuẩn.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tạo mã VietQR thành công"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy đơn hàng",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Đơn hàng đã thanh toán trước đó",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    @PostMapping("/vietqr")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER', 'CASHIER', 'STAFF')")
    public VietQrResponse generateVietQr(
            @RequestBody @Valid GenerateQrRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return paymentService.generateVietQr(request.orderId(), principal);
    }

    @Operation(summary = "Kiểm tra trạng thái thanh toán của đơn hàng (UC20)",
            description = "Dành cho màn hình Thu ngân POS kiểm tra trạng thái đơn hàng đã được SePay thanh toán thành công hay chưa.")
    @GetMapping("/orders/{orderId}/status")
    @PreAuthorize("hasAnyAuthority('COO', 'BRANCH_MANAGER', 'CASHIER', 'STAFF')")
    public PaymentStatusResponse getPaymentStatus(@PathVariable Integer orderId) {
        return paymentService.getPaymentStatus(orderId);
    }
}

