package edu.fpt.sba.scms.common.exception;

import edu.fpt.sba.scms.common.enums.ErrorCode;
import lombok.Getter;

/**
 * Gốc của mọi exception nghiệp vụ. Không có exception nào ngoài cây này được ném chủ động.
 * Chỉ {@code controller.handler.GlobalExceptionHandler} được map sang {@code ApiErrorResponse}.
 */
@Getter
public abstract class BaseException extends RuntimeException {

    private final ErrorCode errorCode;

    protected BaseException(ErrorCode errorCode) {
        this(errorCode, errorCode.message(), null);
    }

    protected BaseException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    protected BaseException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
