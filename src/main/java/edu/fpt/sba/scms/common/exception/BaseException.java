package edu.fpt.sba.scms.common.exception;

import edu.fpt.sba.scms.common.enums.ErrorCode;
import edu.fpt.sba.scms.common.util.FieldError;
import lombok.Getter;
import org.springframework.http.HttpHeaders;

import java.util.List;

/**
 * Gốc của mọi exception nghiệp vụ. Không có exception nào ngoài cây này được ném chủ động.
 * Chỉ {@code controller.handler.GlobalExceptionHandler} được map sang {@code ApiErrorResponse}.
 * <p>
 * Ba hook dưới đây là <b>hợp đồng đầy đủ</b> của một exception nghiệp vụ: handler chỉ cần đọc chúng, không phải
 * {@code instanceof} từng lớp con (thêm lớp con mới không phải sửa handler — LSP/OCP). Lớp con nào có dữ liệu
 * thêm thì override hook tương ứng.
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

    /** Mã con cho FE (vd. {@code VOUCHER_EXPIRED}) — mặc định không có. */
    public String detailCode() {
        return null;
    }

    /** Lỗi theo từng field — mặc định không có. */
    public List<FieldError> fieldErrors() {
        return List.of();
    }

    /** Header cần trả kèm (vd. {@code Retry-After}) — mặc định không có. */
    public HttpHeaders httpHeaders() {
        return HttpHeaders.EMPTY;
    }
}
