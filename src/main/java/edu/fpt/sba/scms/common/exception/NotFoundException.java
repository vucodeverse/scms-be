package edu.fpt.sba.scms.common.exception;

import edu.fpt.sba.scms.common.enums.ErrorCode;
import org.springframework.http.HttpStatus;

/** 404 — không tìm thấy; message luôn nêu tên tài nguyên. */
public class NotFoundException extends BaseException {

    private final String detailCode;

    public NotFoundException(String resource) {
        super(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy " + resource + ".");
        this.detailCode = null;
    }

    public NotFoundException(ErrorCode errorCode) {
        super(requireNotFound(errorCode));
        this.detailCode = errorCode != ErrorCode.RESOURCE_NOT_FOUND ? errorCode.name() : null;
    }

    public NotFoundException(ErrorCode errorCode, String message) {
        super(requireNotFound(errorCode), message);
        this.detailCode = errorCode != ErrorCode.RESOURCE_NOT_FOUND ? errorCode.name() : null;
    }

    @Override
    public String detailCode() {
        return detailCode;
    }

    private static ErrorCode requireNotFound(ErrorCode errorCode) {
        if (errorCode.status() != HttpStatus.NOT_FOUND) {
            throw new IllegalArgumentException("NotFoundException cần ErrorCode 404, nhận " + errorCode);
        }
        return errorCode;
    }
}
