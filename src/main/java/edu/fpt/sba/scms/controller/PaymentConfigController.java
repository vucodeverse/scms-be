package edu.fpt.sba.scms.controller;

import edu.fpt.sba.scms.dto.ApiErrorResponse;
import edu.fpt.sba.scms.dto.request.PingTestRequest;
import edu.fpt.sba.scms.dto.request.UpdatePaymentConfigRequest;
import edu.fpt.sba.scms.dto.response.PaymentConfigResponse;
import edu.fpt.sba.scms.dto.response.PingTestResponse;
import edu.fpt.sba.scms.security.principal.UserPrincipal;
import edu.fpt.sba.scms.service.PaymentConfigService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller cấu hình cổng thanh toán SePay & tài khoản thụ hưởng chuỗi (UC15, SCR-16 TAB-10).
 */
@RestController
@RequestMapping("/api/v1/settings/sepay")
@RequiredArgsConstructor
@Tag(name = "15. Payment Settings", description = "Cấu hình tài khoản ngân hàng & cổng SePay (UC15, SCR-16 TAB-10)")
@SecurityRequirement(name = "bearerAuth")
public class PaymentConfigController {

    private final PaymentConfigService paymentConfigService;

    @Operation(summary = "Xem cấu hình thanh toán SePay (UC15)",
            description = "Lấy thông tin tài khoản ngân hàng nhận tiền và cổng SePay của chuỗi. API Token được che mờ bảo mật.")
    @GetMapping
    @PreAuthorize("hasAuthority('COO')")
    public PaymentConfigResponse getConfig() {
        return paymentConfigService.getConfig();
    }

    @Operation(summary = "Cập nhật cấu hình SePay (UC15)",
            description = "COO lưu thông tin tài khoản ngân hàng, tiền tố chuyển khoản và Secret Token SePay.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cập nhật thành công"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    @PutMapping
    @PreAuthorize("hasAuthority('COO')")
    public PaymentConfigResponse updateConfig(
            @RequestBody @Valid UpdatePaymentConfigRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return paymentConfigService.updateConfig(request, principal);
    }

    @Operation(summary = "Ping Test kết nối SePay (UC15)",
            description = "Gửi yêu cầu thử nghiệm kiểm tra tính hợp lệ của Webhook URL và API Token.")
    @PostMapping("/ping")
    @PreAuthorize("hasAuthority('COO')")
    public PingTestResponse pingTest(@RequestBody(required = false) PingTestRequest request) {
        return paymentConfigService.pingTest(request);
    }
}

