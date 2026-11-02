package edu.fpt.sba.scms.controller.handler;

import edu.fpt.sba.scms.common.enums.ErrorCode;
import edu.fpt.sba.scms.common.exception.InvalidFieldValueException;
import edu.fpt.sba.scms.dto.ApiErrorResponse;
import edu.fpt.sba.scms.dto.ApiExceptionFactory;
import edu.fpt.sba.scms.dto.FieldErrorItem;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.ArrayList;
import java.util.List;

/**
 * Lỗi đầu vào của request (400) — body không qua được bean validation, thiếu/sai kiểu tham số.
 * Tách khỏi {@link GlobalExceptionHandler} để mỗi file một nhóm trách nhiệm.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class ValidationExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                        HttpServletRequest request) {
        List<FieldErrorItem> errors = new ArrayList<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.add(new FieldErrorItem(fe.getField(), fe.getDefaultMessage()));
        }
        ex.getBindingResult().getGlobalErrors()
                .forEach(ge -> errors.add(new FieldErrorItem(ge.getObjectName(), ge.getDefaultMessage())));
        return badRequest(errors, request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleHandlerMethodValidation(HandlerMethodValidationException ex,
                                                                          HttpServletRequest request) {
        List<FieldErrorItem> errors = new ArrayList<>();
        ex.getAllErrors().forEach(error -> errors.add(new FieldErrorItem(
                error instanceof FieldError fe ? fe.getField() : "request", error.getDefaultMessage())));
        return badRequest(errors, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex,
                                                                      HttpServletRequest request) {
        List<FieldErrorItem> errors = ex.getConstraintViolations().stream()
                .map(v -> new FieldErrorItem(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return badRequest(errors, request);
    }

    /** Thiếu/ sai kiểu query param — trả tên param để FE hiển thị đúng field. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex,
                                                                   HttpServletRequest request) {
        String message = "Thiếu tham số bắt buộc: " + ex.getParameterName() + ".";
        return badRequest(List.of(new FieldErrorItem(ex.getParameterName(), message)), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                               HttpServletRequest request) {
        String message = "Giá trị không hợp lệ cho tham số: " + ex.getName() + ".";
        return badRequest(List.of(new FieldErrorItem(ex.getName(), message)), request);
    }

    /**
     * Rule "một field phụ thuộc field khác" (vd. sortBy phải nằm trong whitelist của danh sách) — service ném lỗi
     * nên cần nêu tên field cho FE. Chỉ dùng cho lỗi đầu vào, không dùng cho lỗi nghiệp vụ (dùng {@code ConflictException}).
     */
    @ExceptionHandler(InvalidFieldValueException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidFieldValue(InvalidFieldValueException ex,
                                                                    HttpServletRequest request) {
        return badRequest(List.of(new FieldErrorItem(ex.getField(), ex.getMessage())), request);
    }

    private static ResponseEntity<ApiErrorResponse> badRequest(List<FieldErrorItem> errors,
                                                               HttpServletRequest request) {
        return ApiExceptionFactory.build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR.name(), null,
                ErrorCode.VALIDATION_ERROR.message(), request, errors);
    }
}
