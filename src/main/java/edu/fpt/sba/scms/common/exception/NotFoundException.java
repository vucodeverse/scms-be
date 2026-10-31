package edu.fpt.sba.scms.common.exception;

import edu.fpt.sba.scms.common.enums.ErrorCode;

/** 404 — không tìm thấy; message luôn nêu tên tài nguyên. */
public class NotFoundException extends BaseException {

    public NotFoundException(String resource) {
        super(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy " + resource + ".");
    }

    public NotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }
}
