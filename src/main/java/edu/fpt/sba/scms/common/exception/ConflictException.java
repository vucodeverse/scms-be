package edu.fpt.sba.scms.common.exception;

import edu.fpt.sba.scms.common.enums.ErrorCode;
import org.springframework.http.HttpStatus;

/** 409 — trùng dữ liệu / xung đột trạng thái. Mã vào phải là 409 (không để "lỗi kiểu" trả nhầm status). */
public class ConflictException extends BaseException {

    public ConflictException(ErrorCode errorCode) {
        super(requireConflict(errorCode));
    }

    public ConflictException(ErrorCode errorCode, String message) {
        super(requireConflict(errorCode), message);
    }

    public ConflictException(ErrorCode errorCode, String message, Throwable cause) {
        super(requireConflict(errorCode), message, cause);
    }

    private static ErrorCode requireConflict(ErrorCode errorCode) {
        if (errorCode.status() != HttpStatus.CONFLICT) {
            throw new IllegalArgumentException("ConflictException cần ErrorCode 409, nhận " + errorCode);
        }
        return errorCode;
    }
}
