package edu.fpt.sba.scms.controller.handler;

import edu.fpt.sba.scms.dto.ApiErrorResponse;
import edu.fpt.sba.scms.dto.FieldErrorItem;
import edu.fpt.sba.scms.common.enums.ErrorCode;
import edu.fpt.sba.scms.common.exception.AccountLockedException;
import edu.fpt.sba.scms.common.exception.BaseException;
import edu.fpt.sba.scms.common.exception.BusinessRuleException;
import edu.fpt.sba.scms.common.exception.UnauthorizedException;
import edu.fpt.sba.scms.common.exception.ValidationException;
import edu.fpt.sba.scms.common.trace.TraceContextFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;

import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.validation.FieldError;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.List;

/**
 * Lớp DUY NHẤT map exception -> ApiErrorResponse (IMPLEMENTATION.md §8.2/§8.3).
 * Chỉ được import {@code common}, Spring MVC/Security — không import controller/service/repository.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ApiErrorResponse> handleBase(BaseException ex, HttpServletRequest request) {
        ErrorCode code = ex.getErrorCode();
        String detailCode = ex instanceof BusinessRuleException bre ? bre.getDetailCode() : null;
        List<FieldErrorItem> fieldErrors =
                ex instanceof ValidationException ve
                        ? ve.getFieldErrors().stream()
                                .map(fe -> new FieldErrorItem(fe.field(), fe.message()))
                                .toList()
                        : null;

        if (ex instanceof UnauthorizedException) {
            log.warn("Auth failure {} at {}: {}", code, request.getRequestURI(), ex.getMessage());
        } else if (code.status().is4xxClientError()) {
            log.warn("Business rejection {} at {}: {}", code, request.getRequestURI(), ex.getMessage());
        } else {
            log.error("System error {} at {}", code, request.getRequestURI(), ex);
        }

        HttpHeaders headers = new HttpHeaders();
        if (ex instanceof AccountLockedException locked) {
            headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(locked.getRetryAfterSeconds()));
        }
        return build(code.status(), code.name(), detailCode, ex.getMessage(), request, fieldErrors, headers);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleBadCredentials(BadCredentialsException ex,
                                                                 HttpServletRequest request) {
        log.warn("Login failed at {}", request.getRequestURI());
        return build(HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_CREDENTIALS.name(), null,
                ErrorCode.INVALID_CREDENTIALS.message(), request, null, null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(AuthenticationException ex,
                                                                 HttpServletRequest request) {
        log.warn("Authentication failed at {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_REQUIRED.name(), null,
                ErrorCode.AUTH_REQUIRED.message(), request, null, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex,
                                                               HttpServletRequest request) {
        log.warn("Access denied at {}", request.getRequestURI());
        return build(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED.name(), null,
                ErrorCode.ACCESS_DENIED.message(), request, null, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                        HttpServletRequest request) {
        List<FieldErrorItem> errors = new ArrayList<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.add(new FieldErrorItem(fe.getField(), fe.getDefaultMessage()));
        }
        ex.getBindingResult().getGlobalErrors()
                .forEach(ge -> errors.add(new FieldErrorItem(ge.getObjectName(), ge.getDefaultMessage())));
        return validation(errors, request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleHandlerMethodValidation(HandlerMethodValidationException ex,
                                                                          HttpServletRequest request) {
        List<FieldErrorItem> errors = new ArrayList<>();
        ex.getAllErrors().forEach(error -> errors.add(new FieldErrorItem(
                error instanceof FieldError fe ? fe.getField() : "request", error.getDefaultMessage())));
        return validation(errors, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex,
                                                                      HttpServletRequest request) {
        List<FieldErrorItem> errors = ex.getConstraintViolations().stream()
                .map(v -> new FieldErrorItem(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return validation(errors, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadable(HttpMessageNotReadableException ex,
                                                             HttpServletRequest request) {
        // Không trả message Jackson gốc (lộ tên class/field nội bộ của DTO).
        log.warn("Body không đọc được at {}: {}", request.getRequestURI(), ex.getClass().getSimpleName());
        return build(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_INPUT_PAYLOAD.name(), null,
                ErrorCode.INVALID_INPUT_PAYLOAD.message(), request, null, null);
    }

    /** Thiếu/ sai kiểu query param — trả tên param để FE hiển thị đúng field. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex,
                                                                   HttpServletRequest request) {
        String message = "Thiếu tham số bắt buộc: " + ex.getParameterName() + ".";
        return validation(List.of(new FieldErrorItem(ex.getParameterName(), message)), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                               HttpServletRequest request) {
        String message = "Giá trị không hợp lệ cho tham số: " + ex.getName() + ".";
        return validation(List.of(new FieldErrorItem(ex.getName(), message)), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResource(NoResourceFoundException ex,
                                                             HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND.name(), null,
                ErrorCode.RESOURCE_NOT_FOUND.message(), request, null, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        // Lỗi có sẵn status (vd. HttpRequestMethodNotSupported -> 405): giữ status, không thành 500.
        // Message Spring của các lỗi này liệt kê method/media-type server hỗ trợ -> trả message generic.
        if (ex instanceof ErrorResponse er && er.getStatusCode().is4xxClientError()) {
            HttpStatus status = HttpStatus.valueOf(er.getStatusCode().value());
            return build(status, status.name(), null, "Yêu cầu không hợp lệ (" + status.value() + ").",
                    request, null, null);
        }
        log.error("Unhandled exception at {}", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_SERVER_ERROR.name(), null,
                ErrorCode.INTERNAL_SERVER_ERROR.message(), request, null, null);
    }

    private ResponseEntity<ApiErrorResponse> validation(List<FieldErrorItem> errors, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR.name(), null,
                ErrorCode.VALIDATION_ERROR.message(), request, errors, null);
    }

    private ResponseEntity<ApiErrorResponse> build(HttpStatus status, String errorCode, String detailCode,
                                                   String message, HttpServletRequest request,
                                                   List<FieldErrorItem> fieldErrors, HttpHeaders headers) {
        ApiErrorResponse body = ApiErrorResponse.of(status.value(), errorCode, detailCode, message,
                request.getRequestURI(), TraceContextFilter.currentTraceId(), fieldErrors);
        return new ResponseEntity<>(body, headers == null ? new HttpHeaders() : headers, status);
    }
}
