package edu.fpt.sba.scms.controller;

import edu.fpt.sba.scms.dto.ApiErrorResponse;
import edu.fpt.sba.scms.dto.request.LoginRequest;
import edu.fpt.sba.scms.dto.request.PinLoginRequest;
import edu.fpt.sba.scms.dto.request.RefreshRequest;
import edu.fpt.sba.scms.dto.request.RefreshTokenRequest;
import edu.fpt.sba.scms.dto.response.CashierPinOptionDto;
import edu.fpt.sba.scms.dto.response.CurrentUserResponse;
import edu.fpt.sba.scms.dto.response.KioskBranchDto;
import edu.fpt.sba.scms.dto.response.LoginResponse;
import edu.fpt.sba.scms.dto.response.MessageResponse;
import edu.fpt.sba.scms.dto.response.TokenResponse;
import edu.fpt.sba.scms.security.principal.UserPrincipal;
import edu.fpt.sba.scms.security.util.ClientIpResolver;
import edu.fpt.sba.scms.security.web.RefreshCookieSupport;
import edu.fpt.sba.scms.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


/**
 * UC01 — Auth (M01). Path đã chốt trong IMPLEMENTATION.md §10.1.
 * Controller trả payload trần; envelope do {@code ApiResponseAdvice} bọc.
 * <ul>
 *   <li>Mặc định: refresh token trả trong body (FE lưu nơi an toàn).</li>
 *   <li>Bật {@code app.auth.cookie.refresh-enabled=true}: refresh token nằm trong cookie httpOnly,
 *       không xuất hiện trong body lẫn localStorage (khuyến nghị khi triển khai thật).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "01. Auth", description = "Đăng nhập, làm mới token, đăng xuất, thông tin tài khoản (UC01)")
public class AuthController {

    private final AuthService authService;
    private final RefreshCookieSupport refreshCookieSupport;
    private final ClientIpResolver clientIp;

    @Operation(summary = "Đăng nhập",
            description = "Sai 5 lần trong 15 phút -> khóa 15 phút (ACCOUNT_LOCKED). roleHint chỉ để FE điều hướng.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đăng nhập thành công"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "INVALID_CREDENTIALS | ACCOUNT_LOCKED",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    @PostMapping("/login")
    public LoginResponse login(@RequestBody @Valid LoginRequest request,
                               HttpServletRequest httpRequest, HttpServletResponse response) {
        LoginResponse result = authService.login(request, clientIp.of(httpRequest));
        if (refreshCookieSupport.enabled()) {
            refreshCookieSupport.write(response, result.refreshToken());
            return new LoginResponse(result.accessToken(), result.expiresInSec(), null, result.user(),
                    result.landing());
        }
        return result;
    }

    @Operation(summary = "Danh sách chi nhánh cho màn hình Kiosk / PIN",
            description = "Public endpoint liệt kê chi nhánh đang hoạt động (trừ INACTIVE) để chọn tại quầy.")
    @GetMapping("/branches")
    public List<KioskBranchDto> listPublicBranches(@RequestParam(required = false) String keyword) {
        return authService.listPublicBranches(keyword);
    }

    @Operation(summary = "Danh sách Thu ngân mở khóa PIN theo chi nhánh",
            description = "Public endpoint cho màn POS / Kiểm két liệt kê thu ngân đang hoạt động của chi nhánh.")
    @GetMapping("/cashiers")
    public List<CashierPinOptionDto> listCashierPinOptions(@RequestParam Integer branchId) {
        return authService.listCashierPinOptions(branchId);
    }

    @Operation(summary = "Đăng nhập Thu ngân bằng mã PIN",
            description = "Xác thực thu ngân bằng PIN 4-6 số tại chi nhánh; sai 3 lần tạm khóa 15 phút (PIN_LOCKED).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Mở khóa Thu ngân thành công"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "INVALID_PIN | PIN_LOCKED | ACCOUNT_INACTIVE",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "ACCESS_DENIED | CROSS_BRANCH_DENIED",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    @PostMapping("/pin-login")
    public LoginResponse pinLogin(@RequestBody @Valid PinLoginRequest request,
                                  HttpServletRequest httpRequest) {
        return authService.pinLogin(request, clientIp.of(httpRequest));
    }

    @Operation(summary = "Làm mới access token",
            description = "Nhận refresh token trong body hoặc cookie httpOnly; token cũ bị thu hồi (rotation).")
    @ApiResponses({
            @ApiResponse(responseCode = "401", description = "AUTH_TOKEN_EXPIRED",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))})
    @PostMapping("/refresh")
    public TokenResponse refresh(@RequestBody(required = false) RefreshTokenRequest request,
                                 HttpServletRequest httpRequest, HttpServletResponse response) {
        TokenResponse result = authService.refresh(new RefreshRequest(
                request == null ? null : request.refreshToken(),
                // Tắt cờ cookie thì KHÔNG đọc cookie: client còn cookie cũ không được refresh qua cookie nữa.
                refreshCookieSupport.enabled() ? refreshCookieSupport.read(httpRequest) : null,
                clientIp.of(httpRequest)));
        if (refreshCookieSupport.enabled()) {
            refreshCookieSupport.write(response, result.refreshToken());
            return new TokenResponse(result.accessToken(), result.expiresInSec(), null, result.user());
        }
        return result;
    }

    @Operation(summary = "Đăng xuất",
            description = "Thu hồi toàn bộ refresh token của user và chặn access token hiện tại.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "Đã đăng xuất")
    @PostMapping("/logout")
    public MessageResponse logout(@AuthenticationPrincipal UserPrincipal principal,
                                  HttpServletResponse response) {
        authService.logout(principal);
        if (refreshCookieSupport.enabled()) {
            refreshCookieSupport.write(response, null);
        }
        // Trả object (không phải void/null) để ApiResponseAdvice bọc được: Spring MVC bỏ qua ghi body khi null.
        return new MessageResponse("Đã đăng xuất.");
    }

    @Operation(summary = "Thông tin tài khoản đang đăng nhập",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/me")
    public CurrentUserResponse me(@AuthenticationPrincipal UserPrincipal principal) {
        return authService.me(principal);
    }

}
