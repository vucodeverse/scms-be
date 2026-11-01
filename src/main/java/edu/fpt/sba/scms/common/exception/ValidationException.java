package edu.fpt.sba.scms.common.exception;

import edu.fpt.sba.scms.common.enums.ErrorCode;
import edu.fpt.sba.scms.common.util.FieldError;

import java.util.List;

/** 400 — dữ liệu đầu vào sai; mang danh sách lỗi theo field. */
public class ValidationException extends BaseException {

    private final List<FieldError> fieldErrors;

    public ValidationException(String message, List<FieldError> fieldErrors) {
        super(ErrorCode.VALIDATION_ERROR, message);
        this.fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }

    @Override
    public List<FieldError> fieldErrors() {
        return fieldErrors;
    }

    public static ValidationException single(String field, String message) {
        return new ValidationException(message, List.of(new FieldError(field, message)));
    }
}
