package edu.fpt.sba.scms.security.filter;

import edu.fpt.sba.scms.common.enums.ErrorCode;
import edu.fpt.sba.scms.dto.ApiErrorResponse;
import edu.fpt.sba.scms.dto.ApiExceptionFactory;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Exception của Spring Security ném ra từ controller / method security (vd. {@code @PreAuthorize}).
 * 401/403 ở tầng filter chain do {@code ScmsAuthenticationEntryPoint} / {@code ScmsAccessDeniedHandler} xử lý.
 * <p>
 * {@code @Order} đặt trước {@link GlobalExceptionHandler} để hai advice không tranh nhau xử lý cùng một exception
 * (hành vi trước đây phụ thuộc thứ tự đăng ký bean).
 */
@Slf4j
@Order(Ordered.LOWEST_PRECEDENCE - 10)
@RestControllerAdvice
public class SecurityExceptionHandler {

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(AuthenticationException ex,
                                                                 HttpServletRequest request) {
        log.warn("Authentication failed at {}: {}", request.getRequestURI(), ex.getMessage());
        return ApiExceptionFactory.of(HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_REQUIRED, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex,
                                                               HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            log.warn("Unauthenticated access attempt at {}", request.getRequestURI());
            return ApiExceptionFactory.of(HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_REQUIRED, request);
        }
        log.warn("Access denied at {}", request.getRequestURI());
        return ApiExceptionFactory.of(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED, request);
    }
}
