package edu.fpt.sba.scms.common.exception;

import edu.fpt.sba.scms.common.enums.ErrorCode;

/** 500 — sinh file báo cáo thất bại (UC36 AT3). */
public class ReportGenerationException extends BaseException {

    public static final String MESSAGE = "Tạo file báo cáo thất bại. Vui lòng thử lại.";

    public ReportGenerationException(Throwable cause) {
        super(ErrorCode.INTERNAL_SERVER_ERROR, MESSAGE, cause);
    }
}
