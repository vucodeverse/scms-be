package edu.fpt.sba.scms.controller.handler;

import edu.fpt.sba.scms.common.enums.ErrorCode;
import edu.fpt.sba.scms.common.exception.BaseException;
import edu.fpt.sba.scms.common.exception.UnauthorizedException;
import edu.fpt.sba.scms.dto.ApiErrorResponse;
import edu.fpt.sba.scms.dto.ApiExceptionFactory;
import edu.fpt.sba.scms.dto.FieldErrorItem;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * Lớp duy nhất map exception nghiệp vụ + lỗi hạ tầng -> {@code ApiErrorResponse}.
 * <p>
 * Thứ tự advice chốt bằng {@code @Order} để không phụ thuộc thứ tự đăng ký bean:
 * validation ({@code HIGHEST_PRECEDENCE}) → security ({@code LOWEST - 10}) → class này ({@code LOWEST}).
 * Lỗi của Spring Security ở {@link SecurityExceptionHandler}; lỗi validation request ở
 * {@link ValidationExceptionHandler}.
 * <p>
 * Chỉ được import {@code common}, Spring MVC — không import service/repository.
 */
@Slf4j
@Order(Ordered.LOWEST_PRECEDENCE)
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ApiErrorResponse> handleBase(BaseException ex, HttpServletRequest request) {
        ErrorCode code = ex.getErrorCode();
        logFailure(code, request, ex);
        return ApiExceptionFactory.build(code.status(), code.name(), ex.detailCode(), ex.getMessage(), request,
                toItems(ex), ex.httpHeaders());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadable(HttpMessageNotReadableException ex,
                                                             HttpServletRequest request) {
        // Không trả message Jackson gốc (lộ tên class/field nội bộ của DTO).
        log.warn("Body không đọc được at {}: {}", request.getRequestURI(), ex.getClass().getSimpleName());
        return ApiExceptionFactory.of(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_INPUT_PAYLOAD, request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResource(NoResourceFoundException ex,
                                                             HttpServletRequest request) {
        return ApiExceptionFactory.of(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, request);
    }

    /**
     * Hai người sửa cùng một bản ghi ({@code @Version}) -> 409 để FE tải lại, thay vì rơi vào 500 chung chung.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorResponse> handleOptimisticLock(OptimisticLockingFailureException ex,
                                                                 HttpServletRequest request) {
        log.warn("Xung đột ghi đồng thời at {}: {}", request.getRequestURI(), ex.getClass().getSimpleName());
        return ApiExceptionFactory.of(HttpStatus.CONFLICT, ErrorCode.INVALID_STATE_TRANSITION, request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex,
                                                                HttpServletRequest request) {
        log.warn("Xung đột ràng buộc dữ liệu at {}: {}", request.getRequestURI(), ex.getClass().getSimpleName());
        return ApiExceptionFactory.build(HttpStatus.CONFLICT, ErrorCode.RESOURCE_CONFLICT.name(), null,
                "Dữ liệu bị xung đột hoặc đang được tham chiếu, không thể thao tác.", request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        // Lỗi có sẵn status (vd. HttpRequestMethodNotSupported -> 405): giữ status, không thành 500.
        // Message Spring của các lỗi này liệt kê method/media-type server hỗ trợ -> trả message generic.
        if (ex instanceof ErrorResponse er && er.getStatusCode().is4xxClientError()) {
            return handleFrameworkClientError(er.getStatusCode().value(), request);
        }
        log.error("Unhandled exception at {}", request.getRequestURI(), ex);
        return ApiExceptionFactory.of(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_SERVER_ERROR, request);
    }

    /** Lỗi 4xx do Spring MVC tự ném (405, 406, 415...): errorCode phải là mã THẬT trong enum, không phải tên status. */
    private static ResponseEntity<ApiErrorResponse> handleFrameworkClientError(int statusCode,
                                                                                HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(statusCode);
        ErrorCode code = HttpStatusErrorCodes.of(status);
        return ApiExceptionFactory.build(status, code.name(), null, code.message(), request, null);
    }

    private static List<FieldErrorItem> toItems(BaseException ex) {
        return ex.fieldErrors().isEmpty()
                ? null
                : ex.fieldErrors().stream().map(fe -> new FieldErrorItem(fe.field(), fe.message())).toList();
    }

    private static void logFailure(ErrorCode code, HttpServletRequest request, Exception ex) {
        if (ex instanceof UnauthorizedException) {
            log.warn("Auth failure {} at {}: {}", code, request.getRequestURI(), ex.getMessage());
            return;
        }
        if (code.status().is4xxClientError()) {
            log.warn("Business rejection {} at {}: {}", code, request.getRequestURI(), ex.getMessage());
            return;
        }
        log.error("System error {} at {}", code, request.getRequestURI(), ex);
    }
}
